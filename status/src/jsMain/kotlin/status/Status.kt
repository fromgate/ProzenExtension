package status

import common.*
import common.components.dropButton
import common.components.prozenCornerInfoBlock
import common.progress.ProgressBar
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
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLUListElement
import org.w3c.dom.asList
import pageanalyzer.TypeCheck
import pageanalyzer.containsAnyChecks
import pageanalyzer.isOk
import requester.Requester
import kotlin.js.json

var publisherId: String? = null
var token: String? = null
var checker: Checker? = null

private fun getChannelId() {
    chrome.storage.local.get(arrayOf("prozenSearch", "prozenToken", "prozenPublisherId")) { result ->
        publisherId = result["prozenPublisherId"] as? String
        token = result["prozenToken"] as? String
        val requester = Requester(publisherId, token)
        if (requester.hasToken() && requester.hasPublisherId()) {
            checker = Checker(requester)
        } else {
            console.error("Failed to create Checker object for unknown publisher and token.")
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
fun createSearchPage(root: HTMLElement) {
    root.append {
        div(classes = "prozen-search-container") {
            div(classes = "prozen-search-header-container") {
                h3(classes = "prozen-search-header") {
                    +"Проверка публикаций"
                }

                // Фильтры контента
                // gif, short_video, brief, article
                h2(classes = "prozen-search-header-2") {
                    +"Форматы публикаций"
                }
                div(classes = "prozen-search-filters") {
                    label {
                        input(classes = "prozen-search-types-input prozen-checkbox", type = InputType.checkBox) {
                            id = "article"
                            checked = true
                            onChangeFunction = { saveChecks() }
                        }
                        +"Статьи"
                    }
                    label {
                        input(classes = "prozen-search-types-input prozen-checkbox", type = InputType.checkBox) {
                            id = "gif"
                            checked = true
                            onChangeFunction = { saveChecks() }
                        }
                        +"Видео"
                    }
                    label {
                        input(classes = "prozen-search-types-input prozen-checkbox", type = InputType.checkBox) {
                            id = "short_video"
                            checked = true
                            onChangeFunction = { saveChecks() }
                        }
                        +"Ролики"
                    }
                    label {
                        input(classes = "prozen-search-types-input prozen-checkbox", type = InputType.checkBox) {
                            id = "brief"
                            checked = true
                            onChangeFunction = { saveChecks() }
                        }
                        +"Посты"
                    }
                }

                h2(classes = "prozen-search-header-2") {
                    +"Проверяемые признаки"
                }

                div(classes = "prozen-search-filters") {
                    TypeCheck.entries.filter { it != TypeCheck.HTTP_STATUS_CODE }.forEach { typeCheck ->
                        label {
                            input(
                                classes = "prozen-search-type-checks-input prozen-checkbox",
                                type = InputType.checkBox
                            ) {
                                id = typeCheck.name
                                checked = true
                                if (typeCheck == TypeCheck.IS_BANNED) {
                                    disabled = true
                                }
                                onChangeFunction = { saveChecks() }
                            }
                            +"${typeCheck.icon} ${typeCheck.title}"
                        }
                    }
                }

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

                    button(classes = "prozen-search-button") {
                        +"Начать проверку"
                        id = "start-check-button"
                        onClickFunction = {
                            if (checker != null) {
                                val ul = document.getElementById("search-results") as HTMLUListElement
                                ul.clear()
                                GlobalScope.launch {
                                    val useFastCheck = Option.FAST_CHECK.isSet()
                                    if (!checker!!.hasPublications()) {
                                        val spinner = Spinner()
                                        spinner.show("Публикации загружаются…")
                                        checker!!.loadPublications()
                                        spinner.close()
                                    }
                                    val typesToSearch = getTypesToSearch()
                                    val period = getDateRange()
                                    val selectedTypeChecks = getSelectedTypeChecks()

                                    val found = checker!!.find(typesToSearch, period)

                                    val unloaded = checker!!.getUnloadedContextCards(found)

                                    var checkIsOk: Boolean = true
                                    console.dInfo("Unloaded / Found: ${unloaded.size} / ${found.size}")
                                    if (unloaded.isNotEmpty()) {
                                        val progress = ProgressBar()
                                        progress.show(1, "Загрузка публикаций")

                                        checkIsOk = if (useFastCheck) {
                                            checker!!.loadCardsInParallel(unloaded, 3, progress)
                                        } else {
                                            checker!!.loadCardsWithDelay(unloaded, progress)
                                        }
                                        progress.close()
                                    }

                                    val toShow = found
                                        .map { it to checker!!.getPageContext(it) }
                                        .filter { (card, context) ->
                                            val isBanned = card.isBanned
                                            val hasCheck =
                                                context?.let { !it.isOk() || it.containsAnyChecks(selectedTypeChecks) }
                                                    ?: false
                                            isBanned || hasCheck
                                        }

                                    console.dInfo("toShow: ${toShow.size} / found: ${found.size}")

                                    if (toShow.isNotEmpty()) {
                                        toShow.forEach { (card, context) ->
                                            ul.appendChild(card.toLi(context))
                                        }
                                        if (checkIsOk) {
                                            val countStr = toShow.size.paucal("публикация", "публикации", "публикаций")
                                            showNotification("Поиск завершён.\nНайдено: $countStr из ${checker!!.count().format()}.")

                                        } else {
                                            showNotification("Проверка прервана! Дзен включил капчу.\nПовторите проверку позднее.")
                                        }
                                    } else {
                                        showNotification("Поиск завершён.\nПубликации не найдены.")
                                    }
                                }
                            } else {
                                showNotification("Произошла ошибка")
                            }
                        }
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


fun getTypesToSearch(): List<String> {
    return document.getElementsByClassName("prozen-search-types-input")
        .asList()
        .filterIsInstance<HTMLInputElement>()
        .mapNotNull { checkBox -> checkBox.id.takeIf { checkBox.checked } }
}

fun getSelectedTypeChecks(): List<TypeCheck> {
    return document.getElementsByClassName("prozen-search-type-checks-input")
        .asList()
        .filterIsInstance<HTMLInputElement>()
        .mapNotNull { checkBox ->
            val checkedId = checkBox.id.takeIf { checkBox.checked }
            TypeCheck.getByName(checkedId)
        }
}

fun saveChecks() {
    val prozenStatusPublications = getTypesToSearch().joinToString(",")
    val prozenStatusChecks = getSelectedTypeChecks().joinToString(",") { it.name }
    chrome.storage.local.set(
        json("prozenStatusPublications" to prozenStatusPublications, "prozenStatusChecks" to prozenStatusChecks)
    )
}


fun loadChecks() {
    chrome.storage.local.get(arrayOf("prozenStatusPublications", "prozenStatusChecks")) { result ->
        val prozenStatusPublications = (result["prozenStatusPublications"] as? String)?.split(",")?.toSet()
        val prozenStatusChecks = (result["prozenStatusChecks"] as? String)?.split(",")?.toSet()
        prozenStatusPublications?.let { setInputChecks("prozen-search-types-input", it) }
        prozenStatusChecks?.let { setInputChecks("prozen-search-type-checks-input", it) }
    }
}

fun setInputChecks(className: String, checked: Set<String>) {
    document.getElementsByClassName(className)
        .asList()
        .filterIsInstance<HTMLInputElement>()
        .forEach { it.checked = checked.contains(it.id) }
}


fun main() {
    val root = document.getElementById("status-root") as? HTMLElement
    root?.let { createSearchPage(it) }
    loadChecks()
    getChannelId()
}