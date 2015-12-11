package actor

import java.io.{File, PrintWriter}

import akka.actor.{Props, ActorRef, Actor}
import akka.util.Timeout
import client.dropbox.{DropboxClient, FileListEntity, FileListRequest}
import client._
import akka.pattern._
import spray.http.DateTime
import scala.concurrent.Future
import scala.concurrent.duration._
import spray.json._

import FileTreeJson._

object FileSystemActor {
  case object RefreshFolder
  case class FindNode(target: String)
  case object CacheFolder
  case class CreateFolder(target: String)

  def props(cacheFolder: File, client: DropboxClient): Props = Props(new FileSystemActor(cacheFolder, client))
}

class FileSystemActor(cacheFolder: File, client: DropboxClient) extends Actor {

  implicit val timeout: Timeout = 100 minutes

  import FileSystemActor._

  import context.dispatcher

  private var _files: Option[FolderNode] = None

  private val refreshFile = new File(cacheFolder.getAbsoluteFile + "/folders.json")

  private def getNodesForFolder(path: String, pathState: String = ""): Future[Seq[FileSystemNode]] = {

    val thisPath = if (pathState == "") path else pathState + path

    client.fileList(FileListRequest(thisPath)).map { flr =>
      flr.entries.flatMap {
        case FileListEntity(id, "folder", name, _ , _, _, _, _) =>

          Seq(LazyFolderNode(id, name, DateTime(0)))

        case FileListEntity(id, "file", name, _ , _, Some(server_modified), _, Some(size)) =>

          Seq(FileNode(id, name, size.toInt, DateTime(0)))

        case _ =>
          Seq()
      }
    }
  }

  private def resolveChilds(currentNode: FolderNode, remainder: String) = {
    println("RESOLVE: " + "/" + remainder + currentNode.name)

    import context.dispatcher
    getNodesForFolder("/" + remainder + currentNode.name).map { seq =>
      currentNode.asInstanceOf[LazyFolderNode]._childs = Some(seq)
      currentNode.childs
    }
  }

  private def search(url: String, currentNode: FolderNode, remainder: String = ""): Future[Option[(String, FileSystemNode)]] = {

    import context.dispatcher
    if (url == "" || url == "." || url == "/") {
      Future.apply(Some(("", currentNode)))
    } else {

      val paths = url.split("/")

      val currentChilds = if (currentNode.isResolved)
        Future {
          currentNode.childs
        }
      else {
        val resolvedFolder = resolveChilds(currentNode, remainder.replaceAll(currentNode.name + "/", ""))

        resolvedFolder onSuccess {
          case _ =>
            self ! CacheFolder
        }

        resolvedFolder
      }

      val child = currentChilds.map(c => c.find(c => c.name == paths.head))

      if (paths.length > 1) {

        child.flatMap { c =>
          val url = paths.splitAt(1)._2.foldLeft[String]("") { (p, i) =>
            if (p == "") i else p + "/" + i
          }

          c match {
            case Some(child) =>
              child.folder match {
                case Some(folder) =>
                  search(url, folder, remainder + paths.head + "/")
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
  }.flatMap {
    case r @ Some((remainder, node)) =>
      node match {
        case r: LazyFolderNode =>
          if (r.isResolved) Future { Some(remainder, r) }
          else
            resolveChilds(node.folder.get, remainder).map { seq =>
              r._childs = Some(seq)
              Some(remainder, r)
            }
        case _ =>
          Future { r }
      }
    case r: Option[(String, FileSystemNode)] =>
      Future { r }
  }

  override def preStart(): Unit = {
    if (refreshFile.exists) {
      val source = scala.io.Source.fromFile(refreshFile)
      val refreshFileString = try source.mkString finally source.close()

      try {
        val s = refreshFileString.parseJson
        _files = Some(s.convertTo[FolderNode])
      } catch {
        case e: Throwable =>
          println(e)
      }
    } else {
      _files = None
    }

  }

  def receive = {
    case RefreshFolder =>

      val folderFuture = getNodesForFolder("")
        .map(res => StaticFolderNode("", "", res, DateTime(0)))

      folderFuture onSuccess {
        case newFiles =>
          self ! CacheFolder
          _files.synchronized { _files = Some(newFiles) }
      }

      folderFuture pipeTo sender

    case FindNode(target) =>

      val res: Future[FolderNode] = if (_files.synchronized { _files.isEmpty }) {
        (self ? RefreshFolder).mapTo[FolderNode]
      } else {
        Future { _files.get }
      }

      res.flatMap(files => search(target, currentNode = files)) pipeTo sender

    case CacheFolder =>
      _files match {
        case Some(files) =>
          val newJson = files.asInstanceOf[FolderNode].toJson.toString()
          try {
            val writer = new PrintWriter(refreshFile)
            writer.write(newJson)
            writer.close()
          } catch {
            case e: Throwable =>
              println(e)
          }
        case _ =>
          println("nothing to cache")
      }

    case CreateFolder(target) =>

      sender ! None
  }

}
