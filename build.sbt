name := "zoocker"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.0.13",
  "com.typesafe.akka" %% "akka-actor" % "2.2.3",
  "com.typesafe.akka" %% "akka-slf4j" % "2.2.3",
  "io.spray" % "spray-client" % "1.2.0",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.3.0",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.3.0",
  "com.lambdaworks" %% "jacks" % "2.2.3",
  "org.apache.curator" % "curator-recipes" % "2.3.0" exclude("org.slf4j", "slf4j-log4j12") exclude("org.jboss.netty", "netty") exclude("log4j", "log4j")
)     

scalariformSettings

net.virtualvoid.sbt.graph.Plugin.graphSettings

packageArchetype.java_application

seq(Revolver.settings: _*)

atmosSettings

com.github.retronym.SbtOneJar.oneJarSettings
