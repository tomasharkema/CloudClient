package client

import java.io._
import java.nio.file.Files
import java.util.Date
import java.util.concurrent.Executors
import client.FileTreeJson._
import actor.FileHandler
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.DbxFiles.{FolderMetadata, FileMetadata}
import com.typesafe.config.ConfigFactory
import spray.http.DateTime
import sun.misc.IOUtils
import spray.json._

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.{Text, Elem, Node}

import scala.collection.JavaConverters._

import scala.concurrent.ExecutionContext.Implicits.global

case class DropboxResource(absoluteFile: File, node: FolderAndFile)(implicit client: DbxClientV2, fileDownloader: DropboxFileDownloader) extends Resource {
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

  override def children: Seq[Resource] = node.file.folder.map { folder =>
    folder.childs.map { child =>
      DropboxResource(new File(absoluteFile + "/" + child.name), FolderAndFile(node.url, child))
    }
  }.getOrElse(Seq())


  override def date: DateTime = node.file.modifiedDate

  override def length: Int = if (node.file.isFolder)
    366 //node.file.folder.get.childs.length
  else {
    node.file.file.get.size
  }

  override def stream: Future[File] = fileDownloader.stream(absoluteFile, node)
}

class DropboxFileDownloader(client: DbxClientV2) {

  var hits = mutable.HashMap[String, Future[File]]()

  private def doneWithHit(node: FolderAndFile) = {
    hits.remove(node.url)
  }

  def stream(absoluteFile: File, node: FolderAndFile): Future[File] = {
    hits.get(node.url) match {
      case Some(future) =>
        future
      case None =>

        val future = if (absoluteFile.exists) {
          println("Serving " + node.url + "...")
          Future.apply(absoluteFile)
        } else {
          val downloadEnabled = ConfigFactory.load().getBoolean("client.download")

          val f = if (downloadEnabled)
            Future {
              println("Downloading " + node.url + "... concurrent: " + hits.keys.size)
              absoluteFile.getParentFile.mkdirs()
              val fileDownload = client.files.downloadBuilder(node.url).start()
              Files.copy(fileDownload.body, absoluteFile.toPath)
              doneWithHit(node)
              println("Downloaded " + node.url + "... concurrent: " + hits.keys.size)
              absoluteFile
            }
          else
            Future.failed(new IllegalStateException())

          hits ++= Map(node.url -> f)
          f
        }

        future
    }
  }
}

class DropboxCachedFileHandler(cacheFolder: File)(implicit client: DbxClientV2) extends FileHandler {

  private var isRefreshing = false

  implicit var fileDownloader = new DropboxFileDownloader(client)

  refreshFolder()

  def getNodesForFolder(path: String, pathState: String = ""): Seq[FileSystemNode] = {

    val thisPath = if (pathState == "") path else pathState + path

    val entries = client.files.listFolder(thisPath).entries.asScala.toSeq

    entries.map {
      case fileMetadata: FileMetadata =>
        FileNode(fileMetadata.name, fileMetadata.size.toInt, DateTime(fileMetadata.serverModified.getTime))
      case folderMetaData: FolderMetadata =>
        LazyFolderNode(folderMetaData.name, { () =>
          getNodesForFolder("/" + folderMetaData.name, thisPath)
        }, DateTime(0))
    }
  }

  def refreshFolder(): Unit = {
    if (isRefreshing) {
      return
    }

    println("Refresh folder")

    val refreshFile = new File(cacheFolder.getAbsoluteFile + "/folders.json")

    isRefreshing = true

    if (refreshFile.exists) {
      val source = scala.io.Source.fromFile(refreshFile)
      val refreshFileString = try source.mkString finally source.close()

      try {
        val s = refreshFileString.parseJson
        _files = Some(s.convertTo[FolderNode])
        isRefreshing = false
      } catch {
        case e =>
          println(e)
      }
    } else {
      _files = None
    }

    Future {
      StaticFolderNode("", getNodesForFolder(""), DateTime(0))
    } onSuccess {
      case files =>
        println("Done refreshing folder")
        _files = Some(files)

        val newJson = files.asInstanceOf[FolderNode].toJson.toString()
        val writer = new PrintWriter(refreshFile)
        writer.write(newJson)
        writer.close()
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
          Thread.sleep(100)
        }
        _files.get
      }
    }
  }

  def pathCreate(previous: Seq[FileSystemNode]): String =
    previous.filter(_.isFolder).foldLeft[String]("") { (prev, el) =>
      prev + "/" + el.name
    }

  override def resourceForTarget(t: String): Future[Option[Resource]] = {
    val target = t.substring(1)
    files().map { e =>
      e.search(target).map { folderChain =>
        val absoluteFile = new File(cacheFolder.getAbsolutePath + "/files/" + target)
        val file = new File(target)
        DropboxResource(absoluteFile, FolderAndFile(folderChain._1, folderChain._2))
      }
    }
  }
}
