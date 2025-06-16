package studio

import ContentRunner
import common.Option
import common.dLog
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlinx.html.*
import kotlinx.html.dom.create
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.ul
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import requester.Requester
import kotlin.js.json

class Menu(val requester: Requester) : ContentRunner {

    var metrikaId: Int? = null

    suspend fun getData() {
        metrikaId = try {
            requester.getChannelData().first
        } catch (e: Exception) {
            console.error("Menu / getData Failed to get channelData", e)
            console.error(e)
            null
        }
    }

    fun getMetrikaUrl(): String = "https://metrika.yandex.ru/list".takeIf {
        metrikaId == null
    } ?: "https://metrika.yandex.ru/dashboard?id=$metrikaId"

    @OptIn(DelicateCoroutinesApi::class)
    fun create(count: Int = 0) {
        console.dLog("Menu / create : count: $count")
        if (count == 4) return
        GlobalScope.launch {
            if (!Option.PROZEN_MENU.value().await()) return@launch
            if (document.documentElement!!.clientHeight < 777) return@launch
            val navBar = document.querySelector("div[class^=editor--navbar__content]") as? HTMLElement
            if (navBar != null) {
                getData()
                appendMenu(navBar)
            } else {
                console.dLog("Studio / Menu: navBar is null")
                delay(500)
                create(count + 1)
            }
        }
    }

    fun appendMenu(nabBar: HTMLElement) {
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
                                +"Статистика"
                            }
                        }
                        onClickFunction = {
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

                    // Второй элемент меню
                    li {
                        title = "Просмотр статистики в Яндекс.Метрике"
                        style = "cursor: pointer;"
                        a(
                            classes = "editor--navbar__item-17"
                        ) {
                            div(classes = "editor--navbar__icon-1d") {
                                div(classes = "editor--navbar__svg-2_ prozen_menu_metrika")
                            }
                            span(classes = "Text Text_typography_text-15-20 editor--navbar__text-pc") {
                                +"Метрика"
                            }
                        }
                        onClickFunction = {
                            window.open(getMetrikaUrl())
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

                    li {
                        title = "Проверка состояния публикаций"
                        style = "cursor: pointer;"
                        a(classes = "editor--navbar__item-17") {
                            div(classes = "editor--navbar__icon-1d") {
                                div(classes = "editor--navbar__svg-2_ prozen_menu_robot")
                            }
                            span(classes = "Text Text_typography_text-15-20 editor--navbar__text-pc") {
                                +"Проверка"
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
                                window.open(chrome.runtime.getURL("status.html"))
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