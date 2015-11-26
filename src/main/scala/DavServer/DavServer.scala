package DavServer

import java.io.Writer
import java.nio.ByteBuffer
import javax.servlet.{AsyncEvent, AsyncListener}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}

import org.eclipse.jetty.continuation.{Continuation, ContinuationListener, ContinuationSupport}
import org.eclipse.jetty.http.HttpFields
import org.eclipse.jetty.server
import org.eclipse.jetty.server.{Request, Server}
import org.eclipse.jetty.server.handler.{ContextHandler, ErrorHandler, AbstractHandler}
import org.eclipse.jetty.util.IO
import org.eclipse.jetty.util.component.Container


import scala.concurrent.{Await, Future}
import scala.xml._
import scala.concurrent.ExecutionContext.Implicits.global

trait FileHandler {
  def resourceForTarget(target: String): Future[Option[Resource]]
}

class DavServer(port: Int = 7070, handler: FileHandler) extends ErrorHandler {

  val url = "http://localhost:" + port

  def startServer(): Unit = {
    val s = new Server(port)
    val context = new ContextHandler()
    context.setHandler(this)
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



  override def handle(target: String, baseRequest: server.Request, req: HttpServletRequest, res: HttpServletResponse): Unit = {
    val r:Request = req.asInstanceOf[Request]

    r.setHandled(true)

    println("TARGET: " +  target + " " + r.getMethod)

    res.setCharacterEncoding("utf-8")
    baseRequest.setAsyncSupported(true)
    r.setAsyncSupported(true)

    val asyncContext = baseRequest.startAsync()

    asyncContext.start(new Runnable {
      override def run(): Unit = {
        handler.resourceForTarget(target.substring(1)) onSuccess {
          case resour =>
            val res = asyncContext.getResponse.asInstanceOf[HttpServletResponse]
            val req = asyncContext.getRequest.asInstanceOf[HttpServletRequest]

            resour match {
              case Some(resource) =>
                req.getMethod match {
                  case "OPTIONS" =>
                    res.setHeader("DAV", "1")
                    res.setHeader("Allow", "GET,OPTIONS,PROPFIND")
                    res.setStatus(200)

                  case "HEAD" =>
                    res.setContentLength(resource.length)
                    res.setStatus(200)

                  case "PROPFIND" => res.setContentType("application/xml")
                    res.setStatus(207)

                    val depth = req.getHeader("Depth")
                    val input = XML.load(req.getInputStream)

                    val prop = propfind(input \ "prop" \ "_", resource, depth)

                    XML.write(res.getWriter, prop, "utf-8", true, null)
                    println("prop serve " + target)

                  case "GET" =>
                    res.setContentLength(resource.length)
                    res.setStatus(200)
                    println("serve " + target)

                    IO.copy(resource.stream, res.getOutputStream)
                }
              case _ =>
                println("NotFound! " + target)
                res.sendError(404, "Not found!")
            }

            asyncContext.complete()
        }
      }
    })
  }
}