package client

import java.io.File

import com.typesafe.config.ConfigFactory

object Config {

  val file = new File("./client.conf")

  def configFact = ConfigFactory.parseFile(file)

  def cachePath = configFact.getString("client.cache-path")
  def accessToken = configFact.getString("client.access-token")

  def analyze: Boolean = {

    if (!file.exists()) {
      println("-----------------------")
      println("-----------------------")

      println("You should create a client.conf file!")
      println("Looking for: " + file.getAbsolutePath)

      println("-----------------------")
      println("-----------------------")

      System.exit(1)
    }

    cachePath
    accessToken

    true
  }
}
