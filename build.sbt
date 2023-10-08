import com.typesafe.sbt.packager.docker.DockerPermissionStrategy
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.dockerPermissionStrategy
import com.typesafe.sbt.packager.docker._

lazy val `reactivemanifesto` = (project in file("."))
  .enablePlugins(PlayScala, LauncherJarPlugin)

name := "reactivemanifesto-website"
version := "1.0-SNAPSHOT"
scalaVersion := "2.12.12"

fork in Test := false

libraryDependencies ++= Seq(
  ws,
  specs2,
  "org.webjars" % "jquery" % "2.1.4",
  "org.webjars" % "knockout" % "2.3.0",
  "org.webjars" % "retinajs" % "0.0.2",
  "org.reactivemongo" %% "play2-reactivemongo" % "1.1.0-play28-RC11",
//  "org.reactivemongo" %% "reactivemongo-bson-macros" % "1.0.0",
  "com.softwaremill.macwire" %% "macros" % "2.3.1" % Provided,
)

pipelineStages := Seq(gzip, digest)
excludeFilter in digest := "*.map" || "*.gz"

dockerBaseImage := "adoptopenjdk/openjdk11"

javaOptions in Universal ++= Seq(
  "-Dpidfile.path=/dev/null"
)

packageName in Docker := name.value
version in Docker := "latest"
dockerPermissionStrategy := DockerPermissionStrategy.Run
dockerRepository := sys.env.get("DOCKER_REPOSITORY").orElse(Some("registry.pro-us-east-1.openshift.com/staging-reactivemanifesto-website"))
dockerCommands ++= Seq(
  Cmd("USER", "root"),

  ExecCmd("RUN", "apt-get", "update"),
  ExecCmd("RUN", "apt-get", "install", "-y", "gnupg"),
  ExecCmd("RUN", "apt-get", "install", "-y", "wget"),
  ExecCmd("RUN", "wget", "-o-", "https://downloads.mongodb.com/compass/mongodb-mongosh_1.10.6_amd64.deb"),
  ExecCmd("RUN", "dpkg", "-i", "mongodb-mongosh_1.10.6_amd64.deb")
//ExecCmd("RUN", "wget", "-o-", "https://www.mongodb.org/static/pgp/server-7.0.asc"),
//ExecCmd("RUN", "apt-key", "add", "server-7.0.asc"),
//ExecCmd("RUN", "echo", "'deb [ arch=amd64,arm64 ] https://repo.mongodb.org/apt/ubuntu focal/mongodb-org/7.0 multiverse'", "|", "tee", "/etc/apt/sources.list.d/mongodb-org-7.0.list"),
//ExecCmd("RUN", "apt-get", "update"),
//ExecCmd("RUN", "apt-get", "install", "-y", "mongocli")
)
