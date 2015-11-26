name := "cloud-client"

version := "1.0"

scalaVersion := "2.11.7"
libraryDependencies += "org.eclipse.jetty.aggregate" % "jetty-all-server" % "8.1.18.v20150929"
//libraryDependencies += "org.eclipse.jetty" % "jetty" % "6.1.26"
//libraryDependencies += "org.eclipse.jetty" % "jetty-util" % "6.1.26"
//libraryDependencies += "org.mortbay.jetty" % "servlet-api-2.5" % "6.1.26"
libraryDependencies += "org.scala-lang.modules" % "scala-xml_2.11" % "1.0.5"
libraryDependencies += "com.dropbox.core" % "dropbox-core-sdk" % "2.0-beta-2"
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.0"
libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.1.5"

Revolver.settings