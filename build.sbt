name := "cloud-client"

version := "1.0"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

scalaVersion := "2.11.7"
libraryDependencies += "org.scala-lang.modules" % "scala-xml_2.11" % "1.0.5"
libraryDependencies += "com.dropbox.core" % "dropbox-core-sdk" % "2.0-beta-2"
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.0"
libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.1.5"

libraryDependencies ++= {
  val akkaV = "2.4.0"
  val sprayV = "1.3.3"
  Seq(
    "io.spray"            %%  "spray-can"     % sprayV,
    "io.spray"            %%  "spray-http"    % sprayV,
    "io.spray"            %%  "spray-httpx"   % sprayV,
    "io.spray"            %%  "spray-routing" % sprayV,
    "io.spray"            %%  "spray-testkit" % sprayV  % "test",
    "com.typesafe.akka"   %%  "akka-actor"    % akkaV,
    "com.typesafe.akka"   %%  "akka-testkit"  % akkaV   % "test",
    "org.specs2"          %%  "specs2-core"   % "3.6.6" % "test"
  )
}


fork in run := true