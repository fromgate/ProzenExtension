package publications.cards

/**
 * Реестр базовых полей карточки.
 *
 * Один контент-скрипт на страницу, поэтому экземпляр в [CardFields] можно
 * использовать как синглтон. Повторная регистрация поля с тем же id игнорируется:
 * базовые поля не могут быть перекрыты.
 */
class CardFieldRegistry {
    private val fields = LinkedHashMap<String, CardField>()

    fun register(field: CardField) {
        if (!fields.containsKey(field.id)) {
            fields[field.id] = field
        }
    }

    fun get(id: String): CardField? = fields[id]

    fun all(): List<CardField> = fields.values.toList()
}

/**
 * Точка инициализации каталога базовых полей.
 *
 * Вызывается один раз рядом с `installDevValidator()`. Внешние модули сюда
 * не регистрируются: их поля приходят как data-only `CardExtra` через
 * [CardExtras].
 */
object CardFields {
    val registry = CardFieldRegistry()

    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true
        registerBaseCardFields(registry)
    }
}
