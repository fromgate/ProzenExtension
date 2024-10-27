package common.progress

import kotlinx.browser.document
import kotlinx.dom.clear
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.dom.create
import kotlinx.html.style
import org.w3c.dom.HTMLElement
import org.w3c.dom.get

class Spinner(private val rootElement: HTMLElement? = null) {
    private var spinnerElement: HTMLElement? = null

    fun show(text: String = "Выполнение...") {
        spinnerElement?.clear()
        spinnerElement?.remove()
        spinnerElement = document.create.div {
            if (rootElement == null) {
                classes = setOf("prozen-overlay", "prozen-overlay-spinner")
                div(classes = "prozen-spinner")
                div(classes = "prozen-overlay-text") { +text }
            } else {
                classes = setOf("prozen-inline-spinner")
                div(classes = "prozen-spinner")
                div(classes = "prozen-inline-text") { +text }
            }
        }
        appendToBodyOrRoot(spinnerElement!!)
    }

    fun update(text: String) {
        (spinnerElement?.querySelector(".prozen-inline-text") as? HTMLElement)?.innerText = text
    }

    fun close() {
        spinnerElement?.clear()
        spinnerElement?.remove()
        spinnerElement = null
    }

    private fun appendToBodyOrRoot(element: HTMLElement) {
        rootElement?.appendChild(element) ?: document.body?.appendChild(element)
    }

}