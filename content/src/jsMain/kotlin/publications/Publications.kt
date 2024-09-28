package publications

import common.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.dom.clear
import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList


class Publications(val requester: Requester) {


    fun processDashboardCards(pageSize: Int) {
        GlobalScope.launch {
            val cards = requester.getPublicationsByFilterAndSubscribers(pageSize).associateBy { it.id }
            val studioPublicationsBlock =
                document.querySelector("div[class^=editor--last-publications__lastPublications-] > div > div")
            val publicationsBlocks = studioPublicationsBlock
                ?.querySelectorAll("a")
                ?.asList()
                ?.filterIsInstance<HTMLElement>()
            publicationsBlocks?.forEach { publicationBlock ->
                val id = getPublicationBlockId(publicationBlock)
                val card = id?.let { cards[it] }
                card?.let { updateWideCardElement (publicationBlock, it) }
            }
        }
    }

    fun getPublicationBlockId(publicationBlock: HTMLElement): String? {
        val cover = publicationBlock.querySelector("div.editor--publication-cover__image-gr") as? HTMLElement
        var id: String? = null

        cover?.let {
            val url = it.style.backgroundImage.substring(4, it.style.backgroundImage.length - 1).replace("\"", "")
            id = url.split("_")[2]
        }

        if (id == null && publicationBlock.hasAttribute("href")) {
            val href = publicationBlock.getAttribute("href") ?: return null
            val idArray = href.split("-")
            id = idArray.last()
        }
        return id
    }


    fun updateWideCardElement(cardElement: HTMLElement, card: Card) {
        // Найти элемент статистики публикации внутри карточки
        val statElement = cardElement.querySelector(".editor--dashboard-publication-item__publicationStat-1u") as? HTMLElement
            ?: return // Если элемент статистики не найден, выйти

        // Очистить текущую статистику
        statElement.clear()

        // Добавить новые элементы статистики с использованием Flexbox для колонок
        statElement.append {
            div("prozen-card-stats") {
                style = "display: flex; flex-wrap: wrap; justify-content: space-between;"
                // Первый столбец
                div("prozen-card-column") {
                    // Количество показов
                    div("prozen-card-row") {
                        title = "Показы"
                        span("prozen-card-icon prozen_studio_card_icon_shows")
                        span("prozen-card-text") { +card.showsStr() }
                    }

                    // Количество просмотров
                    val views = card.viewsStrAndTitle()
                    div("prozen-card-row") {
                        title = views.second
                        span("prozen-card-icon prozen_studio_card_icon_views")
                        span("prozen-card-text") { +views.first }
                    }
                }

                // Второй столбец
                div("prozen-card-column") {

                    // Дочитываний
                    val viewsTillEnd = card.fullReadsStrTitle()
                    div("prozen-card-row") {
                        title = viewsTillEnd.second
                        span("prozen-card-icon prozen_studio_card_icon_full_views") { } // Иконка дочитываний
                        span("prozen-card-text") { +viewsTillEnd.first } // Количество дочитываний
                    }

                    div("prozen-card-row") {
                        title = "Просмотры от подписчиков"
                        span("prozen-card-icon prozen_studio_card_icon_subscribers") { } // Иконка дочитываний
                        span("prozen-card-text") { +card.subscribersViewStr() } // Количество дочитываний
                    }
                }

                // Третий столбец
                // лайки, коменты
                // репосты, подписки
                div("prozen-card-column") {
                    style = "flex-grow: 1.5;"
                    // Подписки
                    div("prozen-card-row") {
                        span("prozen-card-icon prozen_studio_card_icon_like"){
                            title = "Лайки"
                        }
                        span("prozen-card-text") {
                            title = "Лайки"
                            +card.likes()
                        }
                        span("prozen-card-icon prozen_studio_card_icon_comments") {
                            title = "Комментарии"
                            style = "margin-left: 8px;"
                        }
                        span("prozen-card-text") {
                            title = "Комментарии"
                            +card.comments()
                        }
                        span("prozen-card-icon prozen_studio_card_icon_repost") {
                            title = "Репосты публикации"
                            style = "margin-left: 8px;"
                        }
                        span("prozen-card-text") {
                            title = "Репосты публикации"
                            +card.reposts()
                        }
                    }

                    // Количество кликов
                    div("prozen-card-row") {
                        span("prozen-card-icon prozen_studio_cards_subscribers") {
                            title = "Подписки с публикации"
                        }
                        span("prozen-card-text") {
                            title = "Подписки с публикации"
                            +card.subscriptions()
                        }
                        span("prozen-card-icon prozen_studio_card_icon_er") {
                            title = "Коэффициент вовлечённости, ER"
                            style = "margin-left: 8px;"
                        }
                        span("prozen-card-text") {
                            title = "Коэффициент вовлечённости, ER"
                            +card.er()
                        }
                    }
                }

                // Четвертый столбец (если необходимо добавить больше данных)
                div("prozen-card-column") {
                    // Лайки
                    div("prozen-card-row") {
                        title = "Время публикации (время редактирования)"
                        span("prozen-card-icon prozen_studio_card_icon_clock")

                        val timeStrTitle = card.timeStrTitle()

                        span("prozen-card-text") {
                            title = timeStrTitle.second
                            +timeStrTitle.first
                        }
                    }
                    div("prozen-card-row") {
                        style ="justify-content: right;"
                        span("prozen-card-icon button prozen_studio_card_icon_link") {
                            title = "Ссылка на публикацию\nНажмите, чтобы скопировать в буфер обмена"
                            onClickFunction = {
                                it.preventDefault()
                                copyTextToClipboard(card.url())
                                showNotification("Cсылка скопирована в буфер обмена")
                            }
                        }
                        span("prozen-card-icon button prozen_studio_card_icon_repost") {
                            title = "Нажмите, чтобы сделать репост"
                            onClickFunction = {
                                it.preventDefault()
                                window.open(card.repostUrl(), "_blank")
                            }
                        }
                    }
                }
            }
        }
    }



}