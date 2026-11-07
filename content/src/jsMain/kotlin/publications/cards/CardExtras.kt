package publications.cards

import common.Card
import common.cards.CardExtra
import common.cards.CardExtraProvider

/**
 * Загрузчик внешних полей карточек.
 *
 * Провайдеры регистрируются один раз рядом с `CardFields.init()`. На каждую
 * пачку карточек загрузчик опрашивает всех провайдеров, объединяет результаты,
 * отбрасывает дубликаты по id (приоритет у первого провайдера) и сортирует
 * поля по `order`, затем по `id`.
 */
object CardExtras {
    private val providers = mutableListOf<CardExtraProvider>()

    fun init(externalProviders: List<CardExtraProvider>) {
        providers.clear()
        providers.addAll(externalProviders)
    }

    fun register(provider: CardExtraProvider) {
        providers.add(provider)
    }

    /**
     * Загружает внешние поля для пачки карточек.
     *
     * Ошибка одного провайдера логируется и не ломает рендер: остальные
     * провайдеры и базовые поля продолжают работать.
     */
    suspend fun load(cards: List<Card>): Map<String, List<CardExtra>> {
        if (cards.isEmpty() || providers.isEmpty()) return emptyMap()

        val cardIds = cards.mapTo(mutableSetOf()) { it.id }
        val merged = LinkedHashMap<String, MutableList<CardExtra>>()
        val seenIds = HashMap<String, MutableSet<String>>()

        for (provider in providers) {
            val provided = try {
                provider.extras(cards)
            } catch (error: Throwable) {
                console.error("Prozen: CardExtraProvider failed: ${error.message}")
                emptyMap()
            }

            for ((publicationId, extras) in provided) {
                if (publicationId !in cardIds) continue

                val ids = seenIds.getOrPut(publicationId) { mutableSetOf() }
                for (extra in extras) {
                    if (ids.add(extra.id)) {
                        merged.getOrPut(publicationId) { mutableListOf() }.add(extra)
                    }
                }
            }
        }

        return merged.mapValues { (_, extras) ->
            extras.sortedWith(compareBy<CardExtra>({ it.order }, { it.id }))
        }
    }
}
