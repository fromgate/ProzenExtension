package studio

import ContentRunner
import common.Option
import common.Requester
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlinx.html.*
import kotlinx.html.div
import kotlinx.html.dom.create
import kotlinx.html.js.*
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import kotlin.js.json

class Menu(val requester: Requester): ContentRunner {
    var metrikaId: Int? = null
    suspend fun getData() {
        metrikaId = requester.getChannelData().first
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun create(count: Int = 0) {
        if (count == 4) return
        GlobalScope.launch {
            if (!Option.PROZEN_MENU.value().await()) return@launch
            if (document.documentElement!!.clientHeight < 777) return@launch
            val nabBar = document.querySelector("div[class^=editor--navbar__content]") as? HTMLElement
            if (nabBar != null) {
                getData()
                appendMenu(nabBar)
            } else {
                delay(500)
                create(count + 1)
            }
        }
    }

    fun appendMenu(nabBar: HTMLElement) {

        //editor--navbar__ul-1l

        var existedMenu = document.getElementById("prozen-main-menu")
        if (existedMenu != null && existedMenu.getAttribute("data-publisherId") != requester.publisherId!!) {
            existedMenu.parentNode!!.removeChild(existedMenu)
            existedMenu = null
        }

        if (existedMenu == null) {

            val lastUl = nabBar.getElementsByTagName("ul").asList()[1]
            val separator = document.create.div("editor--divider__divider-fu editor--navbar__divider-2z") {
                div("editor--divider__line-3f")
            }
            lastUl.after(separator)
            separator.after(
                document.create.ul(classes = "editor--navbar__ul-1l editor--navbar__ul-3_ prozen_navbar") {
                    id = "prozen-main-menu"
                    attributes["data-publisherid"] = requester.publisherId!!

                    // Первый элемент меню
                    li {
                        title = "Сводная статистика"
                        style = "cursor: pointer;"
                        a(classes = "editor--navbar__item-17") {
                            div(classes = "editor--navbar__icon-1d") {
                                div(classes = "editor--navbar__svg-2_ prozen_menu_stats")
                            }
                            span(classes = "Text Text_typography_text-15-20 editor--navbar__text-pc") {
                                +"Полная статистика"
                            }
                        }
                        onClickFunction = {
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

                    // Второй элемент меню
                    li {
                        title = "Просмотр статистики в Яндекс.Метрике"
                        style = "cursor: pointer;"
                        a(
                            classes = "editor--navbar__item-17",
                            href = "https://metrika.yandex.ru/dashboard?id=47122428",
                            target = "_blank"
                        ) {
                            div(classes = "editor--navbar__icon-1d") {
                                div(classes = "editor--navbar__svg-2_ prozen_menu_metrika")
                            }
                            span(classes = "Text Text_typography_text-15-20 editor--navbar__text-pc") {
                                +"Метрика"
                            }
                        }
                        onClickFunction = {
                            window.open(
                                "https://metrika.yandex.ru/list".takeIf {
                                    metrikaId == null
                                } ?: "https://metrika.yandex.ru/dashboard?id=$metrikaId"
                            )
                        }
                    }

                    // Третий элемент меню
                    li {
                        title = "Альтернативная функция поиска"
                        style = "cursor: pointer;"
                        a(classes = "editor--navbar__item-17") {
                            div(classes = "editor--navbar__icon-1d") {
                                div(classes = "editor--navbar__svg-2_ prozen_menu_search")
                            }
                            span(classes = "Text Text_typography_text-15-20 editor--navbar__text-pc") {
                                +"Поиск"
                            }
                        }
                        onClickFunction = {
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

                    // Четвертый элемент меню
                    li {
                        title = "Поиск проблемных публикаций"
                        style = "cursor: pointer;"
                        a(classes = "editor--navbar__item-17") {
                            div(classes = "editor--navbar__icon-1d") {
                                div(classes = "editor--navbar__svg-2_ prozen_menu_robot")
                            }
                            span(classes = "Text Text_typography_text-15-20 editor--navbar__text-pc") {
                                +"Проверка публикаций"
                            }
                        }
                        onClickFunction = {
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
                }
            )
        }
    }

    override fun run() {
        create()
    }
}

