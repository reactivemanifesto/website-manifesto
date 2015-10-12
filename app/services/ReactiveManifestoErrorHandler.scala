package services

import play.api.routing.Router
import play.api.{UsefulException, Configuration, Environment, Logger}
import play.api.http.DefaultHttpErrorHandler
import play.api.mvc.{Results, RequestHeader}
import play.core.SourceMapper

import scala.concurrent.Future

class ReactiveManifestoErrorHandler(environment: Environment, configuration: Configuration,
                                    sourceMapper: Option[SourceMapper] = None,
                                    router: => Option[Router] = None)
  extends DefaultHttpErrorHandler(environment, configuration, sourceMapper, router) {

  override def onProdServerError(request: RequestHeader, ex: UsefulException) = {
    Logger.error("An error occurred", ex)
    Future.successful(Results.InternalServerError("Sorry, something went wrong, and I don't know how to react to it."))
  }

  override def onNotFound(request: RequestHeader, message: String) = {
    // If it's for a HEAD request, return 404
    if (request.method == "HEAD") {
      Future.successful(Results.NotFound)
    } else {
      // Otherwise redirect to the index page.
      Future.successful(Results.Redirect("/"))
    }
  }

}
