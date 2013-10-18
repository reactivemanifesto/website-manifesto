package controllers

import play.api.mvc.{Action, Controller}
import org.apache.commons.codec.digest.DigestUtils
import play.api.templates.Html

/**
 * Serves the main pages of the application
 */
object Application extends Controller {

  // Serves the content with an E-Tag, and checks if the E-Tag matches
  def cached(html: Html) = {
    // Hash the content
    val hash = DigestUtils.md5Hex(html.body)
    Action { req =>
      req.headers.get(IF_NONE_MATCH).collect {
        case `hash` => NotModified
      } getOrElse {
        Ok(html).withHeaders(ETAG -> hash)
      }
    }
  }

  /**
   * The index page. Because it's a val, the index only gets rendered once.
   */
  val index = cached(views.html.index())

  /**
   * The list page.
   */
  val list = cached(views.html.list())

  /**
   * The ribbons page.
   */
  val ribbons = cached(views.html.ribbons())
}
