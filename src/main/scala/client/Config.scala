package client

import java.io.File

import com.typesafe.config.ConfigFactory

object Config {

  def configFact = ConfigFactory.load("client.conf")

  val cachePath = configFact.getString("client.cache-path")
  val accessToken = configFact.getString("client.access-token")

  def analyze: Unit = {
    cachePath
    accessToken
  }
}
