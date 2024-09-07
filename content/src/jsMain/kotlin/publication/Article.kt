package publication

import kotlinx.serialization.json.JsonObject
import common.Requester
import common.copyTextToClipboard
import common.removeChildren
import kotlinx.browser.document
import kotlinx.html.dom.create
import kotlinx.html.js.div
import kotlinx.html.js.onClickFunction
import kotlinx.html.style
import kotlinx.html.title
import org.w3c.dom.HTMLDivElement


class Article(requester: Requester, data: JsonObject) : PublicationPage(requester, data) {
    /* 🕑 05.09.24 18:11 (05.09.24 18:18) 📃 408 📄 213 🔗 🤖 */
    override fun showStats() {
        val stats = this.stats ?: return
        val infoBlock =
            document.querySelector("div[class^=content--article-info-block__articleInfoBlock-]") as? HTMLDivElement
        infoBlock?.removeChildren()
        val dateBlock = document.create.div("article-info-block__addTimeInfo-25") {
            +"🕑 ${stats!!.showTime()}"
            title = "Время создания (редактирования)"
            attributes["itemprop"] = "datePublished"
        }
        infoBlock?.appendChild(dateBlock)

        if (stats.views != stats.viewsTillEnd) {
            val viewsBlock = document.create.div("article-info-block__viewsInfo-1g") {
                +"📃 ${stats.views}"
                title = "Просмотры"
            }
            infoBlock?.appendChild(viewsBlock)
        }

        val viewsTillEndBlock = document.create.div("article-info-block__viewsInfo-1g") {
            +"📄 ${stats.viewsTillEnd}"
            title = "Дочитывания"
        }
        infoBlock?.appendChild(viewsTillEndBlock)

        val shortLinkBlock = document.create.div("article-info-block__viewsInfo-1g") {
            +"🔗"
            title = SHORT_LINK_TITLE
            onClickFunction = {
                copyTextToClipboard (stats!!.shortLink)
            }
            style = "cursor: pointer;"
        }
        infoBlock?.appendChild(shortLinkBlock)

        if (stats.notIndexed) {
            val sadRobotBlock = document.create.div("article-info-block__viewsInfo-1g") {
                +"🤖"
                title = NO_INDEX_TITLE
            }
            infoBlock?.appendChild(sadRobotBlock)
        }

    }
}