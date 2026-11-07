package publications.cards

import common.Card
import org.w3c.dom.HTMLElement

/**
 * Идентификаторы базовых полей карточки публикации.
 *
 * Для внешних полей (дев-режим, премиальные модули) идентификатор задаётся
 * строкой, например `"dev.api-diff"`.
 */
enum class CardFieldId {
    SHOWS,
    VIEWS,
    FULL_READS,
    SUBSCRIBER_VIEWS,
    LIKES,
    COMMENTS,
    REPOSTS,
    SUBSCRIPTIONS,
    ER,
    TIME,
    ORIGIN,
    ACTION_COPY_LINK,
    ACTION_REPOST,
}

/** Тип поля: обычная статистика или кнопка-действие. */
enum class CardFieldKind { STAT, ACTION }

/**
 * Декларативное описание одного отображаемого элемента карточки.
 *
 * @param id уникальный идентификатор поля; для базовых полей совпадает с [CardFieldId.name]
 * @param kind тип поля
 * @param title статичный tooltip поля (используется для элементов с иконкой/текстом)
 * @param render синхронный рендер поля в переданный контейнер; асинхронные данные
 *        запускаются внутри через `GlobalScope.launch`
 * @param titleFor динамический tooltip (например, для просмотров он зависит от типа публикации)
 * @param isVisible предикат видимости для внешних полей; базовые поля видны всегда
 *        (в будущем решение принимает [CardVisibility])
 */
class CardField(
    val id: String,
    val kind: CardFieldKind,
    val title: String,
    val render: (Card, HTMLElement) -> Unit,
    val titleFor: (Card) -> String = { title },
    val isVisible: suspend () -> Boolean = { true },
)
