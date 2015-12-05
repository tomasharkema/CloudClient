import java.io.File
import actor.{FileUploadActor, FileSystemActor, FileLockingActor, DavServerActor}
import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.util.Timeout
import client.DropboxCachedFileHandler
import client.dropbox.{FileDownloadRequest, FileListRequest, DropboxClient}
import com.typesafe.config.ConfigFactory
import spray.can.Http

/**
  * Created by tomas on 01-12-15.
  */

object Boot extends App {

  implicit val system = ActorSystem("dropbox-cloud-client")

  val folder = new File(ConfigFactory.load().getString("client.cache-path"))
  folder.mkdirs()

  val accessToken = "ettg2dEwl1YAAAAAAAFt_2A5_otEFHMZe0021S5bz4uWwzHhBEylvPApp1tFGNw0"

  implicit val client = new DropboxClient(accessToken)

  val fileLockActor = system.actorOf(Props[FileLockingActor], "dav-file-lock-actor")
  val fileSystemActor = system.actorOf(FileSystemActor.props(folder, client), "dav-file-system-actor")
  val fileUploadActor = system.actorOf(FileUploadActor.props(folder, client), "dav-file-upload-actor")

  val fileHandler = new DropboxCachedFileHandler(folder, fileSystemActor)
  fileHandler.refreshFolder()

  // the handler actor replies to incoming HttpRequests
  val handler = system.actorOf(DavServerActor.props("http://localhost:7070", fileHandler, fileLockActor, fileSystemActor), name = "dav-server-actor")

  IO(Http) ! Http.Bind(handler, interface = "localhost", port = 7070)
}
