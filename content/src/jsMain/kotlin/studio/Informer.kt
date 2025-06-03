package studio

import common.*
import common.components.spanDots
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.dom.clear
import kotlinx.dom.removeClass
import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.div
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLSpanElement
import org.w3c.dom.events.EventListener
import requester.Requester
import kotlin.js.json
import kotlin.time.Duration.Companion.days

class Informer(val requester: Requester) {
    var metrikaUrl = "https://metrika.yandex.ru/list"
    @OptIn(DelicateCoroutinesApi::class)
    fun create(count: Int = 0) {
        console.dLog("Informer / create : count: $count")
        GlobalScope.launch {
            if (!Option.INFORMER.isSet()) return@launch
            if (document.getElementById("prozen-informer") != null) return@launch
            val rightColumn = document
                .querySelector("div[class^=editor--author-studio-dashboard__rightContent-]") as? HTMLElement
            if (rightColumn != null) {
                val data = getDeferredData()
                appendInformer(rightColumn, data)
            } else {
                console.dLog("Studio / Informer: rightColumn is null")
                if (count <= 10) {
                    delay(500)
                    create(count + 1)
                } else {
                    console.log("Failed to find place for Prozen Informer")
                }
            }
        }
    }

    suspend fun getData(): InformerData = coroutineScope {
        console.dLog("Informer / getData()")
        return@coroutineScope getDeferredData().await()
    }

    suspend fun getDeferredData(): InformerDataDeferred = coroutineScope {
        console.dLog("Informer / getDeferredData()")

        val now = Clock.System.now()
        val todayStr = now.toYYYYMD()
        val before7Str = now.minus(7.days).toYYYYMD()
        val before30Str = now.minus(30.days).toYYYYMD()

        val channelUrl = getSignificantUrl(window.location.href).replace("profile/editor/", "")
        val zenReaderUrl = zenReaderUrl(channelUrl)

        /* val metrikaIdDeferred = async { requester.getMetrikaId() }
        val regTimeDeferred = async { requester.getRegTime() } */
        val channelDataDeferred = async { requester.getChannelData() }

        val strikesDeferred = async { requester.getStrikesInfo() }
        val scrDeferred = async { requester.getScr(before30Str, todayStr) }
        val blockedReadersDeferred = async { requester.getBannedUsers() }
        val statsTimeDeferred = async { requester.getStatsActuality() }
        val minuteCourseDeferred = async { requester.getTimespentRewards(before7Str, todayStr) ?: emptyList() }
        val channelUnIndexedDeferred = async { checkNoIndexUrl(channelUrl) }
        return@coroutineScope InformerDataDeferred(
            limitedAndStrikes = strikesDeferred,
            channelUnIndexed = channelUnIndexedDeferred,
            scr = scrDeferred,
            blockedReaders = blockedReadersDeferred,
            statsTime = statsTimeDeferred,
            minuteCourse = minuteCourseDeferred,
            zenReaderUrl = zenReaderUrl,
            metrikaIdAndRegTime = channelDataDeferred
        )
    }

    fun appendInformer(parent: HTMLElement, data: InformerDataDeferred) {
        if (document.getElementById("prozen-widget") != null) return

        parent.append {
            div("prozen-widget") {
                id = "prozen-widget"
                div("prozen-header-wrapper") {
                    style = "position: relative;"
                    div("prozen-header-content") {
                        title = "Добавлено расширением „Продзен“"
                        img {
                            src = chrome.runtime.getURL("img/toast-logo.png")
                        }
                        span {
                            +"Состояние канала"
                        }
                        // Кнопка для меню
                        button(classes = "prozen-menu-button") {
                            +"☰"
                            onClickFunction = {
                                val menu = document.getElementById("prozen-dropdown-menu") as HTMLElement
                                if (menu.classList.contains("prozen-menu-open")) {
                                    closeMenu()
                                } else {
                                    menu.classList.toggle("prozen-menu-open")
                                    if (menu.classList.contains("prozen-menu-open")) {
                                        document.addEventListener(
                                            "click",
                                            createDocumentClickListener(),
                                            true
                                        )
                                    }
                                }
                            }
                        }
                    }

                    ul("prozen-dropdown-menu") {
                        id = "prozen-dropdown-menu"
                        li {
                            span("prozen-menu-stats")
                            +"Полная статистика"
                            onClickFunction = {
                                closeMenu()
                                chrome.storage.local.set(
                                    json(
                                        "prozenToken" to requester.token,
                                        "prozenPublisherId" to requester.publisherId
                                    )
                                ) {
                                    window.open(chrome.runtime.getURL("stats.html"))
                                }
                            }
                        }
                        li {
                            span("prozen-menu-metrika")
                            +"Метрика"
                            onClickFunction = {
                                closeMenu()
                                window.open(
                                    metrikaUrl
                                )
                            }
                        }
                        li {
                            span("prozen-menu-search")
                            +"Поиск"
                            onClickFunction = {
                                closeMenu()
                                chrome.storage.local.set(
                                    json(
                                        "prozenSearch" to "", // неактуально
                                        "prozenToken" to requester.token,
                                        "prozenPublisherId" to requester.publisherId
                                    )
                                ) {
                                    window.open(chrome.runtime.getURL("search.html"))
                                }
                            }
                        }
                        li {
                            span("prozen-menu-robot")
                            +"Проверка публикаций"
                            onClickFunction = {
                                closeMenu()
                                chrome.storage.local.set(
                                    json(
                                        "prozenId" to requester.publisherId,
                                        "prozenToken" to requester.token,
                                        "prozenPublisherId" to requester.publisherId
                                    )
                                ) {
                                    window.open(chrome.runtime.getURL("status.html"))
                                }
                            }
                        }
                        li {
                            span("prozen-menu-telegram")
                            +"Продзен в Telegram"
                            onClickFunction = {
                                closeMenu()
                                window.open("https://t.me/+jgjgYMVg2gY0ODVi", "_blank")
                            }
                        }
                    }
                }
                div("prozen-widget-content") {
                    div("prozen-widget-item") {
                        id = "prozen-widget-strikes"
                        title = "Информация получена на основе данных раздела «Предупреждения»"
                        span("prozen-widget-item-title") {
                            +"Предупреждения: "
                        }
                        spanDots()
                    }
                    div("prozen-widget-item") {
                        id = "prozen-widget-banned"
                        title = "Информация получена на основе данных раздела «Предупреждения»"
                        span("prozen-widget-item-title") {
                            +"Канал ограничен: "
                        }
                        spanDots()
                    }
                    data.withStrikes {
                        val spanStrikes = document.getDivById("prozen-widget-strikes")?.getChildSpan(1)
                        val spanBanned = document.getDivById("prozen-widget-banned")?.getChildSpan(1)
                        spanStrikes?.clearDots()
                        spanBanned?.clearDots()

                        val strikesData = it.toNonNullPairOrNull()

                        if (strikesData == null) {
                            spanStrikes?.innerText = "?"
                            spanStrikes?.title = M.failedToGetData
                            spanBanned?.innerText = "?"
                            spanBanned?.title = M.failedToGetData
                        } else {
                            spanStrikes?.innerText = strikesData.second.toString()
                            if (strikesData.second > 0) spanStrikes?.className = "prozen-widget-warning"
                            spanStrikes?.title = M.widgetStrikeTitle

                            spanBanned?.innerText = "Да".takeIf { strikesData.first } ?: "Нет"
                            if (strikesData.first) spanBanned?.className = "prozen-widget-warning"
                            spanBanned?.title = M.widgetStrikeTitle

                            Warnings(requester.publisherId!!, strikesData).notify()
                        }
                    }
                    div("prozen-widget-item") {
                        id = "prozen-widget-noindex"
                        span("prozen-widget-item-title") {
                            +"Индексация канала: "
                        }
                        spanDots()
                    }
                    data.withChannelUnIndexed {
                        val notIndexed = it == true
                        val indexDiv = document.getDivById("prozen-widget-noindex")
                        val indexSpan = indexDiv?.getChildSpan(1)
                        indexSpan?.clearDots()
                        indexSpan?.innerText = if (notIndexed) "Нет 🤖" else "Есть"
                        if (notIndexed) indexDiv?.title =
                            """Обнаружен мета-тег <meta name="robots" content="noindex" />
                               Главная страница канала не индексируется поисковиками.
                               Это нормальная ситуация для новых каналов.""".trimIndent()
                    }

                    //withScr
                    div("prozen-widget-item") {
                        title = "Коэффициент охвата подписчиков (Subscribers Coverage Rate).\n" +
                                "Показывает какая доля подписчиков видит карточки публикаций."
                        id = "prozen-widget-scr"
                        span("prozen-widget-item-title") {
                            +"Охват подписчиков (SCR): "
                        }
                        spanDots()
                    }

                    data.withScr { scr ->
                        val scrSpan = document.getDivById("prozen-widget-scr")?.getChildSpan(1)
                        scrSpan?.clearDots()
                        scrSpan?.innerText = scr?.let { "${it.format(2)}%" } ?: "?"
                    }

                    div("prozen-widget-item") {
                        id = "prozen-widget-blockedreaders"
                        title = "Количество заблокированных комментаторов"
                        span("prozen-widget-item-title") {
                            +"Заблокировано читателей: "
                        }
                        spanDots()
                    }

                    data.withBlockedReaders {
                        val brSpan = document.getDivById("prozen-widget-blockedreaders")?.getChildSpan(1)
                        brSpan?.clearDots()
                        brSpan?.innerText = it?.format() ?: "?"
                    }

                    div("prozen-widget-item") {
                        id = "prozen-widget-regtime"
                        title = "Дата создания канала"
                        span("prozen-widget-item-title") {
                            +"Канал создан: "
                        }
                        spanDots()
                    }

                    data.withMetrikaIdAndRegTime {
                        val metrikaId = it?.first
                        if (metrikaId != null) {
                            metrikaUrl = "https://metrika.yandex.ru/dashboard?id=$metrikaId"
                        }
                        val regTime = it?.second
                        val regtimeDiv = document.getDivById("prozen-widget-regtime")
                        if (regTime != null) {
                            val regtimeSpan = regtimeDiv?.getChildSpan(1)
                            regtimeSpan?.innerText = regTime.toDDMMYY()
                        } else {
                            regtimeDiv?.remove()
                        }
                    }

                    div("prozen-widget-item") {
                        id = "prozen-widget-statstime"
                        title = "Время обновления статистики"
                        span("prozen-widget-item-title") {
                            +"Статистика от: "
                        }
                        spanDots()
                    }

                    data.withStatsTime {
                        val stattimeSpan = document.getDivById("prozen-widget-statstime")?.getChildSpan(1)
                        stattimeSpan?.clearDots()
                        stattimeSpan?.innerText = it ?: "?"
                    }

                    //withMinuteCourse

                    div("prozen-widget-item") {
                        id = "prozen-widget-minutecourse"
                        title = "Стоимость минуты вовлечённого просмотра"
                        span("prozen-widget-item-title") {
                            +"Курс минуты: "
                        }
                        spanDots()
                    }

                    data.withMinuteCourse {
                        val courseDiv = document.getDivById("prozen-widget-minutecourse")
                        val courseSpan = courseDiv?.getChildSpan(1)
                        if (!it.isNullOrEmpty()) {
                            it.lastNonZero()?.let { last ->
                                var titleText = "Стоимость минуты вовлечённого просмотра"
                                val previous = it.previousToLastNonZero()
                                if (previous != null) {
                                    titleText += "\nПредыдущий курс (${previous.first}): ${previous.third.format(3)} ₽"
                                }
                                val date = last.first
                                val course = "${last.third.format(3)}₽"
                                val colorClass = when {
                                    previous?.third == null -> null
                                    previous.third <= last.third -> "prozen-widget-success"
                                    else -> "prozen-widget-error"
                                }

                                courseDiv?.title = titleText
                                courseDiv?.getChildSpan(0)?.innerText = "Курс минуты $date: "

                                courseSpan?.innerText = course
                                if (colorClass != null) {
                                    courseSpan?.className = colorClass
                                }
                            }
                        } else {
                            courseSpan?.innerText = "?"
                        }
                    }
                    data.zenReaderUrl.let {
                        div("prozen-widget-item") {
                            title = "Ссылка для подписки на канал\nв телеграм-боте @ZenReaderBot"
                            a(href = it, classes = "prozen-widget-link") {
                                +"🔗 Подписка в Дзен-ридере"
                            }
                        }
                    }
                }
            }
        }
    }

    /* fun appendStyledInformer(parent: HTMLElement, data: InformerData) {
        if (document.getElementById("prozen-widget") != null) return
        with(data) {
            parent.append {
                div("prozen-widget") {
                    id = "prozen-widget"
                    div("prozen-header-wrapper") {
                        style = "position: relative;"
                        div("prozen-header-content") {
                            title = "Добавлено расширением „Продзен“"
                            img {
                                src = chrome.runtime.getURL("img/toast-logo.png")
                            }
                            span {
                                +"Состояние канала"
                            }
                            // Кнопка для меню
                            button(classes = "prozen-menu-button") {
                                +"☰"
                                onClickFunction = {
                                    val menu = document.getElementById("prozen-dropdown-menu") as HTMLElement
                                    if (menu.classList.contains("prozen-menu-open")) {
                                        closeMenu()
                                    } else {
                                        menu.classList.toggle("prozen-menu-open")
                                        if (menu.classList.contains("prozen-menu-open")) {
                                            document.addEventListener(
                                                "click",
                                                createDocumentClickListener(),
                                                true
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        ul("prozen-dropdown-menu") {
                            id = "prozen-dropdown-menu"
                            li {
                                span("prozen-menu-stats")
                                +"Полная статистика"
                                onClickFunction = {
                                    closeMenu()
                                    chrome.storage.local.set(
                                        json(
                                            "prozenToken" to requester.token,
                                            "prozenPublisherId" to requester.publisherId
                                        )
                                    ) {
                                        window.open(chrome.runtime.getURL("stats.html"))
                                    }
                                }
                            }
                            li {
                                span("prozen-menu-metrika")
                                +"Метрика"
                                onClickFunction = {
                                    closeMenu()
                                    window.open(
                                        "https://metrika.yandex.ru/list".takeIf {
                                            metrikaId == null
                                        } ?: "https://metrika.yandex.ru/dashboard?id=$metrikaId"
                                    )
                                }
                            }
                            li {
                                span("prozen-menu-search")
                                +"Поиск"
                                onClickFunction = {
                                    closeMenu()
                                    chrome.storage.local.set(
                                        json(
                                            "prozenSearch" to "", // неактуально
                                            "prozenToken" to requester.token,
                                            "prozenPublisherId" to requester.publisherId
                                        )
                                    ) {
                                        window.open(chrome.runtime.getURL("search.html"))
                                    }
                                }
                            }
                            li {
                                span("prozen-menu-robot")
                                +"Проверка публикаций"
                                onClickFunction = {
                                    closeMenu()
                                    chrome.storage.local.set(
                                        json(
                                            "prozenId" to requester.publisherId,
                                            "prozenToken" to requester.token,
                                            "prozenPublisherId" to requester.publisherId
                                        )
                                    ) {
                                        window.open(chrome.runtime.getURL("status.html"))
                                    }
                                }
                            }
                            li {
                                span("prozen-menu-telegram")
                                +"Продзен в Telegram"
                                onClickFunction = {
                                    closeMenu()
                                    window.open("https://t.me/+jgjgYMVg2gY0ODVi", "_blank")
                                }
                            }
                        }
                    }
                    div("prozen-widget-content") {
                        strikes?.let { strikes ->
                            div("prozen-widget-item") {
                                title = "Информация получена на основе данных раздела «Предупреждения»"
                                span("prozen-widget-item-title") {
                                    +"Предупреждения: "
                                }
                                span("prozen-widget-warning".takeIf { strikes > 0 }) {
                                    +strikes.toString()
                                }
                            }
                        }
                        channelLimited?.let { limited ->
                            div("prozen-widget-item") {
                                title = "Информация получена на основе данных раздела «Предупреждения»"
                                span("prozen-widget-item-title") {
                                    +"Канал ограничен: "
                                }
                                span("prozen-widget-warning".takeIf { limited }) {
                                    +if (limited) "Да" else "Нет"
                                }
                            }
                        }
                        channelUnIndexed?.let { notIndexed ->
                            div("prozen-widget-item") {
                                if (notIndexed) {
                                    title = "Обнаружен мета-тег <meta name=\"robots\" content=\"noindex\" />\n" +
                                            "Главная страница канала не индексируется поисковиками.\n" +
                                            "Это нормальная ситуация для новых каналов."
                                }
                                span("prozen-widget-item-title") {
                                    +"Индексация канала: "
                                }
                                span("prozen-widget-warning".takeIf { notIndexed }) {
                                    +if (notIndexed) "Нет 🤖" else "Есть"
                                }
                            }
                        }

                        scr?.let {
                            if (!scr.isNaN()) {
                                div("prozen-widget-item") {
                                    title = "Коэффициент охвата подписчиков (Subscribers Coverage Rate).\n" +
                                            "Показывает какая доля подписчиков видит карточки публикаций."
                                    div("prozen-widget-item") {
                                        span("prozen-widget-item-title") {
                                            +"Охват подписчиков (SCR): "
                                        }
                                        span {
                                            +"${it.format(2)}%"
                                        }
                                    }
                                }
                            }
                        }
                        blockedReaders?.let {
                            div("prozen-widget-item") {
                                title = "Количество заблокированных комментаторов"
                                span("prozen-widget-item-title") {
                                    +"Заблокировано читателей: "
                                }
                                span {
                                    +it.format()
                                }
                            }
                        }
                        regTime?.let {
                            div("prozen-widget-item") {
                                title = "Дата создания канала"
                                span("prozen-widget-item-title") {
                                    +"Канал создан: "
                                }
                                span {
                                    +it.toDDMMYYYYHHMM()
                                }
                            }
                        }
                        statsTime?.let {
                            div("prozen-widget-item") {
                                title = "Время обновления статистики"
                                span("prozen-widget-item-title") {
                                    +"Статистика от: "
                                }
                                span {
                                    +it
                                }
                            }
                        }
                        minuteCourse.lastNonZero()?.let { last ->
                            var titleText = "Стоимость минуты вовлечённого просмотра"
                            val previous = minuteCourse.previousToLastNonZero()
                            if (previous != null) {
                                titleText += "\nПредыдущий курс (${previous.first}): ${previous.third.format(3)} ₽"
                            }

                            div("prozen-widget-item") {
                                title = titleText
                                span("prozen-widget-item-title") {
                                    +"Курс минуты ${last.first}: "
                                }
                                span(
                                    when {
                                        previous?.third == null -> null
                                        previous.third <= last.third -> "prozen-widget-success"
                                        else -> "prozen-widget-error"
                                    }
                                ) {
                                    +"${last.third.format(3)}₽"
                                }
                            }
                        }
                        zenReaderUrl?.let {
                            div("prozen-widget-item") {
                                title = "Ссылка для подписки на канал\nв телеграм-боте @ZenReaderBot"
                                a(href = it, classes = "prozen-widget-link") {
                                    +"🔗 Подписка в Дзен-ридере"
                                }
                            }
                        }
                    }
                }
            }
        }
    } */

    fun createDocumentClickListener(): EventListener {
        val menu = document.getElementById("prozen-dropdown-menu") as HTMLElement
        val menuButton = document.querySelector(".prozen-menu-button") as HTMLElement
        return EventListener { event ->
            val target = event.target as? HTMLElement
            if (target != null && !menu.contains(target) && target != menuButton) {
                closeMenu()
            }
        }
    }

    fun closeMenu() {
        val menu = document.getElementById("prozen-dropdown-menu") as HTMLElement
        if (menu.classList.contains("prozen-menu-open")) {
            menu.classList.remove("prozen-menu-open")
            document.removeEventListener(
                "click",
                createDocumentClickListener(),
                true
            )
        }
    }

}

private fun HTMLSpanElement.clearDots() {
    this.clear()
    this.removeClass("prozen-widget-loading-dots")
}

data class InformerData(
    val strikes: Int?,
    val channelLimited: Boolean?,
    val channelUnIndexed: Boolean?,
    val scr: Double?,
    val blockedReaders: Int?,
    val statsTime: String?,
    val minuteCourse: List<Triple<String, Double, Double>>,
    val zenReaderUrl: String?,
    val metrikaId: Int?,
    val regTime: Instant?,
)

fun InformerData.isNotNull(): Boolean {
    return listOf(
        strikes,
        channelLimited,
        channelUnIndexed,
        scr,
        blockedReaders,
        statsTime,
        zenReaderUrl
    ).any { it != null } || minuteCourse.isNotEmpty()
}

fun List<Triple<String, Double, Double>>.lastNonZero(): Triple<String, Double, Double>? {
    return this.lastOrNull { it.third != 0.0 }
}

fun List<Triple<String, Double, Double>>.previousToLastNonZero(): Triple<String, Double, Double>? {
    val lastNonZeroIndex = this.indexOfLast { it.third != 0.0 }
    return if (lastNonZeroIndex > 0) this[lastNonZeroIndex - 1] else null
}

@OptIn(DelicateCoroutinesApi::class)
class InformerDataDeferred(
    val limitedAndStrikes: Deferred<Pair<Boolean, Int>?>,
    val channelUnIndexed: Deferred<Boolean?>,
    val scr: Deferred<Double?>,
    val blockedReaders: Deferred<Int?>,
    val statsTime: Deferred<String?>,
    val minuteCourse: Deferred<List<Triple<String, Double, Double>>>,
    val zenReaderUrl: String,
    val metrikaIdAndRegTime: Deferred<Pair<Int?, Instant?>?>
    /*val metrikaId: Deferred<Int?>,
    val regTime: Deferred<Instant?>, */
) {

    suspend fun await(): InformerData {
        val limitedAndStrikesValue = this.limitedAndStrikes.await()
        val metrikaIdAndRegTimeValue = this.metrikaIdAndRegTime.await()
        return InformerData(
            strikes = limitedAndStrikesValue?.second,
            channelLimited = limitedAndStrikesValue?.first,
            channelUnIndexed = channelUnIndexed.await(),
            scr = scr.await(),
            blockedReaders = blockedReaders.await(),
            statsTime = statsTime.await(),
            minuteCourse = minuteCourse.await(),
            zenReaderUrl = zenReaderUrl,
            metrikaId = metrikaIdAndRegTimeValue?.first,
            regTime = metrikaIdAndRegTimeValue?.second
        )
    }

    fun <T> withDeferred(
        deferred: Deferred<T>,
        block: suspend (T?) -> Unit,
    ): Job = GlobalScope.launch {
        block(deferred.awaitWithTimeoutOrNull(3000L))
    }

    fun withStrikes( block: suspend (Pair<Boolean?, Int?>?) -> Unit) =
        withDeferred(limitedAndStrikes, block)

    fun withChannelUnIndexed(block: suspend (Boolean?) -> Unit) =
        withDeferred(channelUnIndexed, block)

    fun withScr(block: suspend (Double?) -> Unit) =
        withDeferred(scr, block)

    fun withBlockedReaders(block: suspend (Int?) -> Unit) =
        withDeferred(blockedReaders, block)

    /*
    fun withMetrikaId(block: suspend (Int?) -> Unit) =
        withDeferred(metrikaId, block)

    fun withRegTime(block: suspend (Instant?) -> Unit) =
        withDeferred(regTime, block)*/

    fun withMetrikaIdAndRegTime(block: suspend (Pair<Int?,Instant?>?) -> Unit) {
        withDeferred(metrikaIdAndRegTime, block)
    }

    fun withStatsTime(block: suspend (String?) -> Unit) =
        withDeferred(statsTime, block)

    fun withMinuteCourse(block: suspend (List<Triple<String, Double, Double>>?) -> Unit) =
        withDeferred(minuteCourse, block)

}