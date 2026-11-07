package publications

import common.Card
import common.cards.CardExtra
import common.isOldFormat
import common.timeStr
import dataclasses.ServiceWorkerMessage
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.dom.clear
import org.w3c.dom.*
import org.w3c.dom.asList
import publications.cards.CardExtras
import publications.cards.CardVisibility
import publications.cards.DashboardComposition
import publications.cards.GridComposition
import publications.cards.TableComposition
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
        if (document.querySelector("[data-prozen-updated]") != null) return
        GlobalScope.launch {
            try {
                val visibleIds = CardVisibility.visibleIds()
                val cards = requester.getPublicationsByFilterAndSubscribers(pageSize).associateBy { it.id }
                val extras = CardExtras.load(cards.values.toList())
                val studioPublicationsBlock =
                    document.querySelector("div[class^=editor--last-publications__lastPublications-] > div > div")
                val publicationsBlocks = studioPublicationsBlock
                    ?.querySelectorAll("a")
                    ?.asList()
                    ?.filterIsInstance<HTMLElement>()
                publicationsBlocks?.forEach { publicationBlock ->
                    val id = getPublicationBlockId(publicationBlock) ?: return@forEach
                    val card = cards[id] ?: cards.values.firstOrNull { it.commonUrl == id }
                    card?.let {
                        updateStudioCardElement(publicationBlock, it, visibleIds, extras[it.id].orEmpty())
                    }
                }
                if (!publicationsBlocks.isNullOrEmpty()) {
                    studioPublicationsBlock.setAttribute("data-prozen-updated", "true")
                }
            } catch (e: Exception) {
                console.error("❌ Error in [processDashboardCards]: ${e.message}")
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

    private fun updateStudioCardElement(
        cardElement: HTMLElement,
        card: Card,
        visibleIds: Set<String>,
        extras: List<CardExtra>,
    ) {
        val statElement =
            cardElement.querySelector(".editor--dashboard-publication-item__publicationStat-1u") as? HTMLElement
                ?: return

        statElement.clear()
        DashboardComposition.composition.render(
            card = card,
            parent = statElement,
            visibleIds = visibleIds,
            extras = extras,
        )
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
            val visibleIds = CardVisibility.visibleIds()
            if (isPublicationGrid()) {
                modifyPublicationGrid(cards, visibleIds)
            } else {
                modifyPublicationTable(cards, visibleIds)
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
    private suspend fun modifyPublicationTable(cards: List<Card>, visibleIds: Set<String>) {
        if (isPublicationGrid()) return
        val extras = CardExtras.load(cards)
        modifyPublicationTable(cards, visibleIds, extras)
    }

    private fun modifyPublicationTable(
        cards: List<Card>,
        visibleIds: Set<String>,
        extras: Map<String, List<CardExtra>>,
    ) {
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
                val cardElement = getTableCellById(card.id) ?: card.commonUrl?.let { getTableCellByUrl(it) }
                if (cardElement == null) {
                    waitList.add(card)
                } else {
                    modifyTableRow(cardElement, card, visibleIds, extras[card.id].orEmpty())
                }
            }
        }
        if (waitList.isNotEmpty()) {
            window.setTimeout({
                modifyPublicationTable(waitList, visibleIds, extras)
            }, 300)
        }
    }

    private fun modifyTableRow(
        cell: HTMLElement,
        card: Card,
        visibleIds: Set<String>,
        extras: List<CardExtra>,
    ) {
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

        TableComposition.cells.forEachIndexed { index, composition ->
            rowCells.cells[index + TableComposition.FIRST_CELL_INDEX]?.let { tableCell ->
                val cellElement = tableCell as? HTMLElement ?: return@let
                cellElement.clear()
                composition.render(
                    card = card,
                    parent = cellElement,
                    visibleIds = visibleIds,
                    extras = extras,
                )
            }
        }
    }

    private fun getTableCellById(publicationId: String): HTMLElement? {
        val inner = document.querySelector(
            "td > a[href*='$publicationId'], " +
                    "td [data-testid='publication-cover'] > [style*='$publicationId']"
        )
        return inner?.closest("td") as? HTMLElement
    }
    private fun getTableCellByUrl(url: String): HTMLElement? =
        document.querySelector("td > a[href='$url']")?.parentElement as? HTMLElement


    /**
     *  Добавление расширенной статистики в карточном режиме страницы публикаций
     *
     *  @param cards List<Card> - список с данными карточек с расширенной статистикой
     *
     */
    private suspend fun modifyPublicationGrid(cards: List<Card>, visibleIds: Set<String>) {
        if (!isPublicationGrid()) return
        val extras = CardExtras.load(cards)
        modifyPublicationGrid(cards, visibleIds, extras)
    }

    private fun modifyPublicationGrid(
        cards: List<Card>,
        visibleIds: Set<String>,
        extras: Map<String, List<CardExtra>>,
    ) {
        val waitList = mutableListOf<Card>()
        cards.forEach { card ->
            if (!card.isOldFormat()) {
                val cellElement = getGridCellById(card.id) ?: card.commonUrl?.let { getGridCellByUrl(it) }
                if (cellElement == null) {
                    waitList.add(card)
                } else {
                    modifyGridCell(cellElement, card, visibleIds, extras[card.id].orEmpty())
                }
            }
        }
        if (waitList.isNotEmpty()) {
            window.setTimeout({
                modifyPublicationGrid(waitList, visibleIds, extras)
            }, 300)
        }
    }

    fun getGridCellById(publicationId: String): HTMLElement? {
        val CARD_SELECTOR = "[data-testid^='publication-card-']"
        val card = document.querySelector("[data-testid='publication-card-$publicationId']")
            ?: document.querySelector("a[data-testid='to-publication-link'][href*='$publicationId']")
                ?.closest(CARD_SELECTOR)
            ?: document.querySelector("[data-testid='publication-cover'] > [style*='$publicationId']")
                ?.closest(CARD_SELECTOR)
        return card as? HTMLElement
    }


    fun getGridCellByUrl(url: String): HTMLElement? {
        val a = document.querySelector("a[data-testid='to-publication-link'][href='$url']")
        return a?.parentElement as? HTMLElement
    }

    private fun modifyGridCell(
        cellElement: HTMLElement,
        card: Card,
        visibleIds: Set<String>,
        extras: List<CardExtra>,
    ) {
        if (cellElement.hasAttribute("data-prozen-publication-id")) return
        cellElement.setAttribute("data-prozen-publication-id", card.id)

        val date = cellElement.querySelector("span.Text_typography_text-12-16") as? HTMLElement
        date?.innerText = "${card.timeStr()} · "

        val statElement = cellElement.querySelector("div[class^=editor--stats__block]") as? HTMLElement
        statElement?.clear()
        statElement?.let {
            GridComposition.composition.render(
                card = card,
                parent = it,
                visibleIds = visibleIds,
                extras = extras,
            )
        }
    }
}
