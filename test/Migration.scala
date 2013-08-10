import java.util.Date
import models._
import org.joda.time.DateTime
import org.specs2.mutable.Specification
import play.api.libs.json.{Json, Reads, JsArray}
import play.api.libs.ws.WS
import play.api.test._
import play.api.test.Helpers._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConverters._
import services.UserService

object MigrationSpec extends Specification {

  "the migration" should {
    "migrate everything" in running(FakeApplication()) {

      // First, fetch all signatories
      def fetchSignatories(skip: Int): Future[Seq[UserService.OAuthUser]] = {
        for {
          response <- WS.url("http://warry.hullapp.io/api/v1/51d44fdede3b4ea48900086e/activity?limit=200&where%5Bverb%5D=like&skip="
            + skip).get()
          rest <- {
            val results = try {
              response.json.as[JsArray].value
            } catch {
              case NonFatal(e) => {
                println(response.getAHCResponse.getHeaders.asInstanceOf[java.util.Map[String, java.util.List[String]]].asScala.toSeq.map { h =>
                  h._1 + ": " + h._2.asScala.mkString(", ")
                }.mkString("\n"))
                println(response.toString)
                throw e
              }
            }
            println("Fetched " + results.size + " signatories")
            if (results.isEmpty) {
              Future.successful(Nil)
            } else {
              fetchSignatories(skip + 200)
            }
          }
        } yield {
          response.json.as[JsArray].value.map { js =>
            try {
              val name = (js \ "actor" \ "name").as[String]
              val avatar = (js \ "actor" \ "picture").asOpt[String]
              val pjs = (js \ "actor" \ "identities").as[JsArray].value.head
              val providerId = (pjs \ "provider").as[String]
              val provider = providerId match {
                case "google" => Google((pjs \ "uid").as[String])
                case "twitter" => Twitter((pjs \ "uid").as[String].toLong, (pjs \ "login").as[String])
                case "linkedin" => LinkedIn((pjs \ "uid").as[String])
                case "github" => GitHub((pjs \ "uid").as[String].toLong, (pjs \ "login").as[String])
              }
              val signed = new DateTime((js \ "published").as[Date](Reads.IsoDateReads))
              UserService.OAuthUser(provider, name, avatar, Some(signed))
            } catch {
              case NonFatal(e) => {
                println("Got error: " + e.getMessage)
                println(Json.prettyPrint(js))
                throw e
              }
            }
          } ++ rest
        }
      }

      val sigs = Await.result(fetchSignatories(0), Duration.Inf)

      println("Fetched " + sigs.size + " signatories in total")

      var count = 0
      sigs.grouped(10).foreach { sigs =>
        count = count + 1
        println("Saving batch " + count + "")
        Await.result(Future.sequence(sigs.filterNot(_.name == "Maxime Dantec").map(UserService.findOrSaveUser)), Duration.Inf)
      }
    }
  }

}