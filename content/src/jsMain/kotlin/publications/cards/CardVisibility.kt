package publications.cards

/**
 * Резолвер видимости полей карточки.
 *
 * Подготовительный этап: все поля видны. Позже здесь появится пакетное чтение
 * опций (`CardFieldId -> Option`) и предикаты внешних полей.
 */
object CardVisibility {
    suspend fun isVisible(field: CardField): Boolean = field.isVisible()

    /** Пакетно вычисляет видимые поля, чтобы не читать опции отдельно для каждой карточки. */
    suspend fun visibleIds(): Set<String> = CardFields.registry.all().mapNotNull { field ->
        if (isVisible(field)) field.id else null
    }.toSet()
}
