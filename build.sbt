lazy val `reactivemanifesto` = (project in file("."))
  .enablePlugins(PlayScala, LauncherJarPlugin)

name := "reactivemanifesto"
version := "1.0-SNAPSHOT"
scalaVersion := "2.12.8"

fork in Test := false

libraryDependencies ++= Seq(
  ws,
  specs2,
  "org.webjars" % "jquery" % "2.1.4",
  "org.webjars" % "knockout" % "2.3.0",
  "org.webjars" % "retinajs" % "0.0.2",
  "org.reactivemongo" %% "play2-reactivemongo" % "0.16.0-play26",
  "com.softwaremill.macwire" %% "macros" % "2.3.1" % Provided
)

pipelineStages := Seq(gzip, digest)
excludeFilter in digest := "*.map" || "*.gz"
