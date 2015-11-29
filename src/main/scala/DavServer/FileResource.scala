package DavServer

import java.io.{FileInputStream, InputStream, File}
import scala.concurrent.Future
import scala.xml._


trait Resource {
  def property(prop:Node): Option[Node] = prop match {
    case t @ <resourcetype/> => Some(t)
    case _ => None
  }

  def url: String
  def children: Seq[Resource]
  def length: Int
  def stream: Future[InputStream]
}