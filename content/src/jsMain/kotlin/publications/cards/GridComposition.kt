package publications.cards

import publications.cards.CardFieldId.ACTION_COPY_LINK
import publications.cards.CardFieldId.ACTION_REPOST
import publications.cards.CardFieldId.COMMENTS
import publications.cards.CardFieldId.ER
import publications.cards.CardFieldId.FULL_READS
import publications.cards.CardFieldId.LIKES
import publications.cards.CardFieldId.ORIGIN
import publications.cards.CardFieldId.REPOSTS
import publications.cards.CardFieldId.SHOWS
import publications.cards.CardFieldId.SUBSCRIBER_VIEWS
import publications.cards.CardFieldId.SUBSCRIPTIONS
import publications.cards.CardFieldId.VIEWS

/**
 * Сеточный режим страницы «Публикации».
 *
 * DOM повторяет прежний ручной рендер: четыре строки по две ячейки,
 * в правой ячейке первой строки — «лайки | репосты» и т.д.
 */
object GridComposition {
    val composition = CardComposition(
        layout = CardLayoutKind.GRID,
        rootClass = "prozen-grid-card-stats",
        rootStyle = "z-index: 10;",
        containers = listOf(
            CardContainer(
                cssClass = "prozen-grid-card-row",
                slots = listOf(
                    CardSlot(
                        cssClass = "prozen-grid-card-cell",
                        field = SHOWS,
                        titleOnWrapper = true,
                    ),
                    CardSlot(
                        cssClass = "prozen-grid-card-cell",
                        fields = listOf(
                            CardFieldRef(LIKES, iconTitle = true, textTitle = true),
                            CardFieldRef(REPOSTS, style = "margin-left: 8px;", iconTitle = true, textTitle = true),
                        ),
                    ),
                ),
            ),
            CardContainer(
                cssClass = "prozen-grid-card-row",
                slots = listOf(
                    CardSlot(
                        cssClass = "prozen-grid-card-cell",
                        field = VIEWS,
                        titleOnWrapper = true,
                    ),
                    CardSlot(
                        cssClass = "prozen-grid-card-cell",
                        fields = listOf(
                            CardFieldRef(SUBSCRIPTIONS, iconTitle = true),
                            CardFieldRef(COMMENTS, iconTitle = true),
                        ),
                    ),
                ),
            ),
            CardContainer(
                cssClass = "prozen-grid-card-row",
                slots = listOf(
                    CardSlot(
                        cssClass = "prozen-grid-card-cell",
                        field = FULL_READS,
                        titleOnWrapper = true,
                    ),
                    CardSlot(
                        cssClass = "prozen-grid-card-cell",
                        fields = listOf(
                            CardFieldRef(ER, iconTitle = true),
                        ),
                    ),
                ),
            ),
            CardContainer(
                cssClass = "prozen-grid-card-row",
                slots = listOf(
                    CardSlot(
                        cssClass = "prozen-grid-card-cell",
                        field = SUBSCRIBER_VIEWS,
                        titleOnWrapper = true,
                    ),
                    CardSlot(
                        cssClass = "prozen-grid-card-cell",
                        fields = listOf(
                            CardFieldRef(ORIGIN),
                            CardFieldRef(ACTION_COPY_LINK),
                            CardFieldRef(ACTION_REPOST),
                        ),
                    ),
                ),
            ),
        ),
        extension = CardExtensionZone(
            // Вертикальный контейнер: несколько внешних полей должны идти
            // отдельными строками внизу карточки.
            containerClass = "prozen-grid-card-stats",
            slotClass = "prozen-grid-card-cell",
            slotStyle = "justify-content: left;",
        ),
    )
}
