
import common.*
import common.components.dropButton
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
import org.w3c.dom.HTMLElement

var counter: Counter? = null
var token: String? = null
var publisherId: String? = null


fun createStatisticsPage(root: HTMLElement) {
    root.append {
        div(classes = "prozen-statistics-container") {
            h3(classes = "prozen-statistics-header") {
                +"Итоговая статистика"
            }
            div(classes = "prozen-statistics-filters") {
                val dates = getAllTime().toStringPair()
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

                    button(classes = "prozen-search-button") {
                        +"Рассчитать"
                        id = "start-check-button"
                        onClickFunction = {
                            calculateStats()
                        }
                    }

                    span(classes = "prozen-light-button") {
                        title = "Экспорт данных в Excel"
                        span("prozen-excel-button-icon")
                        onClickFunction = {
                            try {
                                val period = getDateRange()
                                val cards = counter?.cards?.filter { it.isInPeriod(period!!) }
                                console.dInfo("Cards to Export: ${cards?.size ?: "null"}")
                                if (!cards.isNullOrEmpty()) {
                                    ExcelExporter.export(cards)
                                } else {
                                    showNotification("Нет данных для экспортирования")
                                }
                            } catch (e: Exception) {
                                console.error("Export failed:", e)
                                showNotification("Не удалось экспортировать данные")
                            }
                        }
                    }
                }
            }
            div {
                id = "statistics"
            }
        }
    }
}


fun FlowContent.statisticsItem(description: String, valueId: String) {
    div(classes = "prozen-statistics-item") {
        span { +description }
        span(classes = "value") { id = valueId }
    }
}

private fun getChannelId() {
    chrome.storage.local.get(arrayOf("prozenSearch", "prozenToken", "prozenPublisherId")) { result ->
        publisherId = result["prozenPublisherId"] as? String
        token = result["prozenToken"] as? String
        val requester = Requester(publisherId, token)
        if (requester.hasToken() && requester.hasPublisherId()) {
            counter = Counter(requester)
        } else {
            console.error("Failed to create Counter object for unknown publisher and token.")
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
fun calculateStats() {
    counter?.let {
        GlobalScope.launch {
            if (!it.isDataLoaded()) {
                val spinner = Spinner()
                spinner.show("Загрузка данных…")
                it.loadData()
                spinner.close()
            }
            val result = it.calculateStats(getDateRange())
            val statistics = document.getElementById("statistics")
            statistics?.clear()


            result.values.toList().sortedByDescending { stat ->
                stat.impressions
            }.forEach { stat ->
                statistics?.append {
                    statBlock(stat)
                }
            }
        }
    } ?: showNotification("Произошла ошибка")
}


fun main() {
    getChannelId()
    val rootElement = document.getElementById("statistics-root") as HTMLElement
    createStatisticsPage(rootElement)
}

