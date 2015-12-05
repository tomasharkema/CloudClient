package client

import java.io._
import java.util.Date
import actor.FileSystemActor.{FindNode, RefreshFolder}
import akka.actor._
import akka.util.Timeout
import actor.FileHandler
import client.dropbox._
import com.typesafe.config.ConfigFactory
import spray.http.{HttpData, DateTime}
import scala.collection.mutable
import scala.concurrent._
import scala.util._
import scala.xml.{Text, Elem, Node}
import scala.concurrent.duration._
import akka.pattern._
import scala.concurrent.ExecutionContext.Implicits.global

case class DropboxResource(absoluteFile: File, node: FolderAndFile)(implicit client: DropboxClient, fileDownloader: DropboxFileDownloader) extends Resource {
  def file = new File(url)

  val formatter = {
    val df = new java.text.SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss z")
    df.setTimeZone(java.util.TimeZone.getTimeZone("GMT"))
    df
  }

  override def property(prop:Node): Option[Node] = {

    def easyNode(value: Node): Option[Node] =
      prop match {
        case Elem(p,l,at,sc) =>
          Some(Elem(p,l,at,sc,value))
      }

    def easy(value: String):Option[Node] =
      easyNode(Text(value))

    prop match {
      case <getlastmodified/> => easy(formatter.format(new Date(node.file.modifiedDate.clicks)))

      case <getcontentlength/> => easy(length.toString)

      case <resourcetype/> =>
        if (node.file.isFolder)
          easyNode(<D:collection/>)
        else
          Some(prop)

      case _ => super.property(prop)
    }
  }

  override def url: String = node.url

  override def children: Future[Seq[Resource]] = Future { node.file.folder.get.childs.map { child =>
    DropboxResource(new File(absoluteFile + "/" + child.name), FolderAndFile(node.url, child))
  }}

  override def date: DateTime = node.file.modifiedDate

  override def length: Int = if (node.file.isFolder)
    0
  else
    node.file.file.get.size

  override def stream: Future[File] = fileDownloader.stream(absoluteFile, node)

}

class DropboxFileDownloader(client: DropboxClient) {

  var hits = mutable.HashMap[String, Future[File]]()

  private def doneWithHit(node: FolderAndFile) = {
    hits.remove(node.url)
    println("STIL BUSY WITH" + hits.keys)
  }

  def stream(absoluteFile: File, node: FolderAndFile): Future[File] = {
    hits.get(node.url) match {
      case Some(future) =>
        future
      case None =>
        if (absoluteFile.exists) {
          println("Serving " + node.url + "...")
          Future.apply(absoluteFile)
        } else {
          val downloadEnabled = ConfigFactory.load().getBoolean("client.download")
          val isTooBig = node.file.size > 524288000
          val f = if (downloadEnabled && !isTooBig) {
            Future {
              println("Downloading " + node.url + " had more: " + hits.size)
              absoluteFile.getParentFile.mkdirs()
            }.flatMap { e =>
              client.downloadFile(node.file.file.get).map { stream =>
                val target = new FileOutputStream(absoluteFile)
                try stream.foreach { chunck =>
                  target.write(chunck.toByteArray)
                } finally target.close()
                doneWithHit(node)
                println("Downloaded " + node.url)
                absoluteFile
              }
            }
          }
          else {
            Future.failed(new IllegalStateException())
          }

          f onFailure {
            case e =>
              doneWithHit(node)
              println(e)
          }

          hits ++= Map(node.url -> f)
          f
        }
    }
  }
}

class DropboxCachedFileHandler(cacheFolder: File, fileSystemActor: ActorRef)(implicit client: DropboxClient) extends FileHandler {

  implicit val timeout: Timeout = 100 minutes

  private var isRefreshing = false

  implicit val fileDownloader = new DropboxFileDownloader(client)

  def refreshFolder(): Unit = {
    fileSystemActor ! RefreshFolder
  }

  def searchForFile(target: String): Future[Option[(String, FileSystemNode)]] = (fileSystemActor ? FindNode(target)).mapTo[Option[(String, FileSystemNode)]]

  def pathCreate(previous: Seq[FileSystemNode]): String =
    previous.filter(_.isFolder).foldLeft[String]("") { (prev, el) =>
      prev + "/" + el.name
    }

  override def resourceForTarget(t: String): Future[Option[Resource]] = {
    val target = t.replaceAll("//", "/").substring(1)

    searchForFile(target).map { folderChain =>
      folderChain.map { chain =>
        val absoluteFile = new File(cacheFolder.getAbsolutePath + "/files/" + target)
        val file = new File(target)
        DropboxResource(absoluteFile, FolderAndFile(chain._1, chain._2))
      }
    }
  }

  override def uploadResource(t: String, stream: Stream[HttpData]): Future[Boolean] = Future {
    val target = t.substring(1)
    val targetFile = new File(cacheFolder.getAbsolutePath + "/upload/" + target)

    if (targetFile.exists()) {
      false
    } else {

      targetFile.getParentFile.mkdirs()
      val targetOutput = new FileOutputStream(targetFile)
      try stream.foreach { chunck =>
        targetOutput.write(chunck.toByteArray)
      } finally targetOutput.close()

      true
    }
  }

}
