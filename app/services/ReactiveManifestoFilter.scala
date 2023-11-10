package services

import play.api.libs.streams.Accumulator
import play.api.mvc.{EssentialAction, EssentialFilter, Results}

class ReactiveManifestoFilter extends EssentialFilter {
  override def apply(next: EssentialAction): EssentialAction = EssentialAction { rh =>
    val isHealthCheck = rh.path == "/health"
    val requestHttps: Boolean = rh.headers.get("X-Forwarded-Proto").exists(_.equalsIgnoreCase("https"))
    if (isHealthCheck || requestHttps || rh.secure) {
      next(rh)
    } else if (rh.host == "reactivemanifesto.org") {
      Accumulator.done(Results.MovedPermanently(s"https://www.reactivemanifesto.org${rh.uri}"))
    } else {
      Accumulator.done(Results.MovedPermanently(s"https://${rh.host}${rh.uri}"))
    }
  }
}
