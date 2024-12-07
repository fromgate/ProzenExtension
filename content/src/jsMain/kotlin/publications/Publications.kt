package publications

import common.*
import dataclasses.ServiceWorkerMessage
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.dom.clear
import kotlinx.html.div
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.title
import org.w3c.dom.*
import requester.Requester


@OptIn(DelicateCoroutinesApi::class)
class Publications(val requester: Requester) {


    /**
     *  Добавление расширенной статистики на карточки главной страницы
     *
     *  @param pageSize - количество карточек
     *
     */
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
                card?.let { updateStudioCardElement(publicationBlock, it) }
            }
        }
    }

    /**
     * Получить id публикации из html-блока карточки
     *
     * @param publicationBlock - html-блок, содержащий карточку публикации
     *
     * @return - идентификатор карточки или null
     */
    fun getPublicationBlockId(publicationBlock: HTMLElement): String? {
        val cover = publicationBlock.querySelector("div.editor--publication-cover__image-gr") as? HTMLElement
        var id: String? = null

        cover?.let {
            val url = it.style.backgroundImage.substring(4, it.style.backgroundImage.length - 1).replace("\"", "")
            id = url.split("_").getOrNull(2)
        }

        if (id == null && publicationBlock.hasAttribute("href")) {
            val href = publicationBlock.getAttribute("href") ?: return null
            val idArray = href.split("-")
            id = idArray.last()
        }
        return id
    }


    private fun updateStudioCardElement(cardElement: HTMLElement, card: Card) {
        val statElement =
            cardElement.querySelector(".editor--dashboard-publication-item__publicationStat-1u") as? HTMLElement
                ?: return

        statElement.clear()
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

                div("prozen-card-column") {
                    style = "flex-grow: 1.5;"
                    // Подписки
                    div("prozen-card-row") {
                        span("prozen-card-icon prozen_studio_card_icon_like") {
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

                div("prozen-card-column") {

                    val timeCrateAndModStr = card.timeCrateAndModStr()
                    val titleTime = "Время создания: ${timeCrateAndModStr.first}${
                        if (timeCrateAndModStr.second != null) "\nВремя редактирования: ${timeCrateAndModStr.second}" else ""
                    }"
                    div("prozen-card-row") {
                        title = titleTime
                        span("prozen-card-icon prozen_studio_card_icon_clock")
                        span("prozen-card-text") {
                            +timeCrateAndModStr.first
                        }
                        timeCrateAndModStr.second?.let {
                            span("prozen-card-icon prodzen_studio_card_icon_edited") {
                                style = "margin-left: 5px; width: 8px; height: 8px;"
                            }
                        }
                    }
                    div("prozen-card-row") {
                        style = "justify-content: right;"
                        span("prozen-card-icon button prozen_studio_card_icon_link") {
                            title = "Ссылка на публикацию\nНажмите, чтобы скопировать в буфер обмена"
                            onClickFunction = {
                                it.preventDefault()
                                GlobalScope.launch {
                                    copyTextToClipboard(card.shorUrl())
                                    showNotification("Cсылка скопирована в буфер обмена")
                                }
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


    // Публикации
    fun processPublicationsCards(serviceWorkerMessage: ServiceWorkerMessage) {
        GlobalScope.launch {
            val cards = requester.getPublicationsByFilterAndSubscribers(
                pageSize = serviceWorkerMessage.pageSize,
                types = serviceWorkerMessage.types,
                publicationIdAfter = serviceWorkerMessage.publicationIdAfter,
                view = serviceWorkerMessage.view,
                query = serviceWorkerMessage.query
            )
            if (isPublicationGrid()) {
                modifyPublicationGrid(cards)
            } else {
                modifyPublicationTable(cards)
            }
        }
    }

    private fun isPublicationGrid(): Boolean {
        val div = document.querySelector("table[class^=editor--publications-list]")
        return div == null
    }


    /**
     *  Добавление расширенной статистики в табличном режиме страницы публикаций
     *
     *  @param cards List<Card> - список с данными карточек с расширенной статистикой
     *
     */
    private fun modifyPublicationTable(cards: List<Card>) {
        if (isPublicationGrid()) return
        val waitList = mutableListOf<Card>()
        val tHead = document.querySelector("thead > tr") as HTMLTableRowElement
        val titles = listOf(
            "Показы, Просмотры",
            "Дочитывания, Просмотры подписчиков",
            "Лайки, Комментарии, Репосты, Подписки",
            "ER %"
        )
        titles.forEachIndexed() { index, title ->
            val span = tHead.cells[index + 3]?.querySelector("span") as? HTMLSpanElement
            span?.innerText = title
        }
        cards.forEach { card ->
            if (card.type !in listOf("post", "narrative", "story")) {
                val cardElement = getTableCellById(card.id)
                if (cardElement == null) {
                    waitList.add(card)
                } else {
                    modifyTableRow(cardElement, card)
                }
            }
        }
        if (waitList.isNotEmpty()) {
            window.setTimeout({
                modifyPublicationTable(waitList)
            }, 300)
        }
    }

    private fun modifyTableRow(cell: HTMLElement, card: Card) {
        if (cell.hasAttribute("data-prozen-publication-id")) return
        cell.setAttribute("data-prozen-publication-id", card.id)

        val snippet = cell.querySelector("p.editor--publication-preview__snippet-IX") as? HTMLElement
        if (snippet != null && card.type !in listOf("post", "gallery", "brief")) {
            snippet.style.setProperty("-webkit-line-clamp", "2")
        }


        val rowCells = (cell.parentNode as HTMLTableRowElement)
        val timeCell = rowCells.cells[1]
        timeCell?.querySelector("span")?.let {
            it.textContent = card.timeStr()
        }

        rowCells.cells[3]?.let {
            it.clear()
            it.append {
                div("prozen-card-column") {
                    style = "padding: 0px;"
                    // Количество показов
                    div("prozen-card-row") {
                        style = "padding: 0px;"
                        title = "Показы"
                        // span("prozen-card-icon prozen_studio_card_icon_shows")
                        span("prozen-card-text") { +card.showsStr() }
                    }

                    // Количество просмотров
                    val views = card.viewsStrAndTitle()
                    div("prozen-card-row") {
                        style = "padding: 0px;"
                        title = views.second
                        // span("prozen-card-icon prozen_studio_card_icon_views")
                        span("prozen-card-text") { +views.first }
                    }
                }
            }
        }

        rowCells.cells[4]?.let {
            it.clear()
            it.append {
                div("prozen-card-column") {
                    // Дочитываний
                    val viewsTillEnd = card.fullReadsStrTitle()
                    div("prozen-card-row") {
                        title = viewsTillEnd.second
                        // span("prozen-card-icon prozen_studio_card_icon_full_views") { } // Иконка дочитываний
                        span("prozen-card-text") { +viewsTillEnd.first } // Количество дочитываний
                    }

                    div("prozen-card-row") {
                        title = "Просмотры от подписчиков"
                        // span("prozen-card-icon prozen_studio_card_icon_subscribers") { } // Иконка дочитываний
                        span("prozen-card-text") { +card.subscribersViewStr() } // Количество дочитываний
                    }
                }
            }
        }

        rowCells.cells[5]?.let {
            it.clear()
            it.append {
                div("prozen-card-column") {
                    div("prozen-card-row") {
                        style = "justify-content: right;"
                        title = "Лайки"
                        span("prozen-card-icon prozen_studio_card_icon_like")
                        span("prozen-card-text") {
                            +card.likes()
                        }
                    }
                    div("prozen-card-row") {
                        style = "justify-content: right;"
                        title = "Комментарии"
                        span("prozen-card-icon prozen_studio_card_icon_comments")
                        span("prozen-card-text") {
                            +card.comments()
                        }
                    }

                    // Количество кликов
                    div("prozen-card-row") {
                        style = "justify-content: right;"
                        title = "Репосты публикации"
                        span("prozen-card-icon prozen_studio_card_icon_repost")
                        span("prozen-card-text") {
                            +card.reposts()
                        }
                    }
                    div("prozen-card-row") {
                        style = "justify-content: right;"
                        title = "Подписки с публикации"
                        span("prozen-card-icon prozen_studio_cards_subscribers")
                        span("prozen-card-text") {
                            title = "Подписки с публикации"
                            +card.subscriptions()
                        }
                    }
                }
            }
        }

        rowCells.cells[6]?.let {
            it.clear()
            it.append {
                div("prozen-card-column") {
                    div("prozen-card-row") {
                        title = "Коэффициент вовлечённости, ER"
                        span("prozen-card-text") {
                            +card.er()
                        }
                    }
                }
            }
        }
    }

    private fun getTableCellById(publicationId: String): HTMLElement? {
        val table = document.querySelector("table[class^=editor--publications-list]") as? HTMLElement
        val a = table?.querySelector("a[class^=editor--publication-preview][href*='$publicationId']") as? HTMLElement

        if (a != null) {
            return a.parentElement as HTMLElement
        }

        val div =
            table?.querySelector("div.editor--publication-cover__image-gr[style*='$publicationId']") as? HTMLElement

        return div?.parentElement?.parentElement?.parentElement?.parentElement as? HTMLElement
    }



    /**
     *  Добавление расширенной статистики в карточном режиме страницы публикаций
     *
     *  @param cards List<Card> - список с данными карточек с расширенной статистикой
     *
     */
    fun modifyPublicationGrid (cards: List<Card>) {
        if (!isPublicationGrid()) return
        val waitList = mutableListOf<Card>()
        cards.forEach { card ->
            if (!card.isOldFormat()) {
                val cellElement = getGridCellById(card.id)
                if (cellElement == null) {
                    waitList.add(card)
                } else {
                    modifyGridCell (cellElement, card)
                }
            }
        }
        if (waitList.isNotEmpty()) {
            window.setTimeout({
                modifyPublicationTable(waitList)
            }, 300)
        }
    }

    fun getGridCellById(publicationId: String): HTMLElement? {
        val a = document.querySelector("a.editor--publication-card__link-3k[href*='$publicationId'")
        if (a != null) return a.parentNode as HTMLElement
        val div = document.querySelector("div.editor--publication-cover__image-gr[style*='$publicationId'")
        val parentNode = div?.parentNode?.parentNode as? HTMLElement
        return parentNode?.querySelector("div.editor--publication-card__stats-1k") as? HTMLElement
    }

    fun modifyGridCell (cellElement: HTMLElement, card: Card) {
        if (cellElement.hasAttribute("data-prozen-publication-id")) return
        cellElement.setAttribute("data-prozen-publication-id", card.id)

        val date = cellElement.querySelector("span.Text_typography_text-12-16") as? HTMLElement
        date?.innerText = "${card.timeStr()} · "

        val statElement = cellElement.querySelector("div[class^=editor--stats__block]") as? HTMLElement
        statElement?.clear()
        val zIndex = 11
        statElement?.append {
            div("prozen-grid-card-stats") {
                // Показы  | Лайки / Коменты
                div("prozen-grid-card-row") {
                    div("prozen-grid-card-cell") {
                        title = "Показы"
                        span("prozen-grid-card-icon prozen_studio_card_icon_shows")
                        span("prozen-grid-card-text") { +card.showsStr() }
                    }
                    div("prozen-grid-card-cell") {
                        span("prozen-grid-card-icon prozen_studio_card_icon_like") {
                            title = "Лайки"
                        }
                        span("prozen-grid-card-text") {
                            title = "Лайки"
                            +card.likes()
                        }
                        span("prozen-grid-card-icon prozen_studio_card_icon_repost") {
                            title = "Репосты публикации"
                            style = "margin-left: 8px;"
                        }
                        span("prozen-grid-card-text") {
                            title = "Репосты публикации"
                            +card.reposts()
                        }
                    }
                }

                // Просмотры | Подписки / Коменты
                div("prozen-grid-card-row") {
                    div("prozen-grid-card-cell") {
                        val views = card.viewsStrAndTitle()
                        title = views.second
                        span("prozen-grid-card-icon prozen_studio_card_icon_views")
                        span("prozen-grid-card-text") { +views.first }
                    }
                    div("prozen-grid-card-cell") {
                        span("prozen-grid-card-icon prozen_studio_cards_subscribers") {
                            title = "Подписки с публикации"
                        }
                        span("prozen-grid-card-text") { +card.subscriptions() }
                        span("prozen-grid-card-icon prozen_studio_card_icon_comments") {
                            title = "Комментарии"
                        }
                        span("prozen-grid-card-text") { +card.comments() }
                    }
                }

                // Дочитывания | ER
                div("prozen-grid-card-row") {
                    div("prozen-grid-card-cell") {
                        val viewsTillEnd = card.fullReadsStrTitle()
                        title = viewsTillEnd.second
                        span("prozen-grid-card-icon prozen_studio_card_icon_full_views")
                        span("prozen-grid-card-text") { +viewsTillEnd.first }
                    }
                    div("prozen-grid-card-cell") {
                        span("prozen-grid-card-icon prozen_studio_card_icon_er") {
                            title = "Коэффициент вовлечённости, ER"
                        }
                        span("prozen-grid-card-text") { +card.er() }
                    }
                }

                div("prozen-grid-card-row") {
                    div("prozen-grid-card-cell") {
                        title = "Просмотры от подписчиков"
                        span("prozen-grid-card-icon prozen_studio_card_icon_subscribers")
                        span("prozen-grid-card-text") { +card.subscribersViewStr() }
                    }
                    div("prozen-grid-card-cell") {
                        span("prozen-grid-card-icon button prozen_studio_card_icon_link") {
                            title = "Ссылка на публикацию\nНажмите, чтобы скопировать в буфер обмена"
                            onClickFunction = {
                                it.preventDefault()
                                GlobalScope.launch {
                                    copyTextToClipboard(card.url())
                                    showNotification("Cсылка скопирована в буфер обмена")
                                }
                            }
                        }
                        span("prozen-grid-card-icon button prozen_studio_card_icon_repost") {
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