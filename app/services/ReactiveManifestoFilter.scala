package services

import play.api.Logger
import play.api.http.HeaderNames
import play.api.libs.streams.Accumulator
import play.api.mvc.{EssentialAction, EssentialFilter, Results}

class ReactiveManifestoFilter extends EssentialFilter {
  val log = Logger("moz-debug-log")
  override def apply(next: EssentialAction) = EssentialAction { rh =>
    // 2019-05-16 James Roper added this to help try and debug why Moz isn't indexing our site
    if (rh.path == "/robots.txt") {
      log.info(s"${rh.method} '${rh.uri}' made from '${rh.remoteAddress}', User-Agent: '${rh.headers.get(HeaderNames.USER_AGENT).getOrElse("")}'")
    }
    if (!rh.secure) {
      Accumulator.done(Results.MovedPermanently(s"https://${rh.host}${rh.uri}"))
    } else {
      next(rh)
    }
  }
}
