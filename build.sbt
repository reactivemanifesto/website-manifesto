name := "reactivemanifesto"

version := "1.0-SNAPSHOT"

playScalaSettings

libraryDependencies ++= Seq(
  "org.webjars" %% "webjars-play" % "2.2.1-2",
  "org.webjars" % "jquery" % "1.10.2",
  "org.webjars" % "knockout" % "2.3.0",
  "org.webjars" % "retinajs" % "0.0.2",
  "org.reactivemongo" %% "play2-reactivemongo" % "0.10.2"
)

