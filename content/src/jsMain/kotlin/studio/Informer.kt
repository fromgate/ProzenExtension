package studio

import common.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.datetime.Clock
import kotlinx.html.dom.append
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.js.a
import kotlinx.html.js.div
import kotlinx.html.js.span
import kotlinx.html.style
import kotlinx.html.title
import org.w3c.dom.HTMLElement
import kotlin.time.Duration.Companion.days

class Informer(val requester: Requester) {
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

    suspend fun appendInformer(parent: HTMLElement) {
        if (!Option.INFORMER.value().await()) return
        if (document.getElementById("prozen-informer") != null) return

        val data = getData()
        if (data.isNotNull()) {
            with(data) {
                parent.append {
                    div("editor--notifications-preview-block-desktop__block-39") {
                        id = "prozen-informer"
                        style = "margin-top: 24px;"
                        h3("editor--author-studio-section-title__title-uh Text Text_weight_medium Text_color_full Text_typography_headline-18-22 editor--author-studio-section-title__text-2P") {
                            title = "Добавлено расширением „Продзен“"
                            style = "margin-bottom: 10px;"
                            +"Продзен"
                        }
                        strikes?.let {
                            span("Text Text_typography_text-15-20 editor--notification__textWrapper-1- editor--notification__text-3k prozen-mb5-block") {
                                title = "Информация получена на основе данных раздела «Предупреждения»"
                                +"Предупреждения: $it"
                            }
                        }
                        channelLimited?.let {
                            span("Text Text_typography_text-15-20 editor--notification__textWrapper-1- editor--notification__text-3k prozen-mb5-block") {
                                title = "Информация получена на основе данных раздела «Предупреждения»"
                                +if (it) "Канал ограничен" else "Канал не ограничен"
                            }
                        }
                        channelUnIndexed?.let {
                            span("Text Text_typography_text-15-20 editor--notification__textWrapper-1- editor--notification__text-3k prozen-mb5-block") {
                                if (it) {
                                    title = "Обнаружен мета-тег <meta name=\"robots\" content=\"noindex\" />\n" +
                                            "Главная страница канала не индексируется поисковиками.\n" +
                                            "Это нормальная ситуация для новых каналов."
                                    +"Канал не индексируется 🤖"
                                } else {
                                    +"Канал индексируется 🤖"
                                }
                            }
                        }
                        scr?.let {
                            span("Text Text_typography_text-15-20 editor--notification__textWrapper-1- editor--notification__text-3k prozen-mb5-block") {
                                title = "Коэффициент охвата подписчиков (Subscribers Coverage Rate).\n" +
                                        "Показывает какая доля подписчиков видит карточки публикаций."
                                +"Охват подписчиков (SCR): ${it.format()}%"
                            }
                        }
                        blockedReaders?.let {
                            span("Text Text_typography_text-15-20 editor--notification__textWrapper-1- editor--notification__text-3k prozen-mb5-block") {
                                title = "Количество заблокированных комментаторов"
                                +"Заблокировано читателей: $it"
                            }
                        }
                        statsTime?.let {
                            span("Text Text_typography_text-15-20 editor--notification__textWrapper-1- editor--notification__text-3k prozen-mb5-block") {
                                title = "Время обновления статистики"
                                +"Статистика от: $it"
                            }
                        }

                        minuteCourse.lastNonZero()?.let { last ->
                            val previous = minuteCourse.previousToLastNonZero()
                            var titleText = "Стоимость минуты вовлечённого просмотра"
                            if (previous != null) {
                                titleText += "\nПредыдущий курс (${previous.first}): ${previous.third} ₽"
                                span("Text Text_typography_text-15-20 editor--notification__textWrapper-1- editor--notification__text-3k prozen-mb5-block") {
                                    title = titleText
                                    +"Курс минуты ${last.first}: ${last.third}₽"
                                }
                            }
                        }
                        zenReaderUrl?.let {
                            a {
                                href = it
                                span("Text Text_color_full Text_typography_text-14-18 editor--author-studio-article-card__title prozen-mb5-block") {
                                    title = "Ссылка для подписки на канал\nв телеграм-боте ZenReader"
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