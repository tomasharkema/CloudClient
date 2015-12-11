import java.io.File

import org.junit.runner.RunWith
import org.specs2.mutable._
import org.specs2.runner.JUnitRunner
import org.specs2.time.NoTimeConversions

import akka.actor._
import akka.testkit._
import scala.concurrent.duration._

//@RunWith(classOf[JUnitRunner])
//class FileSystemTest extends Specification with NoTimeConversions {
//
//  implicit val timeout: Timeout = 1 minutes
//
////  implicit val system = ActorSystem("dropbox-cloud-client-test")
////
////  val accessToken = "ettg2dEwl1YAAAAAAAFt_2A5_otEFHMZe0021S5bz4uWwzHhBEylvPApp1tFGNw0"
////  val folder = new File(ConfigFactory.load().getString("client.cache-path"))
////  val client = new DropboxClient(accessToken)
////
////  val actorRef = TestActorRef(FileSystemActor.props(folder, client))
////  val actor = actorRef.underlyingActor
//
//  "FileSystem search" should {
//    "find nothing without any folder present" in {
////      val future = actorRef ? FindNode("/")
////      val Success(Some((path, node))) = future.value.get
////      println(path, node)
//      "" must be equalTo ""
//    }
//  }
//}

abstract class AkkaSpecs2Support extends TestKit(ActorSystem())
  with After
  with ImplicitSender {
  // make sure we shut down the actor system after all tests have run
  def after = system.terminate()
}

@RunWith(classOf[JUnitRunner])
class AkkaTestkitSpecs2SupportSpec extends Specification
  with NoTimeConversions {

  "A TestKit" should {
    "work properly with Specs2 unit tests" in new AkkaSpecs2Support {
      within(1 second) {
        system.actorOf(Props(new Actor {
          def receive = { case x ⇒ sender ! x }
        })) ! "hallo"

        expectMsgType[String] must be equalTo "hallo"
      }
    }
  }

}
