package DavServer

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}

import _root_.server.{AsyncHandlerTrait, AsyncHandler}
import org.eclipse.jetty.io.EofException
import org.eclipse.jetty.server._
import org.eclipse.jetty.server.handler.{ContextHandler, ErrorHandler, AbstractHandler}
import org.eclipse.jetty.util.IO
import scala.concurrent.{Await, Future}
import scala.xml._
import scala.concurrent.ExecutionContext.Implicits.global

trait FileHandler {
  def resourceForTarget(target: String): Future[Option[Resource]]
}

class DavServer(port: Int = 7070, handler: FileHandler) extends AsyncHandlerTrait {

  val url = "http://localhost:" + port

  def startServer(): Unit = {
    val s = new Server(port)
    val context = new ContextHandler()
    context.setHandler(new AsyncHandler(this))
    context.setContextPath("/*")
    s.setHandler(context)

    s.start()
    s.join()
  }

  def propfind(props: NodeSeq, res: Resource, depth: String) = {

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

  override def handle(target: String, req: HttpServletRequest, res: HttpServletResponse, complete: (() => Unit)): Unit = {
    handler.resourceForTarget(target.substring(1)) onSuccess {
      case resour =>
        resour match {
          case Some(resource) =>
            req.getMethod match {
              case "OPTIONS" =>
                res.setHeader("DAV", "1")
                res.setHeader("Allow", "GET,OPTIONS,PROPFIND")
                res.setStatus(200)

                complete()

              case "HEAD" =>
                res.setContentLength(resource.length)
                res.setStatus(200)

                complete()

              case "PROPFIND" => res.setContentType("application/xml")
                res.setStatus(207)
                val depth = req.getHeader("Depth")
                val input = XML.load(req.getInputStream)
                val prop = propfind(input \ "prop" \ "_", resource, depth)
                XML.write(res.getWriter, prop, "utf-8", true, null)

                complete()

              case "GET" =>
                val input = scala.io.Source.fromInputStream(req.getInputStream).mkString
                println(input)
                res.setContentLength(resource.length)
                val future = resource.stream
                future onSuccess {
                  case stream =>
                    val outputStream = res.getOutputStream.asInstanceOf[HttpOutput]
                    if (!outputStream.isClosed) {
                      try {
                        IO.copy(stream, outputStream)
                      } catch {
                        case e: EofException =>
                          println("Eof")
                      }
                    } else {
                      println("CLOSED")
                    }
                    complete()
                }
            }
          case _ =>
            res.sendError(404, "Not found!")
            complete()
        }
    }
  }
}