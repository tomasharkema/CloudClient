package DavServer

import java.io.{FileInputStream, InputStream, File}
import scala.xml._


trait Resource {
  def property(prop:Node): Option[Node] = prop match {
    case t @ <resourcetype/> => Some(t)
    case _ => None
  }

  def url: String
  def children: Seq[Resource]
  def length: Int
  def stream: InputStream
}

case class FileResource(file: File) extends Resource {
  val formatter = {
    val df = new java.text.SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss z")
    df.setTimeZone(java.util.TimeZone.getTimeZone("GMT"))
    df
  }

  def httpDate(time: Long): String = formatter.format(new java.util.Date(time))

  override def property(prop:Node):Option[Node] = {

  def easyNode(value: Node): Option[Node] =
      prop match {
        case Elem(p,l,at,sc) =>
          Some(Elem(p,l,at,sc,value))
      }

    def easy(value:String):Option[Node] =
      easyNode(Text(value))

    prop match{
      case <getlastmodified/> => easy(httpDate(file.lastModified))
      case <getcontentlength/> => easy(file.length.toString)
      case <resourcetype/> => {
        if (file.isDirectory) easyNode(<D:collection/>) else Some(prop)
      }
      case _ => super.property(prop)
    }
  }

  def url = file.getPath.substring(1)+(if (file.isDirectory) "/" else "")

  def children = file.listFiles.map(new FileResource(_)).toSeq

  def length = file.length.intValue

  def stream = new FileInputStream(file)
}