package actor

import java.io.File

import actor.FileUploadActor.Upload
import akka.actor.{Props, Actor}
import client.dropbox.DropboxClient

import scala.concurrent.Future
import scala.concurrent.duration._

object FileUploadActor {
  case object Upload

  def props(cacheFolder: File, client: DropboxClient): Props = Props(new FileUploadActor(cacheFolder, client))
}

class FileUploadActor(cacheFolder: File, client: DropboxClient) extends Actor {

  private val uploadFolder = new File(cacheFolder.getAbsolutePath + "/upload")

  override def preStart(): Unit = {
    self ! Upload
  }

  private def walkFolder(folder: File): Either[File, File] = folder.listFiles().filter(_.getName != ".DS_Store").headOption match {
    case Some(file) =>
      if (file.isFile) {
        Right(file)
      } else {
        walkFolder(file)
      }

    case _ =>
      Left(folder)
  }

  def receive = {
    case Upload =>
      import context.dispatcher
      val uploadFuture = walkFolder(uploadFolder) match {
        case Right(file) =>
          client.uploadFile()
          Future {}
        case Left(folder) =>
          println("Needs to remove folder: " + folder.getAbsolutePath)
          Future { folder.delete() }
      }

      uploadFuture onComplete {
        case _ =>
          context.system.scheduler.scheduleOnce(5 seconds, self, Upload)
      }

  }

}
