package publications.cards

import common.Card
import org.w3c.dom.HTMLElement

/**
 * Идентификаторы базовых полей карточки публикации.
 *
 * Внешние поля (дев-режим, премиальные модули) не попадают в этот enum:
 * они приходят как data-only `common.cards.CardExtra` и рендерятся в зоне
 * расширения раскладки.
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
 * @param isVisible предикат видимости базового поля; внешние поля решают видимость
 *        на стороне провайдера `CardExtra`
 */
class CardField(
    val id: String,
    val kind: CardFieldKind,
    val title: String,
    val render: (Card, HTMLElement) -> Unit,
    val titleFor: (Card) -> String = { title },
    val isVisible: suspend () -> Boolean = { true },
)
