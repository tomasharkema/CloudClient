package actor

import java.io.File

import actor.FileUploadActor.Upload
import akka.actor.{Props, Actor}
import client.dropbox.{FileUploadResponse, FileUploadRequest, DropboxClient}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success

object FileUploadActor {
  case object Upload

  def props(cacheFolder: File, client: DropboxClient): Props = Props(new FileUploadActor(cacheFolder, client))
}

class FileUploadActor(cacheFolder: File, client: DropboxClient) extends Actor {

  private val uploadFolder = new File(cacheFolder.getAbsolutePath + "/upload")

  override def preStart(): Unit = {
    self ! Upload
  }

  private def walkFolder(folder: File): Option[Either[File, File]] = {
    val list = folder.listFiles()
    if (list == null) {
      None
    } else list
      .filter(_.getName != ".DS_Store").headOption match {
      case Some(file) =>
        if (file.isFile) {
          Some(Right(file))
        } else {
          walkFolder(file)
        }

      case _ =>
        Some(Left(folder))
    }
  }

  def receive = {
    case Upload =>
      import context.dispatcher
      val uploadFuture: Future[Option[(FileUploadResponse, File)]] = walkFolder(uploadFolder) match {
        case Some(Right(file)) =>
          println("Uploading " + file)
          client.uploadFile(file, FileUploadRequest(file.getAbsolutePath.replaceAll(uploadFolder.getAbsolutePath, "")))
            .map(res => Some((res, file)))
        case Some(Left(folder)) =>
          println("Needs to remove folder: " + folder.getAbsolutePath)
          Future {
            folder.listFiles().foreach(_.delete())
            folder.delete()
            None
          }
        case _ =>
          Future(None)
      }

      uploadFuture onComplete {
        case Success(Some((res, file))) =>
          val newFile = new File(cacheFolder.getAbsolutePath + "/files/" + file.getAbsolutePath.replaceAll(uploadFolder.getAbsolutePath, ""))
          newFile.getParentFile.mkdirs()
          println(newFile.getAbsolutePath)
          file.renameTo(newFile)
          println("Done uploading")
          context.system.scheduler.scheduleOnce(5 millis, self, Upload)

        case _ =>
          context.system.scheduler.scheduleOnce(10 seconds, self, Upload)
      }

  }

}
