package services

import play.api.mvc.{EssentialAction, EssentialFilter}


class ReactiveManifestoFilter extends EssentialFilter {
  override def apply(next: EssentialAction) = EssentialAction { rh =>
    // I need to find out exactly what Heroku is sending
    if (rh.headers.get("Log-Headers").contains("true")) {
      println(s"${rh.method} ${rh.uri} ${rh.version}")
      rh.headers.headers.foreach {
        case (name, value) => println(s"$name: $value")
      }
      println()
    }
    next(rh)
  }
}
