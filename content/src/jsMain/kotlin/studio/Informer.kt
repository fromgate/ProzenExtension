package studio

import common.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.html.dom.append
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.js.a
import kotlinx.html.js.div
import kotlinx.html.js.img
import kotlinx.html.js.span
import kotlinx.html.style
import kotlinx.html.title
import org.w3c.dom.HTMLElement
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
                if (count <=3) {
                    delay(500)
                    create(count + 1)
                } else {
                    console.log("Failed to find place for Prozen Informer")
                }
            }
        }
    }

    suspend fun getData(): InformerData {
        val date = Clock.System.now()
        val todayStr = date.toDateString()
        val before7 = date.minus(7.days)
        val before7Str = before7.toDateString()
        val before30 = date.minus(30.days)
        val before30Str = before30.toDateString()
        val channelUrl = window.location.href.replace("profile/editor/", "")
        val strikes = requester.getStrikesInfo()
        return InformerData(
            strikes = strikes?.second,
            channelLimited = strikes?.first,
            channelUnIndexed = checkNoIndexUrl(channelUrl),
            scr = requester.getScr(before30Str, todayStr),
            blockedReaders = requester.getBannedUsers(),
            statsTime = requester.getStatsActuality(),
            minuteCourse = requester.getTimespentRewards(before7Str, todayStr),
            zenReaderUrl = zenReaderUrl(channelUrl),
        )
    }

    fun appendStyledInformer(parent: HTMLElement, data: InformerData) {
        with(data) {
            parent.append {
                div("prozen-widget") {
                      div("prozen-widget-header") {
                        title = "Добавлено расширением «Продзен»"
                        div("prozen-header-content") {
                            img {
                                src = chrome.runtime.getURL("img/toast-logo.png")
                            }
                            span {
                                +"Информер"
                            }
                        }
                    }
                    div("prozen-widget-content") {
                        strikes?.let {
                            div("prozen-widget-item") {
                                title = "Информация получена на основе данных раздела «Предупреждения»"
                                span("prozen-widget-item-title") {
                                    +"Предупреждения: "
                                }
                                span(if (it > 0) "prozen-widget-warning" else null) {
                                    +it.toString()
                                }
                            }
                        }
                        channelLimited?.let {
                            div("prozen-widget-item") {
                                title = "Информация получена на основе данных раздела «Предупреждения»"
                                span("prozen-widget-item-title") {
                                    +"Канал ограничен: "
                                }
                                span(if (it) "prozen-widget-warning" else null) {
                                    +if (it) "Да" else "Нет"
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
                            title = "Коэффициент охвата подписчиков (Subscribers Coverage Rate).\n" +
                                    "Показывает какая доля подписчиков видит карточки публикаций."
                            div("prozen-widget-item") {
                                span("prozen-widget-item-title") {
                                    +"Охват подписчиков (SCR): "
                                }
                                span {
                                    +"${it.format()}%"
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
                                    +it.toString()
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
                                span(when {
                                    previous?.third == null -> null
                                    previous.third <= last.third -> "prozen-widget-success"
                                    else -> "prozen-widget-error"
                                }) {
                                    +"${last.third.format(4)}₽"
                                }
                            }
                        }
                        zenReaderUrl?.let {
                            div("prozen-widget-item") {
                                title = "Ссылка для подписки на канал\nв телеграм-боте ZenReader"
                                a(href = it, classes = "prozen-widget-link") {
                                    +"🔗 Подписка в ZenReader"
                                }
                            }
                        }
                    }
                }
            }
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
    val zenReaderUrl: String?
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