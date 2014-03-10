import actors.SignatoriesCache
import akka.actor.Props
import controllers.HeadAction
import play.api.libs.concurrent.Akka
import play.api.mvc._
import play.api.{Logger, Application, GlobalSettings}
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    val actorSystem = Akka.system(app)
    // Create the signatories actor.
    val sigsActor = actorSystem.actorOf(Props[SignatoriesCache], "signatories")
    // There's a race condition, when MongoDB connects, it then authenticates, but we might be sending messages before
    // that.  So wait 2 seconds for authentication to happen.
    actorSystem.scheduler.schedule(2 seconds, 10 minutes, sigsActor, SignatoriesCache.Reload)
  }

  override def onRouteRequest(req: RequestHeader) = {
    // Implement transparent HEAD handling.  If no handler is found, and the it's a HEAD requests, looks up a handler
    // for an equivalent GET request, and if found, wraps that in a HeadAction that blocks the response body but
    // sends all the same response headers.
    super.onRouteRequest(req) match {
      case None if req.method == "HEAD" => {
        super.onRouteRequest(req.copy(method = "GET")) match {
          case Some(wrapped: EssentialAction) => Some(new HeadAction(wrapped))
          case other => other
        }
      }
      case other => other
    }
  }

  override def onError(request: RequestHeader, ex: Throwable) = {
    Logger.error("An error occurred", ex)
    Future.successful(Results.InternalServerError("Sorry, something went wrong, and I don't know how to react to it."))
  }

  override def onHandlerNotFound(request: RequestHeader) = {
    // Redirect all unknown URLs to the index page.
    Future.successful(Results.Redirect("/"))
  }
}
