package studio

import common.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.*
import kotlinx.html.js.div
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.EventListener
import kotlin.js.json
import kotlin.time.Duration.Companion.days

class Informer(val requester: Requester) {

    @OptIn(DelicateCoroutinesApi::class)
    fun create(count: Int = 0) {
        GlobalScope.launch {
            if (!Option.INFORMER.value().await()) return@launch
            val data = getData()
            if (document.getElementById("prozen-informer") != null) return@launch
            val rightColumn = document
                .querySelector("div[class^=editor--author-studio-dashboard__rightContent-]") as? HTMLElement
            if (rightColumn != null) {
                appendStyledInformer(rightColumn, data)
            } else {
                if (count <= 3) {
                    delay(500)
                    create(count + 1)
                } else {
                    console.log("Failed to find place for Prozen Informer")
                }
            }
        }
    }

    suspend fun getData(): InformerData = coroutineScope {
        val date = Clock.System.now()
        val todayStr = date.toYYYYMMDD()
        val before7 = date.minus(7.days)
        val before7Str = before7.toYYYYMMDD()
        val before30 = date.minus(30.days)
        val before30Str = before30.toYYYYMMDD()
        val channelUrl = window.location.href.replace("profile/editor/", "")
        val zenReaderUrl = zenReaderUrl(channelUrl)

        val channelDataDeferred = async { requester.getChannelData() }
        val strikesDeferred = async { requester.getStrikesInfo() }
        val scrDeferred = async { requester.getScr(before30Str, todayStr) }
        val blockedReadersDeferred = async { requester.getBannedUsers() }
        val statsTimeDeferred = async { requester.getStatsActuality() }
        val minuteCourseDeferred = async { requester.getTimespentRewards(before7Str, todayStr) }
        val channelUnIndexedDeferred = async { checkNoIndexUrl(channelUrl) }

        val strikes = strikesDeferred.await()
        val scr = scrDeferred.await()
        val blockedReaders = blockedReadersDeferred.await()
        val statsTime = statsTimeDeferred.await()
        val minuteCourse = minuteCourseDeferred.await()
        val channelUnIndexed = channelUnIndexedDeferred.await()
        val channelData = channelDataDeferred.await()

        strikes?.let { Warnings(requester.publisherId!!, it).notify() }


        return@coroutineScope InformerData(
            strikes = strikes?.second,
            channelLimited = strikes?.first,
            channelUnIndexed = channelUnIndexed,
            scr = scr,
            blockedReaders = blockedReaders,
            statsTime = statsTime,
            minuteCourse = minuteCourse,
            zenReaderUrl = zenReaderUrl,
            metrikaId = channelData.first,
            regTime = channelData.second
        )
    }

    fun appendStyledInformer(parent: HTMLElement, data: InformerData) {
        with(data) {
            parent.append {
                div("prozen-widget") {
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
                                        window.open(chrome.runtime.getURL("totalstats.html"))
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
                                        window.open(chrome.runtime.getURL("sadrobot.html"))
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
                        channelUnIndexed?.let {
                            div("prozen-widget-item") {
                                if (it) {
                                    title = "Обнаружен мета-тег <meta name=\"robots\" content=\"noindex\" />\n" +
                                            "Главная страница канала не индексируется поисковиками.\n" +
                                            "Это нормальная ситуация для новых каналов."
                                }
                                span("prozen-widget-item-title") {
                                    +"Индексация канала: "
                                }
                                span {
                                    +if (it) "Нет 🤖" else "Есть"
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
                                titleText += "\nПредыдущий курс (${previous.first}): ${previous.third.format(4)} ₽"
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
                                    +"${last.third.format(4)}₽"
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
    }

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
        // val menuButton = document.querySelector(".prozen-menu-button") as HTMLElement
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