package status

import common.*
import common.components.*
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

suspend fun loadChannelId() {
    val keys = arrayOf("prozenSearch", "prozenToken", "prozenPublisherId")
    val data = getFromStorageStr(keys)
    publisherId = data["prozenPublisherId"]
    token = data["prozenToken"]
    val requester = Requester(publisherId, token)
    if (requester.hasToken() && requester.hasPublisherId()) {
        checker = Checker(requester)
    } else {
        console.error("Failed to create Checker object for unknown publisher and token.")
        throw IllegalStateException("Invalid publisher or token")
    }
}

@OptIn(DelicateCoroutinesApi::class)
suspend fun createSearchPage(root: HTMLElement) {

    val dates = loadLastSelectedTime() ?: getLastMonth().toStringPair()

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
                    +"Доступность публикаций"
                }
                val premiumInputs = listOf(TypeCheck.PREMIUM, TypeCheck.NOT_PREMIUM)
                div("prozen-search-filters") {

                    premiumInputs.forEach { premiumType ->
                        label {
                            input(
                                classes = "prozen-search-filters-checks-input prozen-checkbox",
                                type = InputType.checkBox
                            ) {
                                id = "premium-filter-${premiumType.name}"
                                checked = premiumType == TypeCheck.NOT_PREMIUM
                                onChangeFunction = { premiumFilterClick("premium-filter-${premiumType.name}") }
                            }
                            +"${premiumType.icon} ${premiumType.title}"
                        }
                    }
                }

                h2(classes = "prozen-search-header-2") {
                    +"Проверяемые признаки"
                }

                div(classes = "prozen-search-filters") {
                    TypeCheck.entries.filter { it != TypeCheck.HTTP_STATUS_CODE && it !in premiumInputs }
                        .forEach { typeCheck ->
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
                        GlobalScope.launch {
                            saveSelectedTime(selected)
                        }
                        val timePeriod: Pair<Instant, Instant>? = selectedToPeriod(selected)
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

                                    val premiumFilter = getPremiumFilter()

                                    val toShow = found.mapNotNull { card ->
                                        val ctx = checker!!.getPageContext(card)
                                        if (card.isBanned) return@mapNotNull card to ctx
                                        ctx?.takeIf {
                                            !it.isOk() ||
                                                    (it.containsAnyChecks(selectedTypeChecks)
                                                            && it.containsAnyChecks(premiumFilter))
                                        }?.let { card to it }
                                    }

                                    console.dInfo("toShow: ${toShow.size} / found: ${found.size}")

                                    if (toShow.isNotEmpty()) {
                                        toShow.forEach { (card, context) ->
                                            ul.appendChild(card.toLi(context))
                                        }
                                        if (checkIsOk) {
                                            val countStr = toShow.size.paucal("публикация", "публикации", "публикаций")
                                            showNotification(
                                                "Поиск завершён.\nНайдено: $countStr из ${
                                                    checker!!.count().format()
                                                }."
                                            )

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

suspend fun saveSelectedTime(select: String) {
    saveToStorage("$publisherId-checker-period", select)
}

suspend fun loadLastSelectedTime(): Pair<String, String>? {
    val selected = getFromStorageStr("$publisherId-checker-period")
    return selected?.let { selectedToPeriod(it)?.toStringPair() }
}

fun selectedToPeriod(selected: String): Pair<Instant, Instant>? {
    return when (selected) {
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
}

fun premiumFilterClick(clickedId: String) {
    val ids = listOf(TypeCheck.PREMIUM.name, TypeCheck.NOT_PREMIUM.name)
    val checkboxes = ids.mapNotNull { document.getElementById( "premium-filter-$it") as? HTMLInputElement }
    if (checkboxes.size != 2) return

    val clicked = checkboxes.find { it.id == clickedId } ?: return
    val other = checkboxes.first { it != clicked }

    if (!clicked.checked && !other.checked) {
        other.checked = true
    }
}

fun getPremiumFilter(): List<TypeCheck> {
    val ids = listOf(TypeCheck.PREMIUM.name, TypeCheck.NOT_PREMIUM.name)
    return ids.mapNotNull {
        val isChecked = (document.getElementById("premium-filter-$it") as? HTMLInputElement)?.checked == true
        if (isChecked) TypeCheck.getByName(it) else null
    }
}

@OptIn(DelicateCoroutinesApi::class)
fun main() {
    GlobalScope.launch {
        val currentTheme: TriState = getFromStorageStr("prozen-theme").themeToTristate()
        setVisualTheme(currentTheme.toTheme())
        val root = document.getElementById("status-root") as? HTMLElement
        loadChannelId()
        root?.let { createSearchPage(it) }
        loadChecks()
    }
}