package client

import java.io.File

import com.typesafe.config.ConfigFactory

object Config {

  val file = new File("./client.conf")

  def configFact = ConfigFactory.parseFile(file)

  val cachePath = configFact.getString("client.cache-path")
  val accessToken = configFact.getString("client.access-token")

  def analyze: Unit = {

    if (!file.exists()) {
      println("-----------------------")
      println("-----------------------")

      println("You should create a client.conf file!")

      println("-----------------------")
      println("-----------------------")
    }

    cachePath
    accessToken
  }
}
