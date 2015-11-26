package client

import java.io.{FileOutputStream, FileInputStream, InputStream, File}
import java.nio.file.Files
import java.util.Date

import DavServer.{FileResource, Resource, FileHandler}
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.DbxFiles.FileMetadata
import sun.misc.IOUtils

import scala.concurrent.Future
import scala.xml.{Text, Elem, Node}

import scala.collection.JavaConverters._

import scala.concurrent.ExecutionContext.Implicits.global

case class DropboxResource(client: DbxClientV2)(absoluteFile: File, node: FolderAndFile) extends Resource {
  def file = new File(url)

  val formatter = {
    val df = new java.text.SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss z")
    df.setTimeZone(java.util.TimeZone.getTimeZone("GMT"))
    df
  }

  override def property(prop:Node):Option[Node] = {

    def easyNode(value: Node): Option[Node] =
      prop match {
        case Elem(p,l,at,sc) =>
          Some(Elem(p,l,at,sc,value))
      }

    def easy(value: String):Option[Node] =
      easyNode(Text(value))

    prop match {
      case <getlastmodified/> => easy(formatter.format(new Date()))//easy(httpDate(file.lastModified))

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

  override def children: Seq[Resource] = node.file.folder.map { folder =>
    folder.childs.map { child =>
      val faf = FolderAndFile(node.url, child)
      val dr = DropboxResource(client)(new File(absoluteFile + "/" + child.name), faf)
      dr
    }
  }.getOrElse(Seq())

  override def length: Int = if (node.file.isFolder)
    366 //node.file.folder.get.childs.length
  else {
    node.file.file.get.size
  }

  override def stream: InputStream = {
    if (absoluteFile.exists) {
      new FileInputStream(absoluteFile)
    } else {
      println("REQUEST FILE STREAM " + node.url)
      val fileDownload = client.files.downloadBuilder(node.url).start()
      println(fileDownload.result, "ABSOLUTE PARENT", absoluteFile.getParentFile)
      absoluteFile.getParentFile.mkdirs()
      Files.copy(fileDownload.body, absoluteFile.toPath)
      new FileInputStream(absoluteFile)
    }
  }
}

class DropboxCachedFileHandler(cacheFolder: File, client: DbxClientV2) extends FileHandler {

  var isRefreshing = false

  def getNodesForFolder(path: String, pathState: String = ""): Seq[FileSystemNode] = {

    val thisPath = if (pathState == "") path else pathState + path

    val entries = client.files.listFolder(thisPath).entries.asScala.toSeq

    println(entries.map(_.name))

    entries.map { el =>
      el match {
        case fileMetadata: FileMetadata =>
          FileNode(el.name, fileMetadata.size.toInt)
        case _ =>
          LazyFolderNode(el.name, { () =>
            getNodesForFolder("/" + el.name, thisPath)
          })
      }
    }
  }

  def refreshFolder(): Unit = {
      if (isRefreshing) {
        return
      }

      _files = None
      isRefreshing = true
      Future {
        StaticFolderNode("", getNodesForFolder(""))
      } onSuccess {
        case files =>
          _files = Some(files)
      }
  }

  var _files: Option[FolderNode] = None

  def files(): Future[FolderNode] = {
      if (_files.isDefined) {
        Future.apply(_files.get)
      } else {
        refreshFolder()
        Future {
          while (_files.isEmpty) {
            Thread.sleep(1000)
          }
          _files.get
        }
      }
  }

  def pathCreate(previous: Seq[FileSystemNode]): String =
    previous.filter(_.isFolder).foldLeft[String]("") { (prev, el) =>
      prev + "/" + el.name
    }

  override def resourceForTarget(target: String): Future[Option[Resource]] = {
    files().map { e =>
      e.search(target).map { folderChain =>
        val absoluteFile = new File(cacheFolder.getAbsolutePath + "/" + target)
        val file = new File(target)
        DropboxResource(client)(absoluteFile, FolderAndFile(folderChain._1, folderChain._2))
      }
    }
  }
}
