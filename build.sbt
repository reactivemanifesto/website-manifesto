lazy val `reactivemanifesto` = (project in file("."))
  .enablePlugins(PlayScala)

name := "reactivemanifesto"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.7"

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

libraryDependencies ++= Seq(
  ws,
  specs2,
  "org.webjars" % "jquery" % "2.1.4",
  "org.webjars" % "knockout" % "2.3.0",
  "org.webjars" % "retinajs" % "0.0.2",
  "org.reactivemongo" %% "play2-reactivemongo" % "0.11.6.play24"
)

routesGenerator := InjectedRoutesGenerator
