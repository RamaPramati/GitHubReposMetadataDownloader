
name := "github-repos-metadata-downloader"

version := "1.0"

scalaVersion := "2.11.4"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies +=
  "com.typesafe.akka" %% "akka-actor" % "2.3.7"

libraryDependencies +=
  "com.typesafe.akka" %% "akka-remote" % "2.3.7"

libraryDependencies +=
   "org.slf4j" % "slf4j-log4j12" % "1.7.10"

libraryDependencies +=
  "org.json4s" %% "json4s-ast" % "3.2.10"

libraryDependencies +=
  "org.json4s" %% "json4s-jackson" % "3.2.10"

libraryDependencies +=
  "commons-httpclient" % "commons-httpclient" % "3.1"

libraryDependencies +=
  "com.typesafe" % "config" % "1.2.1"

libraryDependencies +=
   "org.apache.commons" % "commons-vfs2" % "2.1"

libraryDependencies += "org.json" % "json" % "20090211"

