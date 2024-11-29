import common.Option
import common.Options
import common.components.prozenCornerInfoBlock
import common.showNotification
import kotlinx.browser.document
import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import org.w3c.dom.events.KeyboardEvent

fun createSettingsPage(root: HTMLElement) {
    root.append {
        div(classes = "prozen-settings-container") {

            // Заголовок с логотипом
            div(classes = "prozen-settings-header") {
                img(src = chrome.runtime.getURL("img/toast-logo.png"))
                h3 { +"Продзен: настройки расширения" }
            }

            // Настройки
            val groups = Option.entries.asSequence().map { it.group }.filter { it != "hidden" }.toSet()
            groups.forEach { group ->
                if (group != "default") {
                    h4 { +group }
                }
                Option.entries.filter { it.group == group }.forEach { option ->
                    div(classes = "prozen-settings-item") {
                        label {
                            input(classes = "prozen-checkbox", type = InputType.checkBox) {
                                id = option.id
                                checked = option.defaultValue
                            }
                            +option.title
                        }
                        option.description?.let {
                            div(classes = "prozen-info-icon") {
                                +"ℹ"
                                span(classes = "prozen-tooltip") {
                                    +it
                                }
                            }
                        }
                    }
                }
            }

            // Кнопка Сохранить
            div(classes = "prozen-save-button-container") {
                button(classes = "prozen-save-button") {
                    +"Сохранить"
                    onClickFunction = {
                        val options = document.getElementsByTagName("input")
                            .asList()
                            .filterIsInstance<HTMLInputElement>()
                            .associate { it.id to it.checked }
                        Options.save(options)
                        showNotification("Настройки сохранены")
                    }
                }
            }

            // Футер с ссылками
            footer(classes = "prozen-footer") {
                a(href = "https://t.me/+jgjgYMVg2gY0ODVi", target = "_blank") { +"Телеграм-канал @prodzen" }
                a(href = "https://prozen.ru", target = "_blank") { +"prozen.ru" }
                a(href = "https://donate.stream/prodzen", target = "_blank") { +"Поддержать разработку" }
            }
        }
        prozenCornerInfoBlock()
    }
}

fun toggleDebug() {
    Option.DEBUG.value().then { option ->
        val debug = !option
        Option.DEBUG.set(debug)
        showNotification("Режим отладки ${if (debug) "включён" else "отключён"}")
    }
}

fun registerKeyEvents() {
    var isDPressed = false
    var isNumpad0Pressed = false
    document.addEventListener("keydown", { event ->
        val keyboardEvent = event as KeyboardEvent
        when (keyboardEvent.code) {
            "KeyD" -> isDPressed = true
            "Numpad0" -> isNumpad0Pressed = true
        }
        if (isDPressed && isNumpad0Pressed) {
            toggleDebug()
            isDPressed = false
            isNumpad0Pressed = false
        }
    })

    document.addEventListener("keyup", { event ->
        val keyboardEvent = event as KeyboardEvent
        when (keyboardEvent.code) {
            "KeyD" -> isDPressed = false
            "Numpad0" -> isNumpad0Pressed = false
        }
    })

}


fun main() {
    val rootElement = document.getElementById("settings-root") as HTMLElement
    createSettingsPage(rootElement)
    Options.load().then {
        it.entries.forEach { (key, value) ->
            (document.getElementById(key) as? HTMLInputElement)?.checked = value
        }
    }
    registerKeyEvents()
}
