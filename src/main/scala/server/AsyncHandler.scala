package server

import javax.servlet.{ServletRequestAttributeEvent, ServletRequestAttributeListener, AsyncEvent, AsyncListener}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}

import org.eclipse.jetty.server._
import org.eclipse.jetty.server.handler._

trait AsyncHandlerTrait {
  def handle(target: String, req: HttpServletRequest, res: HttpServletResponse, complete: (() => Unit))
}

class AsyncHandler(handler: AsyncHandlerTrait) extends ErrorHandler {
  override def handle(target: String, baseRequest: Request, req: HttpServletRequest, res: HttpServletResponse): Unit = {
    baseRequest.setHandled(true)

    res.setCharacterEncoding("utf-8")
    baseRequest.setAsyncSupported(true)

    val asyncContext = baseRequest.startAsync()

    asyncContext.start(new Runnable {
      override def run(): Unit = {
        def res = asyncContext.getResponse.asInstanceOf[HttpServletResponse]
        def req = asyncContext.getRequest.asInstanceOf[HttpServletRequest]

        handler.handle(target, req, res, { () => asyncContext.complete() })
      }
    })
  }
}
