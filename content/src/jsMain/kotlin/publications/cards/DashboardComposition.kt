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
import publications.cards.CardFieldId.TIME
import publications.cards.CardFieldId.VIEWS

/**
 * Карточки главной страницы студии.
 *
 * DOM повторяет прежний ручной рендер: четыре колонки, третья растянута,
 * в третьей колонке поля сгруппированы по две строки.
 */
object DashboardComposition {
    val composition = CardComposition(
        layout = CardLayoutKind.DASHBOARD,
        rootClass = "prozen-card-stats",
        rootStyle = "display: flex; flex-wrap: wrap; justify-content: space-between;",
        containers = listOf(
            CardContainer(
                cssClass = "prozen-card-column",
                slots = listOf(
                    CardSlot(
                        cssClass = "prozen-card-row",
                        field = SHOWS,
                        titleOnWrapper = true,
                    ),
                    CardSlot(
                        cssClass = "prozen-card-row",
                        field = VIEWS,
                        titleOnWrapper = true,
                    ),
                ),
            ),
            CardContainer(
                cssClass = "prozen-card-column",
                slots = listOf(
                    CardSlot(
                        cssClass = "prozen-card-row",
                        field = FULL_READS,
                        titleOnWrapper = true,
                    ),
                    CardSlot(
                        cssClass = "prozen-card-row",
                        field = SUBSCRIBER_VIEWS,
                        titleOnWrapper = true,
                    ),
                ),
            ),
            CardContainer(
                cssClass = "prozen-card-column",
                style = "flex-grow: 1.5;",
                slots = listOf(
                    CardSlot(
                        cssClass = "prozen-card-row",
                        fields = listOf(
                            CardFieldRef(LIKES, iconTitle = true, textTitle = true),
                            CardFieldRef(COMMENTS, style = "margin-left: 8px;", iconTitle = true, textTitle = true),
                            CardFieldRef(REPOSTS, style = "margin-left: 8px;", iconTitle = true, textTitle = true),
                        ),
                    ),
                    CardSlot(
                        cssClass = "prozen-card-row",
                        fields = listOf(
                            CardFieldRef(SUBSCRIPTIONS, iconTitle = true, textTitle = true),
                            CardFieldRef(ER, style = "margin-left: 8px;", iconTitle = true, textTitle = true),
                        ),
                    ),
                ),
            ),
            CardContainer(
                cssClass = "prozen-card-column",
                slots = listOf(
                    CardSlot(
                        cssClass = "prozen-card-row",
                        field = TIME,
                        titleOnWrapper = true,
                    ),
                    CardSlot(
                        cssClass = "prozen-card-row",
                        style = "justify-content: right;",
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
            containerClass = "prozen-card-column",
            slotClass = "prozen-card-row",
        ),
    )
}
