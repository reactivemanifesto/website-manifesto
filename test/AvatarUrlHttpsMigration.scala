import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import models.DbSignatory
import play.api.inject.DefaultApplicationLifecycle
import play.api.{Configuration, Environment}
import play.modules.reactivemongo.DefaultReactiveMongoApi
import services.UserService

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object AvatarUrlHttpsMigration extends App {

  val environment = Environment.simple()
  val configuration = Configuration.load(environment)
  val applicationLifecycle = new DefaultApplicationLifecycle
  val system = ActorSystem()
  implicit val mat = ActorMaterializer.create(system)
  import system.dispatcher

  val reactiveMongoApi = new DefaultReactiveMongoApi(configuration, applicationLifecycle)
  val userService = new UserService(reactiveMongoApi)

  try {

    val time = System.currentTimeMillis()
    println("Loading signatories...")
    val updated = Await.result(userService.loadSignatories().flatMap { sigs =>
      println(s"Loaded ${sigs.size} signatories in ${System.currentTimeMillis() - time}ms")
      val toUpdate = sigs.collect {
        case sig if sig.avatarUrl.exists(_.startsWith("http:")) => sig
      }
      println(s"Found ${toUpdate.size} signatories to update the avatar URLs for")

      val startTime = System.currentTimeMillis()
      val total = toUpdate.size

      Source(toUpdate)
        .mapAsyncUnordered(20)(updateSignatoryUrl)
        .fold(0) { (count, _) =>
          val done = count + 1
          val remaining = total - done
          val percent = (done.asInstanceOf[Double] / total) * 100
          val estimatedTimeRemaining = done match {
            case 0 => 0
            case nonZero =>
              val rate = (System.currentTimeMillis() - startTime) / done
              (rate * remaining) / 1000
          }
          System.out.print(s"\r$done signatories updated ${percent.round}% ${estimatedTimeRemaining}s remaining")
          done
        }.runWith(Sink.head)

    }, Duration.Inf)
    println(s"Updated ${updated} signatories in ${System.currentTimeMillis() - time}ms")
  } finally {
    system.terminate()
    applicationLifecycle.stop()
  }

  private def updateSignatoryUrl(signatory: DbSignatory): Future[Unit] = {
    userService.updateProfile(signatory.copy(avatarUrl = signatory.avatarUrl.map(_.replaceFirst("http:", "https:"))))
  }

}
