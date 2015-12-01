
import java.io.File
import java.lang.Iterable

import DavServer._
import client.DropboxCachedFileHandler
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.http.HttpRequestor
import com.dropbox.core.http.HttpRequestor.{Response, Uploader, Header}
import com.dropbox.core.v2.{DbxFiles, DbxClientV2}
import scala.collection.JavaConverters._

object Main {

  val accessToken = "ettg2dEwl1YAAAAAAAFt_2A5_otEFHMZe0021S5bz4uWwzHhBEylvPApp1tFGNw0"

  def main(args: Array[String]) {
    args.foreach(println)

    val folder = new File("/Users/tomas/.cloudcache")
    folder.mkdirs()

    val config = new DbxRequestConfig("tomasharkema/cloud-client", "nl_NL")

    implicit val client = new DbxClientV2(config, accessToken)

    val fileHandler = new DropboxCachedFileHandler(folder)

    val server = new DavServer(7070, fileHandler)
    server.startServer()

    fileHandler.refreshFolder()
  }
}
