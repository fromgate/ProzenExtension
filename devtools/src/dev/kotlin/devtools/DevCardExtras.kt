package devtools

import common.Card
import common.Option
import common.cards.CardExtra
import common.cards.CardExtraProvider

/**
 * Dev-поле `dev.status`: статус публикации из ответа API.
 *
 * Поле видно только при включённом `Option.DEBUG`. В остальное время провайдер
 * возвращает пустую карту, поэтому зона расширения карточек не создаётся.
 */
object DevStatusCardExtraProvider : CardExtraProvider {
    override suspend fun extras(cards: List<Card>): Map<String, List<CardExtra>> {
        if (!Option.DEBUG.isSet()) return emptyMap()

        return cards.associate { card ->
            card.id to listOf(
                CardExtra(
                    id = "dev.status",
                    text = "status: ${card.status ?: "null"}",
                    title = "Статус публикации",
                )
            )
        }
    }
}

/**
 * Провайдеры внешних полей dev-сборки.
 *
 * В release-сорс-сете эта функция возвращает пустой список, поэтому dev-поля
 * и их текст физически отсутствуют в релизном бандле.
 */
fun devCardExtraProviders(): List<CardExtraProvider> = listOf(DevStatusCardExtraProvider)
