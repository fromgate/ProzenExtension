package common.cards

import common.Card

/**
 * Данные внешнего поля карточки публикации.
 *
 * Контракт намеренно data-only: внешний модуль не получает DOM и не рисует
 * карточку сам. Всё, что нужно для отображения, описано полями этого класса,
 * а рендер выполняет `content` в зоне расширения текущей раскладки.
 *
 * @param id уникальный namespaced-идентификатор, например `dev.status` или `premium.plan`
 * @param text готовый текст; `null` — текста нет
 * @param iconCssClass CSS-класс иконки; `null` — иконки нет
 * @param title tooltip/полное значение
 * @param order порядок вывода: меньшее значение — выше
 */
data class CardExtra(
    val id: String,
    val text: String? = null,
    val iconCssClass: String? = null,
    val title: String? = null,
    val order: Int = 0,
)

/**
 * Batch-провайдер внешних полей карточки.
 *
 * Получает пачку карточек и возвращает данные по id публикации. Batch-формат
 * оставляет место для будущих источников (в том числе сетевых): провайдер сам
 * отвечает за запрос, кэш и таймауты.
 *
 * Провайдер может быть suspend: загрузчик `content` опрашивает провайдеров
 * до рендера карточек.
 */
interface CardExtraProvider {
    suspend fun extras(cards: List<Card>): Map<String, List<CardExtra>>
}
