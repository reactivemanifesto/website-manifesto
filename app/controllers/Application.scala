package controllers

import java.util.{Locale, Date}

import org.joda.time.DateTimeZone
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import play.api.i18n.Lang
import play.api.mvc.{Call, Action, Controller}
import org.apache.commons.codec.digest.DigestUtils
import play.api.templates.Html

/**
 * Serves the main pages of the application
 */
object Application extends Controller {

  /**
   * A full lang.
   *
   * Some services (eg social services like Facebook) don't support single language codes, you need to have a
   * language and a country.
   *
   * @param lang The lang according to our system
   * @param full The full lang
   */
  case class FullLang(lang: Lang, full: String)

  private val de = FullLang(Lang("de"), "de_DE")
  private val en = FullLang(Lang("en"), "en_US")
  private val es = FullLang(Lang("es"), "es_ES")
  private val fr = FullLang(Lang("fr"), "fr_FR")
  private val ja = FullLang(Lang("ja"), "ja_JP")
  private val ptBR = FullLang(Lang("pt-br"), "pt_BR")

  private val dateFormat: DateTimeFormatter =
    DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'")
      .withLocale(Locale.ENGLISH)
      .withZone(DateTimeZone.UTC)

  /**
   * Takes a list of language to content tuples for a specific page, and returns a function that takes the language and
   * returns the action to serve it.
   *
   * @param reverseRoute The reverse route to the action. This is used to redirect to the English version should a page
   *                     for the given language not be found.
   * @param langContents The language to HTML page contents map.
   */
  def cached(reverseRoute: String => Call, langContents: (FullLang, Html)*): String => Action[_] = {

    val cache = langContents.map {
      case (language, content) =>
        // Hash the content
        val hash = DigestUtils.md5Hex(content.body)
        // Quote the hash, required for etag header formatting
        (language.lang, (content, "\"" + hash + "\""))
    }.toMap

    { language =>
      val lang = Lang(language)

      Action { req =>

        cache.get(lang) match {
          case Some((content, hash)) =>
            val response = req.headers.get(IF_NONE_MATCH).collect {
              case matching if matching.split(",").exists(_.trim == hash) => NotModified
            } getOrElse {
              Ok(content)
            }

            response.withHeaders(
              DATE -> dateFormat.print((new Date).getTime),
              ETAG -> hash,
              CONTENT_LANGUAGE -> lang.code
            )

          case None =>
            // No content for this language was found, redirect to the English version
            Redirect(reverseRoute(en.lang.code))
        }

      }
    }
  }

  val index = {

    def render(lang: FullLang, manifesto: Html) = {
      lang -> views.html.index(manifesto, lang.full)(lang.lang)
    }

    cached(routes.Application.index,
      render(en, views.html.en.manifesto()),
      render(ja, views.html.ja.manifesto()),
      render(fr, views.html.fr.manifesto()),
      render(de, views.html.de.manifesto()),
      render(es, views.html.es.manifesto()),
      render(ptBR, views.html.ptBR.manifesto())
    )
  }

  /**
   * The list page.
   */
  val list = {
    def render(lang: FullLang) = {
      lang -> views.html.list(lang.lang)
    }

    cached(routes.Application.list,
      render(en),
      render(es),
      render(ja),
      render(fr),
      render(de),
      render(ptBR)
    )
  }

  /**
   * The ribbons page.
   */
  val ribbons = {
    def render(lang: FullLang) = {
      lang -> views.html.ribbons(lang.lang)
    }

    cached(routes.Application.ribbons,
      render(en)
    )
  }

  /**
   * The glossary page.
   */
  val glossary = {
    def render(lang: FullLang, glossary: Html) = {
      lang -> views.html.glossary(glossary)(lang.lang)
    }

    cached(routes.Application.glossary,
      render(en, views.html.en.glossary()),
      render(ja, views.html.ja.glossary()),
      render(de, views.html.de.glossary())
    )
  }
}
