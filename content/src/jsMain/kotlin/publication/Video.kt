package publication

import common.M
import common.copyTextToClipboard
import common.format
import common.showNotification
import kotlinx.browser.document
import kotlinx.datetime.Instant
import kotlinx.dom.clear
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.span
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.serialization.json.JsonObject
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLMetaElement
import requester.Requester

class Video(requester: Requester, data: JsonObject) : PublicationPage(requester, data) {

    override suspend fun getStats() {
        val views = (document.querySelector("meta[property=\"ya:ovs:views_total\"]") as HTMLMetaElement).content.toInt()
        val uploadDateStr = (document.querySelector("meta[property=\"ya:ovs:upload_date\"]") as HTMLMetaElement).content
        val uploadDate = Instant.parse(uploadDateStr)
        val noindex = isNotIndexed()
        val url = "https://dzen.ru/video/watch/$publicationId"

        stats = PublicationStats(
            uploadDate,
            null,
            views,
            null,
            null,
            noindex,
            url
        )
    }

    override fun showStats() {
        val stats = this.stats ?: return

        val infoBlock = document.querySelector("div.video-site--card-channel-info__description-2R") as? HTMLDivElement
        infoBlock?.clear()

        infoBlock?.append {
            span {
                title = M.publicationTime
                attributes["itemprop"] = "datePublished"
                style = "display: inline-block !important; margin-left: 5px !important; pointer-events:auto;"
                +"🕑 ${stats.showTime()}"
            }
            span {
                title = M.publicationViews
                style = "display: inline-block !important; margin-left: 5px !important; pointer-events:auto;"
                +"📺 ${stats.views?.format()}"
            }

            span {
                title = M.publicationCopyLink
                style =
                    "display: inline-block !important; margin-left: 5px !important; cursor: pointer; pointer-events:auto;"
                onClickFunction = {
                    copyTextToClipboard(stats.shortLink)
                    showNotification(M.notificationLinkCopied)
                }
                +"🔗"
            }
            if (stats.notIndexed) {
                span {
                    title = M.publicationNotIndexed
                    style = "display: inline-block !important; margin-left: 5px !important; pointer-events:auto;"
                    +"🤖"
                }
            }
        }
    }
}