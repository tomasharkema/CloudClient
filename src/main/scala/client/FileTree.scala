package client

import java.util.Date

import spray.http.DateTime

import scala.concurrent._
import scala.concurrent.duration._

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

  def childs: Seq[FileSystemNode]

  def search(url: String, remainder: String = ""): Option[(String, FileSystemNode)] = {

    if (url == "" || url == "." || url == "/") {
      return Some(("", this))
    }

    val paths = url.split("/")

    val child = childs.find(c => c.name == paths.head)

    if (paths.length > 1) {
      return child.flatMap(c => c.folder.flatMap { c =>
        val url = paths.splitAt(1)._2.foldLeft[String]("") { (p, i) =>
          if (p == "") i else p + "/" + i
        }
        c.search(url, remainder + paths.head + "/")
      })
    }

    child.map(c => (remainder, c))
  }
}

case class StaticFolderNode(id: String, name: String, childs: Seq[FileSystemNode], modifiedDate: DateTime) extends FolderNode

case class LazyFolderNode(id: String, name: String, modifiedDate: DateTime) extends FolderNode {
  def childs = Seq()
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
