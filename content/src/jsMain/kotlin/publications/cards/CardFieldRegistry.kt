package publications.cards

/**
 * Реестр полей карточки.
 *
 * Один контент-скрипт на страницу, поэтому экземпляр в [CardFields] можно
 * использовать как синглтон. Повторная регистрация поля с тем же id игнорируется:
 * базовые поля не могут быть перекрыты внешним модулем.
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
 * Точка инициализации каталога полей.
 *
 * Вызывается один раз рядом с `installDevValidator()`. Внешние модули
 * подключаются через [externalProviders]: каждая функция регистрирует свои поля.
 *
 * Пример будущего подключения:
 * ```
 * CardFields.init(listOf(::registerCardFields))
 * ```
 */
object CardFields {
    val registry = CardFieldRegistry()

    private var initialized = false

    fun init(externalProviders: List<(CardFieldRegistry) -> Unit> = emptyList()) {
        if (initialized) return
        initialized = true
        registerBaseCardFields(registry)
        externalProviders.forEach { it(registry) }
    }

    /**
     * Поля внешних модулей, которые нужно показать в зоне расширения раскладки.
     * Базовые поля сюда не попадают: их место определяет композиция раскладки.
     */
    fun extensionFields(visibleIds: Set<String>): List<CardField> {
        val baseIds = CardFieldId.entries.map { it.name }.toSet()
        return registry.all().filter { it.id !in baseIds && it.id in visibleIds }
    }
}
