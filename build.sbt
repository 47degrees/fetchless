ThisBuild / scalaVersion := scala213
ThisBuild / organization := "com.47deg"

addCommandAlias("ci-test", "scalafmtCheckAll; scalafmtSbtCheck; mdoc; ++test")
addCommandAlias("ci-docs", "github; documentation/mdoc; headerCreateAll")
addCommandAlias("ci-publish", "github; ci-release")

lazy val scala212             = "2.12.20"
lazy val scala213             = "2.13.16"
lazy val scala3Version        = "3.6.4"
lazy val scala2Versions       = Seq(scala212, scala213)
lazy val allScalaVersions     = scala2Versions :+ scala3Version
lazy val scalaVersions213Plus = Seq(scala213, scala3Version)

publish / skip := true

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

lazy val fetchlessStreaming = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(fs2Dependencies)
  .settings(crossScalaVersions := allScalaVersions)
  .dependsOn(fetchless % "compile->compile;test->test")

lazy val fetchlessStreamingJVM = fetchlessStreaming.jvm
lazy val fetchlessStreamingJS = fetchlessStreaming.js
  .settings(crossScalaVersions := scala2Versions)

lazy val fetchlessHttp4s = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(http4sDependencies)
  .settings(crossScalaVersions := scalaVersions213Plus)
  .dependsOn(fetchlessStreaming % "compile->compile;test->test")

lazy val fetchlessHttp4sJVM = fetchlessHttp4s.jvm
lazy val fetchlessHttp4sJS = fetchlessHttp4s.js
  .settings(crossScalaVersions := Seq(scala213))

lazy val fetchlessHttp4s023 = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(http4s023Dependencies)
  .settings(crossScalaVersions := allScalaVersions)
  .dependsOn(fetchlessStreaming % "compile->compile;test->test")

lazy val fetchlessHttp4s023JVM = fetchlessHttp4s023.jvm
lazy val fetchlessHttp4s023JS = fetchlessHttp4s023.js
  .settings(crossScalaVersions := scala2Versions)

lazy val fetchlessDoobie = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(doobieDependencies)
  .settings(crossScalaVersions := allScalaVersions)
  .dependsOn(fetchlessStreaming % "compile->compile;test->test")

lazy val fetchlessDoobieJVM = fetchlessDoobie.jvm

lazy val documentation = project
  .enablePlugins(MdocPlugin)
  .settings(mdocOut := file("."))
  .settings(publish / skip := true)
