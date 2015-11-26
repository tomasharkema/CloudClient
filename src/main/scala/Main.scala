
import java.io.File

import DavServer._
import client.DropboxCachedFileHandler
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.{DbxFiles, DbxClientV2}
import scala.collection.JavaConverters._

object Main {

  val accessToken = "ettg2dEwl1YAAAAAAAFt_2A5_otEFHMZe0021S5bz4uWwzHhBEylvPApp1tFGNw0"

  def main(args: Array[String]) {
    val folder = new File("/Users/tomas/.cloudcache")
    folder.mkdirs()

    val config = new DbxRequestConfig("dropbox/java-tutorial", "nl_NL")
    val client = new DbxClientV2(config, accessToken)

    var fileHandler = new DropboxCachedFileHandler(folder, client)

    val server = new DavServer(7070, fileHandler)
    server.startServer()



    fileHandler.refreshFolder()
  }
}