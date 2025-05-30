package common.components

import kotlinx.html.TagConsumer
import kotlinx.html.span
import org.w3c.dom.HTMLElement

fun TagConsumer<HTMLElement>.spanDots() {
    span("prozen-widget-loading-dots") {
        span("prozen-widget-loading-dot")
        span("prozen-widget-loading-dot")
        span("prozen-widget-loading-dot")
    }
}