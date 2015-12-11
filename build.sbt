name := "cloud-client"

version := "1.0"

scalacOptions := Seq(
  "-unchecked",
  "-deprecation",
  "-encoding",
  "utf8",
  "-feature",
  "-language:implicitConversions",
  "-language:higherKinds",
  "-language:existentials",
  "-language:postfixOps",
  "-optimize"
)

scalaVersion := "2.11.7"
libraryDependencies += "org.scala-lang.modules" % "scala-xml_2.11" % "1.0.5"
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.0"
libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.1.5"

exportJars := true

libraryDependencies ++= {
  val akkaV = "2.4.0"
  val sprayV = "1.3.2"
  Seq(
    "io.spray"            %%  "spray-can"     % sprayV,
    "io.spray"            %%  "spray-http"    % sprayV,
    "io.spray"            %%  "spray-httpx"   % sprayV,
    "io.spray"            %%  "spray-routing" % sprayV,
    "io.spray"            %%  "spray-testkit" % sprayV  % "test",
    "io.spray"            %%  "spray-json"    % sprayV,
    "io.spray"            %%  "spray-client"  % sprayV,
    "com.typesafe.akka"   %%  "akka-actor"    % akkaV,
    "com.typesafe.akka"   %%  "akka-testkit"  % akkaV   % "test",
    "org.specs2"          %%  "specs2-core"   % "3.6.6" % "test",
    "org.specs2"          %%  "specs2-junit"  % "3.6.6"
  )
}

import com.github.retronym.SbtOneJar._

oneJarSettings

scalacOptions in Test ++= Seq("-Yrangepos")

fork in run := true

