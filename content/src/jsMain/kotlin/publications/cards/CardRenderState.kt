package publications.cards

/**
 * Параметры, которые раскладка передаёт базовому рендеру поля.
 *
 * Рендер синхронный и однопоточный, поэтому параметры текущего поля хранятся
 * в [CardRenderState] и действуют только на время вызова `CardField.render`.
 */
internal class CardRenderOptions(
    val iconClass: String,
    val textClass: String,
    val showIcon: Boolean,
    val iconTitle: Boolean,
    val textTitle: Boolean,
    val iconStyle: String?,
    val useShortUrl: Boolean,
    val originButton: Boolean,
)

internal object CardRenderState {
    var options = CardRenderOptions(
        iconClass = "prozen-card-icon",
        textClass = "prozen-card-text",
        showIcon = true,
        iconTitle = false,
        textTitle = false,
        iconStyle = null,
        useShortUrl = true,
        originButton = false,
    )

    fun withOptions(options: CardRenderOptions, block: () -> Unit) {
        val previous = this.options
        this.options = options
        try {
            block()
        } finally {
            this.options = previous
        }
    }
}
