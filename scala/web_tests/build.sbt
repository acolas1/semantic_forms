import sbt.Keys._
import sbt._
import Common._

name := "web_tests"

lazy val web_tests = (project in file("."))
	.enablePlugins(GatlingPlugin)

// 
scalaVersion := "2.11.12"
// crossScalaVersions := Seq("2.11.12", "2.12.6")

// fork a new JVM for 'test:run', but not 'run'
// fork in Test := true
// fork := true
// add a JVM option to use when forking a JVM for 'run'
// javaOptions += "-Xmx50M"

// Gatling is an open-source load testing framework based on Scala, Akka and Netty
libraryDependencies += "io.gatling.highcharts" % "gatling-charts-highcharts" % "2.3.0" % Test
libraryDependencies += "io.gatling" % "gatling-test-framework" % "2.3.0" % Test
libraryDependencies += "com.github.agourlay" %% "cornichon" % // "0.12.7" % Test
								"0.13.2" % Test

// resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
resolvers += Resolver.file("Local repo", file(System.getProperty("user.home") + "/.ivy2/local"))(Resolver.ivyStylePatterns)

// temporary for cornichon SNAPSHOTs
resolvers += "cornichon-sonatype" at "https://oss.sonatype.org/content/repositories/snapshots"

publishArtifact in (Compile, packageDoc) := false
sources in (Compile,doc) := Seq.empty

