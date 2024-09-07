package publication

import common.Requester
import common.copyTextToClipboard
import common.format
import common.removeChildren
import kotlinx.browser.document
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

        val dateBlock = document.create.div("article-stats-view__item") {
            +"🕑 ${stats.showTime()}"
            title = "Время создания (редактирования)"
            attributes["itemprop"] = "datePublished"
        }

        infoBlock?.appendChild(dateBlock)
        if (stats.views != stats.viewsTillEnd) {
            val viewsBlock = document.create.div("article-stats-view__item") {
                +"📃 ${stats.views?.format()}"
                title = "Просмотры"
            }
            infoBlock?.appendChild(viewsBlock)
        }

        val viewsTillEndBlock = document.create.div("article-stats-view__item") {
            +"📄 ${stats.viewsTillEnd?.format()}"
            title = "Дочитывания"
        }
        infoBlock?.appendChild(viewsTillEndBlock)

        val shortLinkBlock = document.create.div("article-stats-view__item") {
            +"🔗"
            title = SHORT_LINK_TITLE
            onClickFunction = {
                copyTextToClipboard(stats.shortLink)
            }
            style = "cursor: pointer;"
        }
        infoBlock?.appendChild(shortLinkBlock)

        if (stats.notIndexed) {
            val sadRobotBlock = document.create.div("article-stats-view__item") {
                +"🤖"
                title = NO_INDEX_TITLE
            }
            infoBlock?.appendChild(sadRobotBlock)
        }

    }
}