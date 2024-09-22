package studio

import ContentRunner
import common.Option
import common.Requester
import kotlinx.browser.document
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.w3c.dom.css.CSSStyleSheet

class Studio(val requester: Requester) : ContentRunner {

    @OptIn(DelicateCoroutinesApi::class)
    override fun run() {
        GlobalScope.launch {
            modifyStudioStyles()
            Menu(requester).create()
            val informer = Informer(requester)
            informer.create()
        }
    }


    suspend fun modifyStudioStyles() {
        // Получение значений настроек с помощью асинхронных вызовов

        // val showComments = Option.COMMENTS_WIDGET.value().await()
        val hideRealtimeStatsList = Option.SHORT_DASHBOARD_REALTIME.value().await()
        val hidePromoteBanner = Option.PROMOTE_SHOW.value().await()

        var sheetStr = ""
        // if (!showComments) sheetStr += ".author-studio-comments-block__authorStudioCommentsBlock-13{display:none;}"

        if (hideRealtimeStatsList) {
            sheetStr += ".editor--realtime-publications__list-3o{display:none;}"
        }

        if (hidePromoteBanner) {
            sheetStr += ".editor--author-studio-dashboard__promoBanner-1U{display:none;}"
            sheetStr += ".editor--youtube-entrency-panel__root-2D{display:none;}"
        }

        // Если нужно применить стили
        if (sheetStr.isNotEmpty()) {
            val sheet = js("new CSSStyleSheet()") as CSSStyleSheet
            sheet.asDynamic().replaceSync(sheetStr)
            document.asDynamic().adoptedStyleSheets = arrayOf(sheet)
        }
    }
}