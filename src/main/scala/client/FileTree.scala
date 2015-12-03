package client

import java.util.Date

import client.dropbox.{FileListRequest, DropboxClient}
import spray.http.DateTime

import scala.concurrent._
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util._

sealed abstract class FileSystemNode {
  def id: String
  def name: String
  def isFile: Boolean
  def isFolder: Boolean

  def modifiedDate: DateTime

  @inline def file: Option[FileNode] = if (isFile) Some(this.asInstanceOf[FileNode]) else None
  @inline def folder: Option[FolderNode] = if (isFolder) Some(this.asInstanceOf[FolderNode]) else None
}

case class FileNode(id: String, name: String, size: Int, modifiedDate: DateTime) extends FileSystemNode {
  def isFile = true
  def isFolder = false
}

sealed abstract class FolderNode extends FileSystemNode {
  def isFile = false
  def isFolder = true
  def id: String
  def isResolved: Boolean

  def childs: Future[Seq[FileSystemNode]]

  def search(url: String, remainder: String = ""): Future[Option[(String, FileSystemNode)]] = {
    childs.flatMap { c =>
      if (url == "" || url == "." || url == "/") {
        Future.apply(Some(("", this)))
      } else {

        val paths = url.split("/")

        val child = Future {
          c.find(c => c.name == paths.head)
        }

        if (paths.length > 1) {

          child.flatMap { c =>
            val url = paths.splitAt(1)._2.foldLeft[String]("") { (p, i) =>
              if (p == "") i else p + "/" + i
            }

            c match {
              case Some(child) =>
                child.folder match {
                  case Some(folder) =>
                    folder.search(url, remainder + paths.head + "/")
                  case None =>
                    Future.apply(None)
                }
              case None =>
                Future.apply(None)
            }
          }
        } else {
          child.map(c => c.map { c => (remainder, c) })
        }
      }
    }
  }
}

case class LazyFolderNodeNeedsClient(id: String, name: String, modifiedDate: DateTime) extends FolderNode {
  def isResolved = childsInjected.isDefined

  var isPending = false
  var childsInjected: Option[Seq[FileSystemNode]] = None

  def childs: Future[Seq[FileSystemNode]] = Future {
    childsInjected match {
      case Some(e) =>
        e
      case None =>
        while (childsInjected.isEmpty && isPending) {
          Thread.sleep(1000)
        }
        childsInjected match {
          case Some(s) =>
            s
          case None =>
            throw new IllegalStateException("")
        }
    }
  }
}

case class StaticFolderNode(id: String, name: String, childs: Future[Seq[FileSystemNode]], modifiedDate: DateTime) extends FolderNode {
  val isResolved = true
}

case class LazyFolderNode(id: String, name: String, modifiedDate: DateTime)(implicit client: DropboxClient) extends FolderNode {

  def isResolved = _child.isDefined

  var _child: Option[Seq[FileSystemNode]] = None

  def childs = _child match {
    case Some(child) =>
      Future { child }
    case None =>

      val newChild = client.fileList(FileListRequest("/"+name)).map { res =>
        res.entries.map { r => r.toFileNode }
      }

      newChild onComplete {
        case Success(c) =>
          _child = Some(c)
        case Failure(e) =>
          e.printStackTrace()
      }
      newChild onFailure {
        case e =>
          println(e)
      }
      newChild
  }
}

case class FolderAndFile(path: String, file: FileSystemNode) {

  def pathFix(string: String): String = {

    def s(pathFix: String) = pathFix.replaceAll("/./", "/").replaceAll("//", "/")

    if (!string.startsWith("/"))
      s("/" + string)
    else
      s(string)
  }

  def url = pathFix(path +
    file.file.map(file => "/" + file.name)
      .getOrElse(file.folder.map(file => "/" + file.name).getOrElse("")))
}
