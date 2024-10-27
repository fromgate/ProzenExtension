import kotlinx.browser.document

import kotlinx.dom.clear
import kotlinx.html.*
import kotlinx.html.dom.create
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import kotlin.math.min


/**
 *  @param rootElement - элемент внутри которого будет отображаться индикатор,
 *                       если null — то будет использован полноэкранный режим
 */
class ProgressIndicator(private val rootElement: HTMLElement? = null) {

    private val overlayMode: Boolean = rootElement == null

    init {
        clearExistingElements()
    }

    /**
     * Показывает спиннер с текстом
     * @param text - текст, отображаемый под спиннером
     */
    fun showSpinner(text: String = "Выполнение...") {
        clearExistingElements()

        val spinnerElement = document.create.div {
            if (overlayMode) {
                classes = setOf ("prozen-overlay", "prozen-overlay-spinner")
                div(classes = "prozen-spinner") {}
                div(classes = "prozen-overlay-text") { +text }
            } else {
                classes = setOf("prozen-inline-spinner")
                div(classes = "prozen-spinner") {}
                div(classes = "prozen-inline-text") { +text }
            }
        }
        appendToBodyOrRoot(spinnerElement)
    }

    /**
     * Показывает прогрессбар с текстом
     * @param text - текст, отображаемый под прогрессбаром
     */
    fun showProgressBar(text: String = "Выполнение...") {
        clearExistingElements()

        val progressElement = document.create.div {
            if (overlayMode) {
                classes = setOf ("prozen-overlay", "prozen-overlay-progress")
                div(classes = "prozen-progress-bar") {
                    div(classes = "prozen-progress-bar-fill") {}
                }
                div(classes = "prozen-overlay-text") { +text }
            } else {
                classes = setOf ("prozen-inline-progress")
                div(classes = "prozen-progress-bar") {
                    div(classes = "prozen-progress-bar-fill") {}
                }
                div(classes = "prozen-inline-text") { +text }
            }
        }
        appendToBodyOrRoot(progressElement)
    }

    /**
     * Обновляет прогрессбар (в процентах)
     * @param progress - значение прогресса от 0 до 100
     */
    fun updateProgress(progress: Int) {
        val normalizedProgress = min(progress, 100)
        val progressBarFill = document.getElementsByClassName("prozen-progress-bar-fill").item(0) as? HTMLElement
        progressBarFill?.style?.width = "$normalizedProgress%"
    }



    /**
     * Убирает спиннер или прогрессбар
     */
    fun hide() {
        clearExistingElements()
    }

    /**
     * Очищает ранее существующие элементы ожидания.
     */
    private fun clearExistingElements() {
        listOf ("prozen-overlay", "prozen-inline-spinner", "prozen-inline-progress").forEach { indicator->
            document.getElementsByClassName(indicator).asList().filterIsInstance<HTMLElement>().forEach {
                it.clear()
                it.remove()
            }
        }
    }

    /**
     * Добавляет элемент либо в body, если режим overlay, либо в root элемент.
     */
    private fun appendToBodyOrRoot(element: HTMLElement) {
        rootElement?.appendChild(element) ?: document.body?.appendChild(element)
    }
}