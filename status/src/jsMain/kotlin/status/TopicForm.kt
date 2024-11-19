package status

import kotlinx.browser.document
import kotlinx.dom.clear
import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.dom.create
import kotlinx.html.js.div
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList


fun showTopicSelectionForm(topics: Map<String, String>, selectedTopics: List<String>) {
    // Создаем затемняющий слой
    val overlay = document.create.div("prozen-topic-overlay") {
        id = "topic-select-overlay"
    }

    // Создаем контейнер формы
    val formContainer = document.create.div("prozen-topic-form-container") {
        id = "topic-select-form"
        // Заголовок формы
        div("prozen-topic-form-title") {
            +"Выберите темы"
        }

        // Контейнер для списка тем
        div("prozen-topic-list") {
            // Добавляем темы с чекбоксами
            topics.forEach { (topicId, topicName) ->
                div("prozen-topic-item") {
                    input(type = InputType.checkBox) {
                        value = topicId // Используем topicId как значение чекбокса
                        if (selectedTopics.contains(topicId)) {
                            checked = true // Устанавливаем чекбокс, если тема выбрана
                        }
                    }
                    span("prozen-topic-label") { +topicName }
                }
            }
        }

        // Кнопка "Выбрать"
        button(classes = "prozen-topic-select-button") {
            +"Выбрать"
            onClickFunction = {
                val selectedTopics = getSelectedTopics()
                console.log("Выбранные темы (topicIds): $selectedTopics")
                closeTopicsForm()
            }
        }
    }

    // Добавляем затемняющий фон и форму в документ
    document.body?.apply {
        appendChild(overlay)
        appendChild(formContainer)
    }
}

// Функция для получения выбранных topicId
fun getSelectedTopics(): List<String> {
    val checkboxes = document.querySelectorAll(".prozen-topic-item input[type='checkbox']").asList()
    return checkboxes.filter { (it as HTMLInputElement).checked }
        .map { (it as HTMLInputElement).value } // Возвращаем topicId из value
}

// Функция для закрытия формы и удаления затемняющего фона
fun closeTopicsForm() {
    document.getElementById("topic-select-overlay")?.remove()
    document.getElementById("topic-select-form")?.remove()
}

// Функция для создания интерфейса с выбранными темами и кнопкой вызова меню

fun TagConsumer<HTMLElement>.topicsPlaceholder(visible: Boolean = false, onOpenSelectionMenu: () -> Unit) {
    div("prozen-selected-topics-container") {
        id = "prozen-selected-topics-container"
        style = "display: ${if (visible) "block" else "none"};"
        div {
            this.id = "prozen-topic-placeholder"
            span("prozen-placeholder-text") {
                +"Выберите темы"
            }
        }
        button(classes = "prozen-select-button") {
            +"Выбрать темы"
            onClickFunction = { onOpenSelectionMenu() }
        }
    }
}

fun setTopicsVisible(visible: Boolean) {
    val topicContainer = document.getElementById("prozen-selected-topics-container") as HTMLElement
    topicContainer.style.display = if (visible) "block" else "none"
}

fun updateTopics(selectedTopics: Map<String, String>) {
    val placeholder = document.getElementById("prozen-topic-placeholder") as HTMLElement
    placeholder.clear()
    if (selectedTopics.isEmpty()) {
        placeholder.append.span("prozen-placeholder-text") {
            +"Выберите темы"
        }
    } else {
        // Отображаем выбранные темы как теги
        selectedTopics.forEach { (topicId, topicName) ->
            placeholder.append.div("prozen-selected-topic-tag") {
                +topicName
                attributes["data-topic-id"] = topicId
            }
        }
    }

}

/* fun displaySelectedTopicsContainer(selectedTopics: List<Pair<String, String>>, onOpenSelectionMenu: () -> Unit) {
    val container = document.create.div("prozen-selected-topics-container") {
        div {
            id = "prozen-topic-placeholder"

            if (selectedTopics.isEmpty()) {
                // Если темы не выбраны, показываем текст-подсказку
                span("prozen-placeholder-text") {
                    +"Выберите темы"
                }
            } else {
                // Отображаем выбранные темы как теги
                selectedTopics.forEach { (topicId, topicName) ->
                    div("prozen-selected-topic-tag") {
                        +topicName
                        attributes["data-topic-id"] = topicId // Сохраняем topicId для идентификации, если нужно
                    }
                }
            }
        }
        // Кнопка для открытия меню выбора тем
        button(classes = "prozen-select-button") {
            +"Выбрать темы"
            onClickFunction = { onOpenSelectionMenu() }
        }
    }

    // Вставляем контейнер в элемент на странице
    document.getElementById("selected-topics-display")?.appendChild(container)
} */