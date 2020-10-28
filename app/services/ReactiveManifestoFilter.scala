package services

import play.api.libs.streams.Accumulator
import play.api.mvc.{EssentialAction, EssentialFilter, Results}

class ReactiveManifestoFilter extends EssentialFilter {
  override def apply(next: EssentialAction): EssentialAction = EssentialAction { rh =>
    if (!rh.secure) {
      Accumulator.done(Results.MovedPermanently(s"https://${rh.host}${rh.uri}"))
    } else if (rh.host == "reactivemanifesto.org") {
      Accumulator.done(Results.MovedPermanently(s"https://www.reactivemanifesto.org${rh.uri}"))
    } else {
      next(rh)
    }
  }
}
