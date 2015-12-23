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
  def size: Int

  def modifiedDate: DateTime

  @inline def file: Option[FileNode] = if (isFile) Some(this.asInstanceOf[FileNode]) else None
  @inline def folder: Option[FolderNode] = if (isFolder) Some(this.asInstanceOf[FolderNode]) else None
}

case class FileNode(id: String, name: String, size: Int, modifiedDate: DateTime) extends FileSystemNode {
  def isFile = true
  def isFolder = false
}

sealed abstract class FolderNode extends FileSystemNode {
  val isFile = false
  val isFolder = true
  def id: String
  def isResolved: Boolean
  val size = 0

  def childs: Seq[FileSystemNode]

  def addNode(folderNode: FolderNode)
}

case class StaticFolderNode(id: String, name: String, var childs: Seq[FileSystemNode], modifiedDate: DateTime) extends FolderNode {
  val isResolved = true

  override def addNode(folderNode: FolderNode) = {
    childs ++= Seq(folderNode)
  }
}

case class LazyFolderNode(id: String, name: String, modifiedDate: DateTime) extends FolderNode {

  def isResolved = _childs.nonEmpty

  var _childs: Seq[FileSystemNode] = Seq()

  def childs = _childs

  override def addNode(folderNode: FolderNode) = {
    _childs ++= Seq(folderNode)
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
