import common.*
import common.components.dropButton
import common.components.prozenCornerInfoBlock
import common.progress.Spinner
import kotlinx.browser.document
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.dom.clear
import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.onKeyDownFunction
import org.w3c.dom.*
import org.w3c.dom.events.KeyboardEvent
import requester.Requester


var publisherId: String? = null
var token: String? = null
var finder: Finder? = null


@OptIn(DelicateCoroutinesApi::class)
fun createSearchPage(root: HTMLElement) {
    root.append {
        div(classes = "prozen-search-container") {
            div(classes = "prozen-search-header-container") {
                h3(classes = "prozen-search-header") {
                    +"Поиск публикаций"
                }

                // Строка поиска
                div(classes = "prozen-search-bar") {
                    input(type = InputType.text, classes = "prozen-search-input") {
                        id = "search-input"
                        list = "search-history"
                        placeholder = "Введите ключевые слова для поиска..."
                        onKeyDownFunction = {
                            if ((it as KeyboardEvent).key == "Enter") {
                                it.preventDefault()
                                val button = document.getElementById("search-button") as HTMLButtonElement
                                button.click()
                            }
                        }
                    }
                    dataList {
                        id = "search-history"
                    }
                    button(classes = "prozen-search-button") {
                        +"Поиск"
                        id = "search-button"
                        onClickFunction = {
                            if (finder != null) {
                                val searchInput = document.getElementById("search-input") as HTMLInputElement
                                val query = searchInput.value.trim()
                                val typesToSearch = getTypesToSearch()
                                val period = getDateRange()

                                if (typesToSearch.isNotEmpty()) {
                                    addNewSearchToHistory(query)
                                    GlobalScope.launch {
                                        val ul = document.getElementById("search-results") as HTMLUListElement
                                        ul.clear()
                                        if (!finder!!.hasPublications()) {
                                            val spinner = Spinner()
                                            spinner.show("Публикации загружаются…")
                                            finder!!.loadPublications()
                                            spinner.close()
                                        }
                                        val found = finder!!.find(query, typesToSearch, period)

                                        if (found.isNotEmpty()) {
                                            found.forEach { card ->
                                                ul.appendChild(card.toLi())
                                            }
                                            val countStr = found.size.paucal("публикация", "публикации", "публикаций")
                                            showNotification("Поиск завершён.\nНайдено: $countStr из ${finder!!.count().format()}.")
                                        } else {
                                            showNotification("Поиск завершён.\nПубликации не найдены.")
                                        }

                                    }
                                } else {
                                    showNotification("Укажите тип публикаций")
                                }
                            } else {
                                showNotification("Произошла ошибка.\nЗакройте эту страницу и повторите попытку.")
                            }
                        }
                    }
                }


                // Фильтры контента
                // gif, short_video, brief, article
                div(classes = "prozen-search-filters") {
                    label {
                        input(classes = "prozen-search-types-input prozen-checkbox", type = InputType.checkBox) {
                            id = "article"
                            checked = true
                        }
                        +"Статьи"
                    }
                    label {
                        input(classes = "prozen-search-types-input prozen-checkbox", type = InputType.checkBox) {
                            id = "gif"
                            checked = true
                        }
                        +"Видео"
                    }
                    label {
                        input(classes = "prozen-search-types-input prozen-checkbox", type = InputType.checkBox) {
                            id = "short_video"
                            checked = true
                        }
                        +"Ролики"
                    }
                    label {
                        input(classes = "prozen-search-types-input prozen-checkbox", type = InputType.checkBox) {
                            id = "brief"
                            checked = true
                        }
                        +"Посты"
                    }
                }

                // Фильтр по дате

                val dates = getLastMonth().toStringPair()
                div(classes = "prozen-search-date-filter") {
                    div {
                        label { +"С даты:" }
                        input(type = InputType.date) {
                            id = "from-date"
                            classes = setOf("prozen-search-date-input")
                            value = dates.first
                        }
                    }
                    div {
                        label { +"По дату:" }
                        input(type = InputType.date) {
                            id = "to-date"
                            style = "margin-right: 10px;"
                            classes = setOf("prozen-search-date-input")
                            value = dates.second
                        }
                    }

                    dropButton(
                        buttonId = "prozen-search-dropdown",
                        buttonTitleOrClass = ".prozen-triangle-button-icon",
                        items = mapOf(
                            "this-month" to "Текущий месяц",
                            "month" to "Месяц",
                            "half-year" to "Полгода",
                            "year" to "Год",
                            "all-time" to "Всё время"
                        )
                    ) { selected ->
                        val timePeriod: Pair<Instant, Instant>? = when (selected) {
                            "this-month" -> {
                                getCurrentMonth()
                            }

                            "month" -> {
                                getLastMonth()
                            }

                            "year" -> {
                                getLastYear()
                            }

                            "half-year" -> {
                                getLast6Months()
                            }

                            "all-time" -> {
                                fromInputDate("2017-05-30")!! to Clock.System.now()
                            }

                            else -> null
                        }
                        timePeriod?.let { setDateRange(it) } ?: showNotification("Произошла ошибка.")
                    }
                }
            }

            div {
                id = "progress-indicator"
            }

            ul(classes = "prozen-search-results") {
                id = "search-results"
            }


        }
        prozenCornerInfoBlock()
    }
}

private fun getChannelId() {
    chrome.storage.local.get(arrayOf("prozenSearch", "prozenToken", "prozenPublisherId")) { result ->
        publisherId = result["prozenPublisherId"] as? String
        token = result["prozenToken"] as? String
        val requester = Requester(publisherId, token)
        if (requester.hasToken() && requester.hasPublisherId()) {
            finder = Finder(requester)
        } else {
            console.error("Failed to create Finder object for unknown publisher and token.")
        }
    }
}

fun getTypesToSearch(): List<String> {
    return document.getElementsByClassName("prozen-search-types-input")
        .asList()
        .filterIsInstance<HTMLInputElement>()
        .mapNotNull { checkBox -> checkBox.id.takeIf { checkBox.checked } }
}


fun main() {
    val rootElement = document.getElementById("search-root") as HTMLElement
    createSearchPage(rootElement)
    getChannelId()
    loadSearchHistory()
}
