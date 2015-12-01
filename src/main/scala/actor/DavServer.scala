package actor

import java.net.URLDecoder
import akka.actor._
import akka.io.Tcp.Closed
import akka.pattern.pipe
import client.Resource
import spray.can.Http
import spray.http.HttpHeaders.{Allow, RawHeader}
import spray.http.HttpMethods._
import spray.http.MediaTypes._
import spray.http._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.xml._

trait FileHandler {
  def resourceForTarget(target: String): Future[Option[Resource]]
}

object DavServerActor {
  def props(url: String, handler: FileHandler): Props = Props(new DavServerActor(url, handler))
}

class DavServerActor(url: String, handler: FileHandler) extends Actor with ActorLogging {

  val PROPFIND = HttpMethod.custom("PROPFIND", safe = true, idempotent = true, entityAccepted = false)
  HttpMethods.register(PROPFIND)

  private def optionsResponse = HttpResponse(
    status = 200,
    headers = List(
      Allow(GET, OPTIONS, PROPFIND),
      RawHeader("DAV", "1")
    )
  )

  def propfind(url: String, props: NodeSeq, res: Resource, depth: String) = {

    val resources:Seq[Resource] = depth match {
      case "0" => res::Nil
      case "1" => res.children ++ (res :: Nil)
    }

    <D:multistatus xmlns:D="DAV:">
      {resources.map(res =>{
      val mapped:Seq[(Node,Option[Node])] = props.map(p => (p,res.property(p)))
      <D:response>
        <D:href>{url + "/" + res.url}</D:href>
        <D:propstat xmlns:D="DAV:">
          <D:prop>
            {mapped.flatMap(_ match {case (_,Some(p))=>p::Nil;case (_,None)=>Nil})}
          </D:prop>
          <D:status>HTTP/1.1 200 OK</D:status>
        </D:propstat>
        <D:propstat xmlns:D="DAV:">
          <D:prop>
            {mapped.flatMap(_ match {case (_,Some(p))=>Nil;case (p,None)=>p})}
          </D:prop>
          <D:status>HTTP/1.1 404 Not Found</D:status>
        </D:propstat>
      </D:response>})}
    </D:multistatus>
  }

  def parsePath(path: Uri.Path): String = {
    URLDecoder.decode(path.toString())
  }

  def receive = {
    case _: Http.Connected => sender ! Http.Register(self)

    case _: Http.CloseCommand =>
      println("CLOSED")
    case _: Http.ErrorClosed =>
      println("ERR CLOSED")

    case HttpRequest(OPTIONS, _, _, _, _) =>
      sender ! optionsResponse

    case HttpRequest(HEAD, path, _, _, _) =>
      handler.resourceForTarget(parsePath(path.path)) map {
        case Some(resource) =>
          HttpResponse(status = 200)
        case None =>
          HttpResponse(status = 404, entity = "Not Found")
      } pipeTo sender

    case HttpRequest(PROPFIND, path, headers, entity, _) =>
      handler.resourceForTarget(parsePath(path.path)).map {
        case Some(resource) =>
          val depth = headers.find(_.is("depth")).map(_.value).getOrElse("0")
          val input = XML.loadString(entity.asString)
          val prop = propfind(url, input \ "prop" \ "_", resource, depth)
          HttpResponse(
            status = 207,
            entity = HttpEntity.apply(`application/xml`, prop.toString)
          )
        case None =>
          HttpResponse(status = 404, entity = "Not Found")

      }.recover {
        case e =>
          log.error(e, entity.asString)
          HttpResponse(status = 404, entity = "Not Found " + e.getMessage())
      } pipeTo sender

    case HttpRequest(GET, path, headers, entity, _) =>

      val h = handler.resourceForTarget(parsePath(path.path))
        .flatMap(_.map(_.stream)
          .getOrElse(Future.failed(new IllegalArgumentException(parsePath(path.path)))))

      h.map { stream =>
        HttpResponse(status = 200, HttpEntity.apply(HttpData.fromFile(fileName = stream.getAbsolutePath)))
      }.recover {
        case e =>
          log.error(e, entity.asString)
          HttpResponse(status = 404, entity = "Not Found " + e.getMessage())
      } pipeTo sender

    case _: HttpRequest =>
      sender ! HttpResponse(status = 404, entity = "Unknown resource!")


    case Timedout(HttpRequest(method, uri, _, _, _)) =>
      sender ! HttpResponse(
        status = 500,
        entity = "The " + method + " request to '" + uri + "' has timed out..."
      )
  }
}
