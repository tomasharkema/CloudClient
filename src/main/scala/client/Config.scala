package client

import java.io.File

import com.typesafe.config.ConfigFactory

object Config {

  val file = new File("./client.conf")

  val isTest = if(ConfigFactory.load().hasPath("client.isTest")) {
    ConfigFactory.load().getBoolean("client.isTest")
  } else {
    false
  }

  def configFact = ConfigFactory.parseFile(file)

  def cachePath = configFact.getString("client.cache-path") + (if (isTest) "/test" else "")
  def accessToken = configFact.getString("client.access-token")
  def download = configFact.getBoolean("client.download")
  def port = configFact.getInt("client.port")

  def isValid: Boolean =
    configFact.hasPath("client.cache-path") &&
      configFact.hasPath("client.access-token") &&
      configFact.hasPath("client.port")

  def analyze: Boolean = {

    if (!file.exists() && isValid) {
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
