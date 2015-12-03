package client.dropbox

import akka.util.Timeout
import client.{FileSystemNode, LazyFolderNode, FileNode}
import spray.http._
import spray.httpx.encoding._

import scala.concurrent.duration._
import scala.util._
import akka.actor.ActorSystem
import spray.json._
import spray.client.pipelining._
import spray.httpx.marshalling._
import spray.httpx.unmarshalling._

case class FileDownloadRequest(path: String)

object FileDownloadRequestFormatter extends DefaultJsonProtocol {
  implicit val FileDownloadRequestFormat = jsonFormat1(FileDownloadRequest.apply)
}

object FileDownloadRequest extends DefaultJsonProtocol {
  implicit val FileDownloadRequestMarshaller =
    Marshaller.of[FileDownloadRequest](ContentTypes.`application/json`) { (value, contentType, ctx) =>
      import FileDownloadRequestFormatter._
      val string = value.toJson.compactPrint
      ctx.marshalTo(HttpEntity(contentType, string))
    }
}

case class FileListRequest(path: String,
                           recursive: Boolean = false,
                           include_media_info: Boolean = false,
                           include_deleted: Boolean = false)

object FileListRequestFormatter extends DefaultJsonProtocol {
  implicit val FileListRequestFormat = jsonFormat4(FileListRequest.apply)
}

object FileListRequest extends DefaultJsonProtocol {
  implicit val FileListRequestMarshaller =
    Marshaller.of[FileListRequest](ContentTypes.`application/json`) { (value, contentType, ctx) =>
      import FileListRequestFormatter._
      val string = value.toJson.compactPrint
      ctx.marshalTo(HttpEntity(contentType, string))
    }
}

case class FileListEntity(id: String,
                          `.tag`: String,
                          name: String,
                          path_lower: String,
                          client_modified: Option[String],
                          server_modified: Option[String],
                          rev: Option[String],
                          size: Option[Long]) {
  def toFileNode: Option[FileSystemNode] = {
    this match {
      case FileListEntity(id, "folder", name, _, _, _, _, _) =>
        Some(LazyFolderNode(id, name, DateTime(0)))

      case FileListEntity(id, "file", name, _, _, Some(server_modified), _, Some(size)) =>
        Some(FileNode(id, name, size.toInt, DateTime(0)))

      case _ =>
        None
    }
  }
}

object FileListEntityFormatting extends DefaultJsonProtocol {
  implicit val FileListEntityFormat = jsonFormat8(FileListEntity.apply)
}

case class FileListResponse(entries: Seq[FileListEntity])

object FileListResponseFormatting extends DefaultJsonProtocol {
  import FileListEntityFormatting._
  implicit val FileListResponseFormat = jsonFormat1(FileListResponse.apply)
}

object FileListResponse {
  implicit val FileListResponseUnMarshaller =
    Unmarshaller[FileListResponse](ContentTypeRange.apply(MediaTypes.`application/json`)) {
      case HttpEntity.NonEmpty(contentType, data) =>
        import FileListResponseFormatting._
        data.asString.parseJson.convertTo[FileListResponse]
    }
}

class DropboxClient(accessToken: String)(implicit system: ActorSystem) {

  import FileDownloadRequestFormatter._

  import system.dispatcher

  def addAuthorization = addCredentials(OAuth2BearerToken(accessToken))

  def fileList(request: FileListRequest) = {
    val pipeline = (
      addAuthorization
      ~> sendReceive
      ~> decode(Deflate)
      ~> unmarshal[FileListResponse]
      )

    pipeline {
      Post("https://api.dropboxapi.com/2/files/list_folder", request)
    }
  }

  def downloadHeader(pathRequest: FileDownloadRequest) = HttpHeaders.RawHeader("Dropbox-API-Arg", pathRequest.toJson.compactPrint)

  def downloadFile(request: FileNode) = {

    implicit val FileUnmarshaller = new FromResponseUnmarshaller[Stream[HttpData]] {
      import spray.json._
      import DefaultJsonProtocol._

      def apply(response: HttpResponse) = {
        Right(response.entity.data.toChunkStream(1024))
      }
    }

    val pipeline = (addAuthorization
      ~> sendReceive
      ~> unmarshal[Stream[HttpData]]
      )

    pipeline {
      Post("https://content.dropboxapi.com/2/files/download")
        .withHeaders(downloadHeader(FileDownloadRequest(request.id)))
    }
  }
}
