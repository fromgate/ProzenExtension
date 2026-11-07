package publications.cards

import common.Card
import common.cards.CardExtra
import kotlinx.browser.document
import kotlinx.html.div
import kotlinx.html.dom.append
import kotlinx.html.dom.create
import kotlinx.html.span
import kotlinx.html.style
import org.w3c.dom.HTMLElement

/** Одно поле внутри слота. */
class CardFieldRef(
    val id: CardFieldId,
    val style: String? = null,
    val showIcon: Boolean = true,
    val iconTitle: Boolean = false,
    val textTitle: Boolean = false,
)

/**
 * Слот раскладки — строка дашборда, ячейка сетки или строка таблицы.
 * В одном слоте может быть несколько полей (например, «лайки | комментарии | репосты»).
 */
class CardSlot(
    val cssClass: String,
    val style: String? = null,
    val fields: List<CardFieldRef>,
    val titleOnWrapper: Boolean = false,
) {
    constructor(
        cssClass: String,
        field: CardFieldId,
        style: String? = null,
        titleOnWrapper: Boolean = false,
        showIcon: Boolean = true,
        iconTitle: Boolean = false,
        textTitle: Boolean = false,
        fieldStyle: String? = null,
    ) : this(
        cssClass = cssClass,
        style = style,
        fields = listOf(CardFieldRef(field, fieldStyle, showIcon, iconTitle, textTitle)),
        titleOnWrapper = titleOnWrapper,
    )
}

/** Контейнер раскладки — колонка дашборда или строка сетки. */
class CardContainer(
    val cssClass: String,
    val style: String? = null,
    val slots: List<CardSlot>,
)

/**
 * Зона расширения: сюда попадают видимые поля внешних модулей, которых нет
 * в базовом наборе раскладки. Внешние поля приходят как data-only [CardExtra].
 */
class CardExtensionZone(
    val containerClass: String,
    val slotClass: String,
    val containerStyle: String? = null,
    val slotStyle: String? = null,
)

/** Набор CSS-классов и поведенческих отличий конкретной раскладки. */
enum class CardLayoutKind(
    val iconClass: String,
    val textClass: String,
    val useShortUrl: Boolean,
    val originButton: Boolean,
) {
    DASHBOARD("prozen-card-icon", "prozen-card-text", useShortUrl = true, originButton = false),
    TABLE("prozen-card-icon", "prozen-card-text", useShortUrl = true, originButton = false),
    GRID("prozen-grid-card-icon", "prozen-grid-card-text", useShortUrl = false, originButton = true),
}

/**
 * Декларативное описание одной раскладки карточки.
 *
 * [rootClass] = null означает, что контейнеры рендерятся прямо в переданный
 * родительский элемент (табличный режим: каждая ячейка получает свою колонку).
 */
class CardComposition(
    val layout: CardLayoutKind,
    val containers: List<CardContainer>,
    val rootClass: String? = null,
    val rootStyle: String? = null,
    val extension: CardExtensionZone? = null,
    val extensionInLastContainer: Boolean = false,
) {
    fun render(
        card: Card,
        parent: HTMLElement,
        visibleIds: Set<String>,
        extras: List<CardExtra>,
    ) {
        val root: HTMLElement? = rootClass?.let { cssClass ->
            document.create.div(cssClass) {
                rootStyle?.let { style = it }
            }
        }
        val target = root ?: parent
        var lastContainer: HTMLElement? = null

        containers.forEach containerLoop@ { containerSpec ->
            val visibleSlots = containerSpec.slots.mapNotNull { slot ->
                val fields = slot.fields.filter { it.id.name in visibleIds }
                if (fields.isEmpty()) null else slot to fields
            }
            if (visibleSlots.isEmpty()) return@containerLoop

            val containerElement = createContainer(containerSpec)
            lastContainer = containerElement

            visibleSlots.forEach { (slot, fields) ->
                val slotElement = createSlot(slot)

                fields.forEach fieldLoop@ { fieldRef ->
                    val field = CardFields.registry.get(fieldRef.id.name) ?: return@fieldLoop
                    if (slot.titleOnWrapper) {
                        slotElement.title = field.titleFor(card)
                    }

                    CardRenderState.withOptions(
                        CardRenderOptions(
                            iconClass = layout.iconClass,
                            textClass = layout.textClass,
                            showIcon = fieldRef.showIcon,
                            iconTitle = fieldRef.iconTitle,
                            textTitle = fieldRef.textTitle,
                            iconStyle = fieldRef.style,
                            useShortUrl = layout.useShortUrl,
                            originButton = layout.originButton,
                        )
                    ) {
                        field.render(card, slotElement)
                    }
                }

                containerElement.appendChild(slotElement)
            }

            target.appendChild(containerElement)
        }

        renderExtension(target, lastContainer, extras)

        if (root != null) {
            parent.appendChild(root)
        }
    }

    private fun createContainer(container: CardContainer): HTMLElement =
        document.create.div(container.cssClass) {
            container.style?.let { style = it }
        }

    private fun createSlot(slot: CardSlot): HTMLElement =
        document.create.div(slot.cssClass) {
            slot.style?.let { style = it }
        }

    private fun renderExtension(
        target: HTMLElement,
        lastContainer: HTMLElement?,
        extras: List<CardExtra>,
    ) {
        val zone = extension ?: return
        if (extras.isEmpty()) return

        val containerElement = document.create.div(zone.containerClass) {
            zone.containerStyle?.let { style = it }
        }

        extras.forEach { extra ->
            val iconCssClass = extra.iconCssClass
            val text = extra.text
            // Поле без текста и иконки пропускается: пустых строк не остаётся.
            if (iconCssClass == null && text == null) return@forEach

            val slotElement = document.create.div(zone.slotClass) {
                zone.slotStyle?.let { style = it }
            }
            extra.title?.let { slotElement.title = it }

            slotElement.append {
                if (iconCssClass != null) {
                    span("${layout.iconClass} $iconCssClass")
                }
                if (text != null) {
                    span(layout.textClass) { +text }
                }
            }
            containerElement.appendChild(slotElement)
        }

        if (containerElement.childElementCount == 0) return

        val extensionTarget = if (extensionInLastContainer) lastContainer else target
        extensionTarget?.appendChild(containerElement)
    }
}
