package publication

import common.Requester
import common.checkNoIndex
import common.format
import kotlinx.browser.document
import kotlinx.datetime.Instant
import kotlinx.html.dom.append
import kotlinx.html.js.div
import kotlinx.html.js.span
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.serialization.json.JsonObject
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLMetaElement
import org.w3c.dom.HTMLSpanElement

class Video(requester: Requester, data: JsonObject) : PublicationPage(requester, data) {

    override suspend fun getStats() {
        val views = (document.querySelector("meta[property=\"ya:ovs:views_total\"]") as HTMLMetaElement).content.toInt()
        val uploadDateStr = (document.querySelector("meta[property=\"ya:ovs:upload_date\"]") as HTMLMetaElement).content
        val uploadDate = Instant.parse(uploadDateStr)
        val noindex = checkNoIndex()
        val url = "https://dzen.ru/video/watch/$publicationId"
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

        val infoBlock = document.querySelector("div.card-channel-info__description") as? HTMLDivElement

        infoBlock?.querySelector("span.card-channel-info__description-meta")?.let { element ->
            val span = element as? HTMLSpanElement
            span?.style?.setProperty("display", "inline-block", "important")
        }

        infoBlock?.append {
            span("card-channel-info__description-meta") {
                title = "Время создания (редактирования)"
                attributes["itemprop"] = "datePublished"
                style = "display: inline-block !important; margin-left: 5px !important; pointer-events:auto;"
                +"🕑 ${stats.showTime()}"
            }
            span("card-channel-info__description-meta") {
                title = "Просмотры"
                style = "display: inline-block !important; margin-left: 5px !important; pointer-events:auto;"
                +"📺 ${stats.views?.format()}"
            }
            if (stats.notIndexed) {
                span("card-channel-info__description-meta") {
                    title = NO_INDEX_TITLE
                    style = "display: inline-block !important; margin-left: 5px !important; pointer-events:auto;"
                    +"🤖"
                }
            }

        }
    }

    /*
    val viewsTillEndBlock = document.create.span ("card-channel-info__description-meta") {
        +"📼 ${stats.viewsTillEnd}"
        title = "Досмотры"
        style = "display: inline-block !important; margin-left: 5px !important; pointer-events:auto;"
    }
    infoBlock?.appendChild(viewsTillEndBlock) */


    /*
    val shortLinkBlock = document.create.span ("card-channel-info__description-meta") {
        +"🔗"
        title = SHORT_LINK_TITLE
        onClickFunction = {
            copyTextToClipboard(stats.shortLink)
        }
        style = "cursor: pointer; display: inline-block !important; margin-left: 5px !important; pointer-events:auto; z-index: 10000;"
    }
    infoBlock?.appendChild(shortLinkBlock) */
}