package DavServer

import spray.http.HttpData.Bytes
import spray.http.HttpEntity.NonEmpty
import spray.http.HttpHeaders.{RawHeader, Allow}
import spray.httpx.unmarshalling.Unmarshaller

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout
import akka.actor._
import spray.can.Http
import spray.can.server.Stats
import spray.util._
import spray.http._
import HttpMethods._
import MediaTypes._
import akka.pattern.pipe

import scala.util._
import scala.concurrent.ExecutionContext.Implicits.global
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

  val `application/xml` = ContentType(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`)

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


  def receive = {
    case _: Http.Connected => sender ! Http.Register(self)

    case HttpRequest(OPTIONS, _, _, _, _) =>
      sender ! optionsResponse

    case HttpRequest(HEAD, path, _, _, _) =>
      handler.resourceForTarget(path.toString()) map {
        case Some(resource) =>
          HttpResponse(status = 200)
        case None =>
          HttpResponse(status = 404, entity = "Not Found")
      } pipeTo sender

    case HttpRequest(PROPFIND, path, headers, entity, _) =>
      handler.resourceForTarget(path.path.toString()).map {
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
      handler.resourceForTarget(path.toString).map {
        case Some(resource) =>
          resource.stream.map { stream =>

            val byteArray = Stream.continually(stream.read).takeWhile(_ != -1).map(_.toByte).toArray

            HttpResponse(status = 200, HttpEntity.apply(byteArray))
          } pipeTo sender
        case None =>
          HttpResponse(status = 404, entity = "Not Found")
      }


    case _: HttpRequest =>
      sender ! HttpResponse(status = 404, entity = "Unknown resource!")

    case Timedout(HttpRequest(method, uri, _, _, _)) =>
      sender ! HttpResponse(
        status = 500,
        entity = "The " + method + " request to '" + uri + "' has timed out..."
      )
  }
}
