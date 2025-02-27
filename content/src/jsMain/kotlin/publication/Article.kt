package publication

import common.*
import kotlinx.browser.document
import kotlinx.dom.clear
import kotlinx.html.dom.append
import kotlinx.html.dom.create
import kotlinx.html.js.div
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.span
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.serialization.json.JsonObject
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLHeadingElement
import org.w3c.dom.asList
import requester.Requester
import kotlin.collections.set


class Article(requester: Requester, data: JsonObject) : PublicationPage(requester, data) {


    override fun showStats() {
        val stats = this.stats ?: return
        val infoBlock = document.querySelector(".article__statistics, div[class^='content--article-info-block__articleInfoBlock-'], div[class^=content--article-render__info-]") as? HTMLElement
        infoBlock?.clear()
        infoBlock?.append {
            div("prozen-article-stats") {
                div("prozen-article-stats-item") {
                    title = M.publicationTime
                    attributes["itemprop"] = "datePublished"
                    +stats.showTime()
                }
                if (stats.views != stats.viewsTillEnd) {
                    div("prozen-article-stats-item") {
                        title = M.publicationViews
                        span(Icons.VIEWS.cssClass)
                        +(stats.views?.format() ?: "")
                    }
                }
                div("prozen-article-stats-item") {
                    title = M.publicationFullViews
                    span(Icons.FULL_VIEWS.cssClass)
                    +(stats.viewsTillEnd?.format() ?: "")
                }
                stats.timeToRead?.let {
                    div("prozen-article-stats-item") {
                        title = M.publicationTimeToRead + "\n${it.secToTimeString()}"
                        span("publication_icon_read_time")
                        + it.secToHHMMSS()
                    }
                }
                div("prozen-article-stats-item") {
                    title = M.publicationCopyLink
                    style = "cursor: pointer;"
                    onClickFunction = {
                        copyTextToClipboard(stats.shortLink)
                        showNotification(M.notificationLinkCopied)
                    }
                    span(Icons.LINK.cssClass)
                }
                if (stats.notIndexed) {
                    div("prozen-article-stats-item") {
                        title = M.publicationNotIndexed
                        span(Icons.SAD_ROBOT.cssClass)
                    }
                }
            }
        }
        addSubtitleLinks()
    }

    private fun addSubtitleLinks() {
        Option.SUBTITLE_LINKS.value().then { option ->
            if (option) {
                val headers = document.querySelectorAll("h2, h3")
                headers.asList().filterIsInstance<HTMLHeadingElement>()
                    .filter { !it.className.contains("content--banner-desktop__title") }
                    .forEach { header ->
                    val anchorId = header.id
                    header.style.position = "relative"
                    val linkIcon = document.create.span("publication-header-icon-url") {
                        title = M.publicationHeaderCopyLink
                        onClickFunction = {
                            copyTextToClipboard("$canonicalUrl#$anchorId")
                            showNotification(M.notificationLinkCopied)
                        }
                    }
                    if (header.hasChildNodes()) {
                        header.insertBefore(linkIcon, header.firstChild)
                    } else {
                        val text = header.innerText
                        header.innerText = ""
                        header.appendChild(linkIcon)
                        header.appendChild(document.create.span {
                            +text
                        })
                    }
                }
            }
        }
    }
}