package publications.cards

import common.Card
import common.comments
import common.copyTextToClipboard
import common.er
import common.fullReadsStrTitle
import common.likes
import common.originIcon
import common.reposts
import common.repostUrl
import common.shorUrl
import common.showsStr
import common.showNotification
import common.subscribersViewStr
import common.subscriptions
import common.timeCrateAndModStr
import common.url
import common.viewsStrAndTitle
import kotlinx.browser.window
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.title
import org.w3c.dom.HTMLElement

/**
 * Каталог базовых полей карточки. Рендеры перенесены из `Publications.kt`
 * без изменения состава DOM: иконка, текст, tooltip и обработчики кликов.
 */
internal fun registerBaseCardFields(registry: CardFieldRegistry) {
    registry.register(
        CardField(
            id = CardFieldId.SHOWS.name,
            kind = CardFieldKind.STAT,
            title = "Показы",
            render = { card, container ->
                renderIconAndText(container, "Показы", "prozen_studio_card_icon_shows", card.showsStr())
            },
        )
    )

    registry.register(
        CardField(
            id = CardFieldId.VIEWS.name,
            kind = CardFieldKind.STAT,
            title = "Просмотры",
            titleFor = { card -> card.viewsStrAndTitle().second },
            render = { card, container ->
                val views = card.viewsStrAndTitle()
                renderIconAndText(
                    container,
                    views.second,
                    "prozen_studio_card_icon_views",
                    views.first,
                )
            },
        )
    )

    registry.register(
        CardField(
            id = CardFieldId.FULL_READS.name,
            kind = CardFieldKind.STAT,
            title = "Дочитывания",
            titleFor = { card -> card.fullReadsStrTitle().second },
            render = { card, container ->
                val fullReads = card.fullReadsStrTitle()
                renderIconAndText(
                    container,
                    fullReads.second,
                    "prozen_studio_card_icon_full_views",
                    fullReads.first,
                )
            },
        )
    )

    registry.register(
        CardField(
            id = CardFieldId.SUBSCRIBER_VIEWS.name,
            kind = CardFieldKind.STAT,
            title = "Просмотры от подписчиков",
            render = { card, container ->
                renderIconAndText(
                    container,
                    "Просмотры от подписчиков",
                    "prozen_studio_card_icon_subscribers",
                    card.subscribersViewStr(),
                )
            },
        )
    )

    registry.register(
        CardField(
            id = CardFieldId.LIKES.name,
            kind = CardFieldKind.STAT,
            title = "Лайки",
            render = { card, container ->
                renderIconAndText(container, "Лайки", "prozen_studio_card_icon_like", card.likes())
            },
        )
    )

    registry.register(
        CardField(
            id = CardFieldId.COMMENTS.name,
            kind = CardFieldKind.STAT,
            title = "Комментарии",
            render = { card, container ->
                renderIconAndText(container, "Комментарии", "prozen_studio_card_icon_comments", card.comments())
            },
        )
    )

    registry.register(
        CardField(
            id = CardFieldId.REPOSTS.name,
            kind = CardFieldKind.STAT,
            title = "Репосты публикации",
            render = { card, container ->
                renderIconAndText(container, "Репосты публикации", "prozen_studio_card_icon_repost", card.reposts())
            },
        )
    )

    registry.register(
        CardField(
            id = CardFieldId.SUBSCRIPTIONS.name,
            kind = CardFieldKind.STAT,
            title = "Подписки с публикации",
            render = { card, container ->
                renderIconAndText(
                    container,
                    "Подписки с публикации",
                    "prozen_studio_cards_subscribers",
                    card.subscriptions(),
                )
            },
        )
    )

    registry.register(
        CardField(
            id = CardFieldId.ER.name,
            kind = CardFieldKind.STAT,
            title = "Коэффициент вовлечённости, ER",
            render = { card, container ->
                renderIconAndText(
                    container,
                    "Коэффициент вовлечённости, ER",
                    "prozen_studio_card_icon_er",
                    card.er(),
                )
            },
        )
    )

    registry.register(
        CardField(
            id = CardFieldId.TIME.name,
            kind = CardFieldKind.STAT,
            title = "Время создания",
            titleFor = { card ->
                val (createTime, modTime) = card.timeCrateAndModStr()
                "Время создания: $createTime" + if (modTime != null) "\nВремя редактирования: $modTime" else ""
            },
            render = { card, container -> renderTime(container, card) },
        )
    )

    registry.register(
        CardField(
            id = CardFieldId.ORIGIN.name,
            kind = CardFieldKind.STAT,
            title = "Источник импорта",
            render = { card, container -> renderOrigin(container, card) },
        )
    )

    registry.register(
        CardField(
            id = CardFieldId.ACTION_COPY_LINK.name,
            kind = CardFieldKind.ACTION,
            title = "Ссылка на публикацию",
            render = { card, container -> renderCopyLink(container, card) },
        )
    )

    registry.register(
        CardField(
            id = CardFieldId.ACTION_REPOST.name,
            kind = CardFieldKind.ACTION,
            title = "Репост публикации",
            render = { card, container -> renderRepost(container, card) },
        )
    )
}

/** Рисует стандартную пару «иконка + значение» по параметрам текущей раскладки. */
private fun renderIconAndText(container: HTMLElement, fieldTitle: String, iconCssClass: String, value: String) {
    val options = CardRenderState.options

    container.append {
        if (options.showIcon) {
            span("${options.iconClass} $iconCssClass") {
                if (options.iconTitle) title = fieldTitle
                options.iconStyle?.let { style = it }
            }
        }

        span(options.textClass) {
            if (options.textTitle) title = fieldTitle
            +value
        }
    }
}

private fun renderTime(container: HTMLElement, card: Card) {
    val options = CardRenderState.options
    val (createTime, modTime) = card.timeCrateAndModStr()

    container.append {
        if (options.showIcon) {
            span("${options.iconClass} prozen_studio_card_icon_clock")
        }
        span(options.textClass) { +createTime }
        if (modTime != null) {
            span("${options.iconClass} prodzen_studio_card_icon_edited") {
                style = "margin-left: 5px; width: 8px; height: 8px;"
            }
        }
    }
}

private fun renderOrigin(container: HTMLElement, card: Card) {
    val options = CardRenderState.options
    val originIcon = card.originIcon()

    if (originIcon == "prozen_studio_card_icon_platform") return

    // В дашборде иконка источника без рамки, в сетке — кнопка.
    val cssClass = if (options.originButton) "prozen-card-icon button $originIcon" else "prozen-card-icon $originIcon"

    container.append {
        span(cssClass) {
            title = "Публикация импортирована из ${if (card.origin == "rss") "RSS" else "Telegram"}"
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
private fun renderCopyLink(container: HTMLElement, card: Card) {
    val options = CardRenderState.options

    container.append {
        span("${options.iconClass} button prozen_studio_card_icon_link") {
            title = "Ссылка на публикацию\nНажмите, чтобы скопировать в буфер обмена"
            onClickFunction = {
                it.preventDefault()
                GlobalScope.launch {
                    copyTextToClipboard(if (options.useShortUrl) card.shorUrl() else card.url())
                    showNotification("Cсылка скопирована в буфер обмена")
                }
            }
        }
    }
}

private fun renderRepost(container: HTMLElement, card: Card) {
    val options = CardRenderState.options

    container.append {
        span("${options.iconClass} button prozen_studio_card_icon_repost") {
            title = "Нажмите, чтобы сделать репост"
            onClickFunction = {
                it.preventDefault()
                window.open(card.repostUrl(), "_blank")
            }
        }
    }
}
