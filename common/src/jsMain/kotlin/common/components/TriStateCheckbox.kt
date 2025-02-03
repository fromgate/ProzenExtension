package common.components

import kotlinx.browser.document
import kotlinx.html.TagConsumer
import kotlinx.html.id
import kotlinx.html.js.div
import kotlinx.html.js.onClickFunction
import kotlinx.html.span
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement

enum class TriState {
    UNCHECKED, CHECKED, INDETERMINATE;

    fun next(): TriState = when (this) {
        UNCHECKED -> CHECKED
        CHECKED -> INDETERMINATE
        INDETERMINATE -> UNCHECKED
    }
}

fun TagConsumer<HTMLElement>.triStateCheckbox(
    componentId: String,
    label: String = "",
    baseClass: String = "prozen-tristate-checkbox",
    initialState: TriState = TriState.UNCHECKED,
    onChange: (TriState) -> Unit = {}
) {
    div(classes = "prozen-tristate-container") {
        div(classes = baseClass) {
            this.id = componentId
            attributes["data-state"] = initialState.name.lowercase()

            onClickFunction = {
                val element = document.getElementById(this.id) as? HTMLDivElement
                val currentState = TriState.entries.firstOrNull { it.name.lowercase() == element?.getAttribute("data-state") }
                    ?: TriState.UNCHECKED
                val newState = currentState.next()
                element?.setAttribute("data-state", newState.name.lowercase())
                onChange(newState)
            }
        }
        if (label.isNotEmpty()) {
            span {
                id = "$componentId-label"
                +label
            }
        }
    }
}