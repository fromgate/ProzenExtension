package publications.cards

import publications.cards.CardFieldId.COMMENTS
import publications.cards.CardFieldId.ER
import publications.cards.CardFieldId.FULL_READS
import publications.cards.CardFieldId.LIKES
import publications.cards.CardFieldId.REPOSTS
import publications.cards.CardFieldId.SHOWS
import publications.cards.CardFieldId.SUBSCRIBER_VIEWS
import publications.cards.CardFieldId.SUBSCRIPTIONS
import publications.cards.CardFieldId.VIEWS

/**
 * Табличный режим страницы «Публикации».
 *
 * Раскладка привязана к фиксированным ячейкам `cells[3..6]`: каждая ячейка
 * получает свою колонку. Заголовки, время в `cells[1]` и сниппет остаются
 * нативными правками страницы в `Publications.kt`.
 */
object TableComposition {
    const val FIRST_CELL_INDEX = 3

    private val showsAndViews = CardComposition(
        layout = CardLayoutKind.TABLE,
        containers = listOf(
            CardContainer(
                cssClass = "prozen-card-column",
                style = "padding: 0px;",
                slots = listOf(
                    CardSlot(
                        cssClass = "prozen-card-row",
                        field = SHOWS,
                        style = "padding: 0px;",
                        titleOnWrapper = true,
                        showIcon = false,
                    ),
                    CardSlot(
                        cssClass = "prozen-card-row",
                        field = VIEWS,
                        style = "padding: 0px;",
                        titleOnWrapper = true,
                        showIcon = false,
                    ),
                ),
            ),
        ),
    )

    private val fullReadsAndSubscribers = CardComposition(
        layout = CardLayoutKind.TABLE,
        containers = listOf(
            CardContainer(
                cssClass = "prozen-card-column",
                slots = listOf(
                    CardSlot(
                        cssClass = "prozen-card-row",
                        field = FULL_READS,
                        titleOnWrapper = true,
                        showIcon = false,
                    ),
                    CardSlot(
                        cssClass = "prozen-card-row",
                        field = SUBSCRIBER_VIEWS,
                        titleOnWrapper = true,
                        showIcon = false,
                    ),
                ),
            ),
        ),
    )

    private val reactions = CardComposition(
        layout = CardLayoutKind.TABLE,
        containers = listOf(
            CardContainer(
                cssClass = "prozen-card-column",
                slots = listOf(
                    CardSlot(
                        cssClass = "prozen-card-row",
                        field = LIKES,
                        style = "justify-content: right;",
                        titleOnWrapper = true,
                    ),
                    CardSlot(
                        cssClass = "prozen-card-row",
                        field = COMMENTS,
                        style = "justify-content: right;",
                        titleOnWrapper = true,
                    ),
                    CardSlot(
                        cssClass = "prozen-card-row",
                        field = REPOSTS,
                        style = "justify-content: right;",
                        titleOnWrapper = true,
                    ),
                    CardSlot(
                        cssClass = "prozen-card-row",
                        field = SUBSCRIPTIONS,
                        style = "justify-content: right;",
                        titleOnWrapper = true,
                        textTitle = true,
                    ),
                ),
            ),
        ),
    )

    private val er = CardComposition(
        layout = CardLayoutKind.TABLE,
        containers = listOf(
            CardContainer(
                cssClass = "prozen-card-column",
                slots = listOf(
                    CardSlot(
                        cssClass = "prozen-card-row",
                        field = ER,
                        titleOnWrapper = true,
                        showIcon = false,
                    ),
                ),
            ),
        ),
        extension = CardExtensionZone(
            // Колонка, а не строка: несколько внешних полей должны идти
            // отдельными строками под ER.
            containerClass = "prozen-card-column",
            slotClass = "prozen-card-row",
            containerStyle = "padding: 0px;",
        ),
        extensionInLastContainer = true,
    )

    /** Композиции для `cells[3]`, `cells[4]`, `cells[5]`, `cells[6]` соответственно. */
    val cells: List<CardComposition> = listOf(showsAndViews, fullReadsAndSubscribers, reactions, er)
}
