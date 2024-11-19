package common.progress

import kotlinx.browser.document
import kotlinx.dom.clear
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.dom.create
import org.w3c.dom.HTMLElement
import kotlin.math.min

class ProgressBar(private val rootElement: HTMLElement? = null) {
    private var progressElement: HTMLElement? = null

    fun show(initValue: Int = 0, text: String = "Выполнение...") {
        progressElement?.clear()
        progressElement?.remove()

        progressElement = document.create.div {
            if (rootElement == null) {
                classes = setOf("prozen-overlay", "prozen-overlay-progress")
                div(classes = "prozen-progress-bar") {
                    div(classes = "prozen-progress-bar-fill")
                }
                div(classes = "prozen-overlay-text") { +text }
            } else {
                classes = setOf("prozen-inline-progress")
                div(classes = "prozen-progress-bar") {
                    div(classes = "prozen-progress-bar-fill")
                }
                div(classes = "prozen-inline-text") { +text }
            }
        }
        appendToBodyOrRoot(progressElement!!)
    }

    fun update(progress: Int? = null, text: String? = null) {
        if (progress != null) {
            val normalizedProgress = min(progress, 100)
            val progressBarLine = progressElement?.querySelector(".prozen-progress-bar-fill") as? HTMLElement
            progressBarLine?.style?.width = "$normalizedProgress%"
        }
        if (text != null) {
            val progressText =
                progressElement?.querySelector(".prozen-overlay-text, .prozen-inline-text") as? HTMLElement
            progressText?.innerText = text
        }
    }

    fun update(value: Int, max: Int, text: String? = null) {
        val percentage = (value.toDouble() / max) * 100
        update(percentage.toInt(), text)
    }

    fun close() {
        progressElement?.clear()
        progressElement?.remove()
        progressElement = null
    }

    private fun appendToBodyOrRoot(element: HTMLElement) {
        rootElement?.appendChild(element) ?: document.body?.appendChild(element)
    }

}