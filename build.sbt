ThisBuild / scalaVersion := scala213
ThisBuild / organization := "com.47deg"

addCommandAlias("ci-test", "scalafmtCheckAll; scalafmtSbtCheck; mdoc; ++test")
addCommandAlias("ci-docs", "github; mdoc; headerCreateAll; publishMicrosite")
addCommandAlias("ci-publish", "github; ci-release")

lazy val scala212         = "2.12.15"
lazy val scala213         = "2.13.8"
lazy val scala3Version    = "3.0.2"
lazy val scala2Versions   = Seq(scala212, scala213)
lazy val allScalaVersions = scala2Versions :+ scala3Version

publish / skip := true

lazy val root = (project in file("."))
  .settings(
    publish / skip := true
  )
  .aggregate(fetchlessJVM, fetchlessJS, fetchlessDebugJVM, fetchlessDebugJS)

lazy val fetchless = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(commonCrossDependencies)
  .settings(crossScalaVersions := allScalaVersions)

lazy val fetchlessJVM = fetchless.jvm
lazy val fetchlessJS = fetchless.js
  .settings(crossScalaVersions := scala2Versions)

lazy val fetchlessDebug = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(crossScalaVersions := allScalaVersions)
  .dependsOn(fetchless % "compile->compile;test->test")

lazy val fetchlessDebugJVM = fetchlessDebug.jvm
lazy val fetchlessDebugJS = fetchlessDebug.js
  .settings(crossScalaVersions := scala2Versions)
