package studio

import ContentRunner
import common.Option
import common.dLog
import kotlinx.browser.document
import kotlinx.coroutines.*
import org.w3c.dom.HTMLStyleElement
import publications.Publications
import requester.Requester

private const val STUDIO_STYLES_ELEMENT_ID = "prozen-studio-styles"

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
        val hideRealtimeStatsList = Option.SHORT_DASHBOARD_REALTIME.value().await()
        val hidePromoteBanner = Option.PROMOTE_SHOW.value().await()
        val hideCommentsBlock = Option.COMMENTS_WIDGET.value().await()

        val sheetStr = buildString {
            if (hideRealtimeStatsList) {
                append(".editor--realtime-publications__list-3o{display:none;}")
            }

            if (hidePromoteBanner) {
                append(".editor--author-studio-dashboard__promoBanner-1U{display:none;}")
                append(".editor--youtube-entrency-panel__root-2D{display:none;}")
                append(".editor--promo-entrency-banner__root-Q1{display:none;}")
                append(".editor--studio-promo-banners__root-10{display:none;}")
            }

            if (hideCommentsBlock) {
                append(".editor--author-studio-comments-block__authorStudioCommentsBlock-13{display:none;}")
            }
        }

        // Если нужно применить стили
        if (sheetStr.isNotEmpty()) {
            applyStudioStyles(sheetStr)
        }
    }

    /**
     * Применяет стили через constructed stylesheet — основной путь для Chrome/Edge
     * и Firefox 153+. В более старых Firefox `document.adoptedStyleSheets` недоступен
     * из content script'а (Xray-обёртки, bug 1751346), поэтому используется
     * запасной вариант с обычным элементом <style>.
     */
    private fun applyStudioStyles(css: String) {
        var applied = false

        try {
            val sheet = js("new CSSStyleSheet()")
            sheet.replaceSync(css)

            // Не перезаписываем adoptedStyleSheets целиком: в списке могут быть
            // листы, добавленные самой страницей
            val adopted = document.asDynamic().adoptedStyleSheets
            val updated = js("[]")
            val count = adopted.length as Int
            for (index in 0 until count) {
                updated.push(adopted[index])
            }
            updated.push(sheet)
            document.asDynamic().adoptedStyleSheets = updated

            // Если ранее сработал запасной вариант, убираем его, чтобы не дублировать правила
            document.getElementById(STUDIO_STYLES_ELEMENT_ID)?.let { it.parentNode?.removeChild(it) }

            applied = true
        } catch (e: dynamic) {
            console.dLog("adoptedStyleSheets is not available: $e")
        }

        if (!applied) {
            applyStudioStylesAsElement(css)
        }
    }

    private fun applyStudioStylesAsElement(css: String) {
        val style = document.getElementById(STUDIO_STYLES_ELEMENT_ID) as? HTMLStyleElement
            ?: (document.createElement("style") as HTMLStyleElement).also {
                it.id = STUDIO_STYLES_ELEMENT_ID
                (document.head ?: document.documentElement)?.appendChild(it)
            }

        style.textContent = css
    }
}
