package actor

import java.io.FileOutputStream
import java.net.URLDecoder
import java.util.UUID
import actor.FileLockingActor.Lock
import actor.FileSystemActor.FindNode
import akka.actor._
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import akka.pattern.pipe
import client.Resource
import spray.can.Http
import spray.http.HttpHeaders.{Allow, RawHeader}
import spray.http.HttpMethods._
import spray.http.MediaTypes._
import spray.http._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.xml._

trait FileHandler {
  def resourceForTarget(target: String): Future[Option[Resource]]

  def uploadResource(target: String, stream: Stream[HttpData]): Future[Boolean]
}

object DavServerActor {
  def props(url: String, handler: FileHandler, lockingActor: ActorRef, fileSystemActor: ActorRef): Props = Props(new DavServerActor(url, handler, lockingActor, fileSystemActor))
}

class DavServerActor(url: String, handler: FileHandler, lockingActor: ActorRef, fileSystemActor: ActorRef) extends Actor with ActorLogging {

  implicit val timeout: Timeout = 100 seconds

  val PROPFIND = HttpMethods.register(
    HttpMethod.custom("PROPFIND", safe = true, idempotent = true, entityAccepted = false))

  val LOCK = HttpMethods.register(
    HttpMethod.custom("LOCK", safe = true, idempotent = true, entityAccepted = false)
  )

  val UNLOCK = HttpMethods.register(
    HttpMethod.custom("UNLOCK", safe = true, idempotent = true, entityAccepted = false)
  )

  val COPY = HttpMethods.register(
    HttpMethod.custom("COPY", safe = true, idempotent = true, entityAccepted = false)
  )

  val MKCOL = HttpMethods.register(
    HttpMethod.custom("MKCOL", safe = true, idempotent = true, entityAccepted = false)
  )

  val MOVE = HttpMethods.register(
    HttpMethod.custom("MOVE", safe = true, idempotent = true, entityAccepted = false)
  )

  val PROPPATCH = HttpMethods.register(
    HttpMethod.custom("PROPPATCH", safe = true, idempotent = true, entityAccepted = false)
  )

  private def optionsResponse = HttpResponse(
    status = 207,
    headers = List(
      Allow(
        GET,
        OPTIONS,
        PROPFIND,
        PUT,
        LOCK,
        UNLOCK,
        COPY,
        DELETE,
        MKCOL,
        //MOVE,
        PROPPATCH
      ),
      RawHeader("DAV", "1,2")
    )
  )

  def propfind(url: String, props: NodeSeq, res: Resource, depth: String) = {
    (depth match {
      case "0" => Future.apply(res::Nil)
      case "1" => res.children.map(c => c ++ (res :: Nil))
    }).map { resources =>
      <D:multistatus xmlns:D="DAV:">
        {resources.map(res => {
        val mapped: Seq[(Node, Option[Node])] = props.map(p => (p, res.property(p)))
        <D:response>
          <D:href>{url + res.url}</D:href>
          <D:propstat xmlns:D="DAV:">
            <D:prop>
              {mapped.flatMap(_ match { case (_, Some(p)) => p :: Nil; case (_, None) => Nil })}
              <D:supportedlock>
                <D:lockentry>
                  <D:lockscope><D:exclusive/></D:lockscope>
                  <D:locktype><D:write/></D:locktype>
                </D:lockentry>
                <D:lockentry>
                  <D:lockscope><D:shared/></D:lockscope>
                  <D:locktype><D:write/></D:locktype>
                </D:lockentry>
              </D:supportedlock>
            </D:prop>
            <D:status>HTTP/1.1 200 OK</D:status>
          </D:propstat>
          <D:propstat xmlns:D="DAV:">
            <D:prop>
              {mapped.flatMap(_ match { case (_, Some(p)) => Nil; case (p, None) => p })}
              <D:supportedlock>
                <D:lockentry>
                  <D:lockscope><D:exclusive/></D:lockscope>
                  <D:locktype><D:write/></D:locktype>
                </D:lockentry>
                <D:lockentry>
                  <D:lockscope><D:shared/></D:lockscope>
                  <D:locktype><D:write/></D:locktype>
                </D:lockentry>
              </D:supportedlock>
            </D:prop>
            <D:status>HTTP/1.1 404 Not Found</D:status>
          </D:propstat>
        </D:response>
      })}
      </D:multistatus>
    }
  }

  def parsePath(path: Uri.Path): String = {
    URLDecoder.decode(path.toString())
  }

  def receive = {
    case _: Http.Connected => sender ! Http.Register(self)

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
      handler.resourceForTarget(parsePath(path.path)).flatMap {
        case Some(resource) =>
          val depth = headers.find(_.is("depth")).map(_.value).getOrElse("0")
          val input = XML.loadString(entity.asString)
          propfind(url, input \ "prop" \ "_", resource, depth).map { prop =>
            HttpResponse(
              status = 207,
              entity = HttpEntity.apply(`application/xml`, prop.toString)
            )
          }
        case None =>
          Future.apply(HttpResponse(status = 404, entity = "Not Found"))

      }.recover {
        case e =>
          log.error(e, entity.asString)
          HttpResponse(status = 404, entity = "Not Found " + e.getMessage())
      } pipeTo sender

    case HttpRequest(PROPPATCH, path, headers, entity, _) =>
      print("PROPPATCH"+ path + " " + entity)

      sender ! HttpResponse(status = 404, entity = "Unknown resource!")


    case HttpRequest(GET, path, headers, entity, _) =>
      println("serve: " + path)

      val h = handler.resourceForTarget(parsePath(path.path))
        .flatMap(_.map(_.stream)
          .getOrElse(Future.failed(new IllegalArgumentException("\"" + parsePath(path.path) + "\""))))

      h.map { stream =>
        HttpResponse(status = 200, HttpEntity.apply(HttpData.fromFile(fileName = stream.getAbsolutePath)))
      }.recover {
        case e =>
          log.error(e, entity.asString)
          HttpResponse(status = 404, entity = "Not Found " + e.getMessage())
      } pipeTo sender


    case HttpRequest(LOCK, path, _, entity, _) =>
      lockingActor ? Lock(path.path.toString()) map {
        case Success(true) =>

          val xml = <?xml version="1.0" encoding="utf-8" ?>
            <D:prop xmlns:D="DAV:">
              <D:lockdiscovery>
                <D:activelock>
                  <D:locktype><D:write/></D:locktype>
                  <D:lockscope><D:exclusive/></D:lockscope>
                  <D:depth>Infinity</D:depth>
                  <D:owner>
                    <D:href>
                      {url + path.path.toString()}
                    </D:href>
                  </D:owner>
                  <D:timeout>Second-604800</D:timeout>
                  <D:locktoken>
                    <D:href>
                      {UUID.randomUUID().toString}
                    </D:href>
                  </D:locktoken>
                </D:activelock>
              </D:lockdiscovery>
            </D:prop>

          HttpResponse(
            status = 200,
            entity = HttpEntity.apply(`application/xml`, xml.toString)
          )
        case Failure(e) =>
          log.error(e, entity.asString)
          HttpResponse(status = 404, entity = "Not Found " + e.getMessage())
      } pipeTo sender

    case HttpRequest(MOVE, path, headers, entity, _) =>
      print("MOVE "+ path + " " + entity)

      sender ! HttpResponse(status = 404, entity = "Unknown resource!")

    case HttpRequest(PUT, path, headers, HttpEntity.NonEmpty(contentType, data), _) =>

      println("PUT "+ path + " " + headers)

      handler.uploadResource(parsePath(path.path), data.toChunkStream(512)) map {
        case true =>
          HttpResponse(status = 200)
        case false =>
          HttpResponse(status = 409)
      } pipeTo sender


    case r @ HttpRequest =>
      println("UNHANDLED")
      println(r)
      sender ! HttpResponse(status = 404, entity = "Unknown resource!")

    case Timedout(HttpRequest(method, uri, _, _, _)) =>
      sender ! HttpResponse(
        status = 500,
        entity = "The " + method + " request to '" + uri + "' has timed out..."
      )
  }
}
