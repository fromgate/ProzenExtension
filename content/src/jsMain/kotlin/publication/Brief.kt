package publication

import common.Requester
import common.copyTextToClipboard
import common.format
import common.removeChildren
import kotlinx.browser.document
import kotlinx.html.dom.append
import kotlinx.html.dom.create
import kotlinx.html.js.div
import kotlinx.html.js.onClickFunction
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.serialization.json.JsonObject
import org.w3c.dom.HTMLDivElement

class Brief(requester: Requester, data: JsonObject) : PublicationPage(requester, data) {
    override fun showStats() {
        val stats = this.stats ?: return
        val infoBlock =
            document.querySelector("div.article-stats-view") as? HTMLDivElement
        infoBlock?.removeChildren()
        infoBlock?.append {
            div("article-stats-view__item") {
                title = "Время создания (редактирования)"
                attributes["itemprop"] = "datePublished"
                +"🕑 ${stats.showTime()}"
            }
            if (stats.views != stats.viewsTillEnd) {
                div("article-stats-view__item") {
                    title = "Просмотры"
                    +"📃 ${stats.views?.format()}"
                }
            }
            div("article-stats-view__item") {
                +"📄 ${stats.viewsTillEnd?.format()}"
                title = "Дочитывания"
            }
            div("article-stats-view__item") {
                +"🔗"
                title = SHORT_LINK_TITLE
                onClickFunction = {
                    copyTextToClipboard(stats.shortLink)
                }
                style = "cursor: pointer;"
            }
            if (stats.notIndexed) {
                div("article-stats-view__item") {
                    +"🤖"
                    title = NO_INDEX_TITLE
                }

            }
        }
    }
}