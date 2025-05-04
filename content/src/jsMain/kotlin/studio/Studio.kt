package studio

import ContentRunner
import common.Option
import common.dLog
import common.isFirefox
import kotlinx.browser.document
import kotlinx.coroutines.*
import org.w3c.dom.css.CSSStyleSheet
import publications.Publications
import requester.Requester

class Studio(val requester: Requester) : ContentRunner {

    @OptIn(DelicateCoroutinesApi::class)
    override fun run() {
        GlobalScope.launch {
            modifyStudioStyles()
            Menu(requester).create()
            Informer(requester).create()
            delay (1000)
            Publications(requester).processDashboardCards(10)
        }
    }


    suspend fun modifyStudioStyles() {
        if (!isFirefox()) {
            val hideRealtimeStatsList = Option.SHORT_DASHBOARD_REALTIME.value().await()
            val hidePromoteBanner = Option.PROMOTE_SHOW.value().await()
            val hideCommentsBlock = Option.COMMENTS_WIDGET.value().await()

            var sheetStr = ""

            if (hideRealtimeStatsList) {
                sheetStr += ".editor--realtime-publications__list-3o{display:none;}"
            }

            if (hidePromoteBanner) {
                sheetStr += ".editor--author-studio-dashboard__promoBanner-1U{display:none;}"
                sheetStr += ".editor--youtube-entrency-panel__root-2D{display:none;}"
                sheetStr += ".editor--promo-entrency-banner__root-Q1{display:none;}"
            }

            if (hideCommentsBlock) {
                sheetStr += ".editor--author-studio-comments-block__authorStudioCommentsBlock-13{display:none;}"
            }

            // Если нужно применить стили
            if (sheetStr.isNotEmpty()) {
                val sheet = js("new CSSStyleSheet()") as CSSStyleSheet
                sheet.asDynamic().replaceSync(sheetStr)
                document.asDynamic().adoptedStyleSheets = arrayOf(sheet)
            }
        } else {
            console.dLog("Prozen Firefox Edition detected. Skipping modifyStudioStyles function.")
        }
    }
}