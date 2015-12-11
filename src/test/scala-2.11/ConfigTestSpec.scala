import client.Config
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ConfigTestSpec extends Specification {
  "Config file" should {
    "be present and contain the right fields" in {
      Config.analyze must be equalTo true
    }
  }
}
