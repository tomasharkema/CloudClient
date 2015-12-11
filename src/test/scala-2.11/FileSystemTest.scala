import java.io.File

import actor.FileSystemActor
import actor.FileSystemActor.FindNode
import akka.actor.ActorSystem
import client.{FileSystemNode, Config}
import client.dropbox.DropboxClient
import com.typesafe.config.ConfigFactory
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.time.NoTimeConversions
import akka.pattern._
import scala.util.Success
import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class FileSystemTest extends Specification with NoTimeConversions {

  val accessToken = Config.accessToken

  def prepare()(implicit system: ActorSystem) = {
    val folder = new File(Config.cachePath)
    val client = new DropboxClient(accessToken)
    (folder, client, system.actorOf(FileSystemActor.props(folder, client)))
  }

  "FileSystem search" should {

    "find root in dropbox folder" in new AkkaSpecs2Support {
      within(1 second) {
        val (_, _, actor) = prepare

        actor ! FindNode("/")

        val result = expectMsgType[Option[(String, FileSystemNode)]]

        result match {
          case Some((path, nodes)) =>
            path must be equalTo ""
          case None =>
            ko("Should have found root")
        }
      }
    }

    "not find incorrect subfolder in dropbox folder" in new AkkaSpecs2Support {
      within(1 second) {
        val (_, _, actor) = prepare

        actor ! FindNode("/A/B/C/D/E")

        val result = expectMsgType[Option[(String, FileSystemNode)]]

        result match {
          case Some((path, nodes)) =>
            ko("Should not have found a folder")
          case None =>
            ok("No folder found!")
        }
      }
    }

    "find a correct subfolder in dropbox folder" in new AkkaSpecs2Support {
      within(10 second) {
        val (_, _, actor) = prepare

        actor ! FindNode("Afbeeldingen/2015/2015-12-09")

        val result = expectMsgType[Option[(String, FileSystemNode)]]

        result match {
          case Some((path, nodes)) =>
            path must be equalTo "Afbeeldingen/2015/"
            nodes.name must be equalTo "2015-12-09"
          case None =>
            ko("Should have found a folder")
        }
      }
    }

  }
}