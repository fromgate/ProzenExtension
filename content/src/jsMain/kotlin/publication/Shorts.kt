package publication

import common.Requester
import common.checkNoIndex
import common.format
import kotlinx.browser.document
import kotlinx.datetime.Instant
import kotlinx.html.dom.append
import kotlinx.html.dom.create
import kotlinx.html.js.span
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.serialization.json.JsonObject
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLMetaElement

class Shorts(requester: Requester, data: JsonObject) : PublicationPage(requester, data) {

    override suspend fun getStats() {
        val views = (document.querySelector("meta[property=\"ya:ovs:views_total\"]") as HTMLMetaElement).content.toInt()
        val uploadDateStr = (document.querySelector("meta[property=\"ya:ovs:upload_date\"]") as HTMLMetaElement).content
        val uploadDate = Instant.parse(uploadDateStr)
        val noindex = checkNoIndex()
        val url = "https://dzen.ru/shorts/$publicationId"
        // val loadStats = requester.getPublicationStat(publicationId)
        stats = PublicationStats(
            uploadDate,
            null,
            views,
            null,
            noindex,
            url
        )
    }

    override fun showStats() {
        val stats = this.stats ?: return
        val infoBlock = document.querySelector("div.video-site--short-meta__wrapper-1u") as? HTMLDivElement

        infoBlock?.append {
            span {
                title = "Время создания (редактирования)"
                attributes["itemprop"] = "datePublished"
                style = "display: inline-block !important; margin-left: 5px !important; pointer-events:auto; font-size: 12px;"
                +"🕑 ${stats.showTime()}"
            }
            span {
                title = "Просмотры"
                style = "display: inline-block !important; margin-left: 5px !important; pointer-events:auto; font-size: 12px;"
                +"📺 ${stats.views?.format()}"
            }
            if (stats.notIndexed) {
                span {
                    title = NO_INDEX_TITLE
                    style = "display: inline-block !important; margin-left: 5px !important; pointer-events:auto; font-size: 12px;"
                    +"🤖"
                }
            }
        }
    }
}