name := "cloud-client"

version := "1.0"

scalaVersion := "2.11.7"
libraryDependencies += "org.eclipse.jetty" % "jetty-server" % "9.3.5.v20151012"
libraryDependencies += "org.eclipse.jetty" % "jetty-continuation" % "9.3.5.v20151012"
libraryDependencies += "org.scala-lang.modules" % "scala-xml_2.11" % "1.0.5"
libraryDependencies += "com.dropbox.core" % "dropbox-core-sdk" % "2.0-beta-2"
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.0"
libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.1.5"


fork in run := true