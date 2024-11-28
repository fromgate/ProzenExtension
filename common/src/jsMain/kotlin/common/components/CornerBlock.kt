package common.components

import kotlinx.html.TagConsumer
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.span
import org.w3c.dom.HTMLElement

fun TagConsumer<HTMLElement>.prozenCornerInfoBlock() {
    div(classes = "prozen-corner-block") {
        span(classes = "prozen-corner-text") { +"Продзен v${chrome.runtime.getManifest().version}" }
        a(href = "https://t.me/+jgjgYMVg2gY0ODVi", target = "_blank", classes = "prozen-corner-link") {
            +"Подпишись на @prodzen в телеграме"
        }
        a(href = "https://donate.stream/prodzen", target = "_blank", classes = "prozen-corner-link") {
            +"Поддержите разработку расширения"
        }
    }
}