package controllers

import java.util.{Date, Locale}

import org.joda.time.DateTimeZone
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import play.api.i18n.Lang
import play.api.mvc._
import org.apache.commons.codec.digest.DigestUtils
import play.twirl.api.Html

/**
 * Serves the main pages of the application
 */
class Application(components: ControllerComponents, implicit private val assetsFinder: AssetsFinder) extends AbstractController(components) {

  /**
   * A full lang.
   *
   * Some services (eg social services like Facebook) don't support single language codes, you need to have a
   * language and a country.
   *
   * @param lang The lang according to our system
   * @param full The full lang
   */
  case class FullLang(lang: Lang, full: String, dir: String)

  private val de = FullLang(Lang("de"), "de_DE", "ltr")
  private val en = FullLang(Lang("en"), "en_US", "ltr")
  private val es = FullLang(Lang("es"), "es_ES", "ltr")
  private val fr = FullLang(Lang("fr"), "fr_FR", "ltr")
  private val it = FullLang(Lang("it"), "it_IT", "ltr")
  private val ja = FullLang(Lang("ja"), "ja_JP", "ltr")
  private val pt = FullLang(Lang("pt"), "pt_PT", "ltr")
  private val ptBR = FullLang(Lang("pt-BR"), "pt_BR", "ltr")
  private val tr = FullLang(Lang("tr"), "tr_TR", "ltr")
  private val id = FullLang(Lang("id"), "id_ID", "ltr")
  private val zhCN = FullLang(Lang("zh-CN"), "zh_CN", "ltr")
  private val ko = FullLang(Lang("ko"), "ko_KR", "ltr")
  private val fa = FullLang(Lang("fa"), "fa_FA", "rtl")
  private val sw = FullLang(Lang("sw"), "sw_SW", "ltr")
  private val cz = FullLang(Lang("cz"), "cz_CZ", "ltr")
  private val ru = FullLang(Lang("ru"), "ru_RU", "ltr")

  private val all = Seq(de, en, es, fr, it, ja, pt, ptBR, tr, id, zhCN, ko, fa, sw, cz, ru)

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
  def cached(reverseRoute: String => Call, langContents: (FullLang, Html)*): String => EssentialAction = {

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
      implicit val impLang = lang.lang
      lang -> views.html.index(manifesto, lang.full, lang.dir)
    }

    cached(routes.Application.index,
      render(en, views.html.en.manifesto()),
      render(ja, views.html.ja.manifesto()),
      render(fr, views.html.fr.manifesto()),
      render(it, views.html.it.manifesto()),
      render(de, views.html.de.manifesto()),
      render(es, views.html.es.manifesto()),
      render(pt, views.html.pt.manifesto()),
      render(ptBR, views.html.ptBR.manifesto()),
      render(tr, views.html.tr.manifesto()),
      render(id, views.html.id.manifesto()),
      render(zhCN, views.html.zhCN.manifesto()),
      render(ko, views.html.ko.manifesto()),
      render(fa, views.html.fa.manifesto()),
      render(sw, views.html.sw.manifesto()),
      render(cz, views.html.cz.manifesto()),
      render(ru, views.html.ru.manifesto())
    )
  }

  /**
   * The list page.
   */
  val list = {
    cached(routes.Application.list,
      all.map { lang =>
        implicit val impLang = lang.lang
        lang -> views.html.list.apply
      }: _*
    )
  }

  /**
   * The ribbons page.
   */
  val ribbons = {
    def render(lang: FullLang) = {
      implicit val impLang = lang.lang
      lang -> views.html.ribbons.apply
    }

    cached(routes.Application.ribbons,
      render(en)
    )
  }

  val privacy = {
    def render(lang: FullLang) = {
      implicit val impLang = lang.lang
      lang -> views.html.privacy.apply
    }

    cached(routes.Application.privacy,
      render(en)
    )
  }

  val cookie = {
    def render(lang: FullLang) = {
      implicit val impLang = lang.lang
      lang -> views.html.cookie.apply
    }

    cached(routes.Application.cookie,
      render(en)
    )
  }

  /**
   * The glossary page.
   */
  val glossary = {
    def render(lang: FullLang, glossary: Html) = {
      implicit val impLang = lang.lang
      lang -> views.html.glossary(glossary, lang.dir)
    }

    cached(routes.Application.glossary,
      render(en, views.html.en.glossary()),
      render(fr, views.html.fr.glossary()),
      render(ja, views.html.ja.glossary()),
      render(it, views.html.it.glossary()),
      render(de, views.html.de.glossary()),
      render(zhCN, views.html.zhCN.glossary()),
      render(ko, views.html.ko.glossary()),
      render(cz, views.html.cz.glossary()),
      render(ru, views.html.ru.glossary())
    )
  }

  def health = Action { Ok("All set!") }
}
