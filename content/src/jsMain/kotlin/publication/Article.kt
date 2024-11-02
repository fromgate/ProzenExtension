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
import org.w3c.dom.HTMLLinkElement
import org.w3c.dom.asList
import kotlin.collections.set


class Article(requester: Requester, data: JsonObject) : PublicationPage(requester, data) {
    /* 🕑 05.09.24 18:11 (05.09.24 18:18) 📃 408 📄 213 🔗 🤖 */
    override fun showStats() {
        val stats = this.stats ?: return


        val infoBlock = document.querySelector(".article__statistics, div[class^='content--article-info-block__articleInfoBlock-']") as? HTMLElement

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
                val shortLink = (document.head!!.querySelector("link[rel=canonical][href]") as HTMLLinkElement).href
                val headers = document.querySelectorAll("h2, h3")
                headers.asList().filterIsInstance<HTMLHeadingElement>().forEach { header ->
                    val anchorId = header.id
                    header.style.position = "relative"
                    val linkIcon = document.create.span("publication-header-icon-url") {
                        title = M.publicationHeaderCopyLink
                        onClickFunction = {
                            copyTextToClipboard("$shortLink#$anchorId")
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