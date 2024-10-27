package publication

import common.*
import kotlinx.browser.document
import kotlinx.html.dom.append
import kotlinx.html.dom.create
import kotlinx.html.js.div
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.span
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.serialization.json.JsonObject
import org.w3c.dom.HTMLDivElement

class Brief(requester: Requester, data: JsonObject) : PublicationPage(requester, data) {
    override fun showStats() {
        val stats = this.stats ?: return
        val infoBlock =
            document.querySelector("div.desktop-brief-page__stats") as? HTMLDivElement
        infoBlock?.removeChildren()
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
                    span(Icons.LINK.cssClass)
                    title = M.publicationCopyLink
                    onClickFunction = {
                        copyTextToClipboard(stats.shortLink)
                        showNotification(M.notificationLinkCopied)
                    }
                    style = "cursor: pointer;"
                }
                if (stats.notIndexed) {
                    div("prozen-article-stats-item") {
                        title = M.publicationNotIndexed
                        span(Icons.SAD_ROBOT.cssClass)
                    }
                }
            }
        }
    }
}