import java.io.File
import actor.DavServerActor
import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.util.Timeout
import client.DropboxCachedFileHandler
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.typesafe.config.ConfigFactory
import spray.can.Http

/**
  * Created by tomas on 01-12-15.
  */

object Boot extends App {
  implicit val system = ActorSystem()

  val folder = new File(ConfigFactory.load().getString("client.cache-path"))
  folder.mkdirs()

  val config = new DbxRequestConfig("tomasharkema/cloud-client", "nl_NL")
  val accessToken = "ettg2dEwl1YAAAAAAAFt_2A5_otEFHMZe0021S5bz4uWwzHhBEylvPApp1tFGNw0"

  implicit val client = new DbxClientV2(config, accessToken)

  val fileHandler = new DropboxCachedFileHandler(folder)
  fileHandler.refreshFolder()

  // the handler actor replies to incoming HttpRequests
  val handler = system.actorOf(DavServerActor.props("http://localhost:7070", fileHandler), name = "dav-server-actor")

  IO(Http) ! Http.Bind(handler, interface = "localhost", port = 7070)
}
