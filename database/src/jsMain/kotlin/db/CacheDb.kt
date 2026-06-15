package common.db

import kotlinx.datetime.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class CacheDb (
    private val prefix: String,
    private val storeName: String = "generic_cache"
) {
    /**
     * Получить данные из кэша или загрузить их при отсутствии
     */
    suspend fun <T> getOrLoadFromCache(
        cacheKey: String,
        expirationTime: Instant,
        loader: suspend () -> T?,
    ): T? {
        try {
            val cachedData = getFromCache(cacheKey)
            if (cachedData != null) {
                return cachedData as T
            }
            val data = loader()
            if (data != null) {
                saveToCache(cacheKey, data, expirationTime)
            }
            return data
        } catch (e: Throwable) {
            console.error("Error with cache for $cacheKey: ${e.message}")
            return null
        }
    }

    /**
     * Получение одной записи с проверкой времени жизни
     */
    suspend fun getFromCache(key: String): Any? {
        val storageKey = "$prefix:$key"
        // Обращаемся напрямую к объекту-синглтону Db
        val item = Db.get(storeName, storageKey) ?: return null

        return try {
            val itemObj = item.asDynamic()
            val data = itemObj["data"]
            val timeStr = itemObj["time"] as? String ?: return null

            val expirationTime = Instant.parse(timeStr)
            if (expirationTime > Clock.System.now()) {
                data
            } else {
                delete(key)
                null
            }
        } catch (e: Throwable) {
            console.error("Error parsing cache item $key: ${e.message}")
            null
        }
    }

    /**
     * Сохранение записи в кэш
     */
    suspend fun saveToCache(key: String, data: Any, expirationTime: Instant = calcExpirationTime()) {
        val storageKey = "$prefix:$key"
        val dataObj = kotlin.js.json(
            "data" to data,
            "time" to expirationTime.toString()
        )
        Db.put(storeName, storageKey, dataObj) // Обращаемся напрямую к Db
    }

    /**
     * Получение списка объектов в диапазоне ключей
     */
    suspend fun getRangeFromCache(startKey: String, endKey: String): List<Any> {
        val fullStartKey = "$prefix:$startKey"
        val fullEndKey = "$prefix:$endKey"

        val rawItems = Db.getRange(storeName, fullStartKey, fullEndKey) // Обращаемся напрямую к Db
        val now = Clock.System.now()
        val results = mutableListOf<Any>()

        for (item in rawItems) {
            try {
                val itemObj = item.asDynamic()
                val data = itemObj["data"]
                val timeStr = itemObj["time"] as? String

                if (timeStr != null && Instant.parse(timeStr) > now) {
                    results.add(data)
                }
            } catch (e: Throwable) {
                console.error("Error parsing range item: ${e.message}")
            }
        }
        return results
    }

    /**
     * Удаление одной записи из кэша
     */
    suspend fun delete(key: String) {
        val storageKey = "$prefix:$key"
        Db.delete(storeName, storageKey) // Обращаемся напрямую к Db
    }

    /**
     * Полностью очистить кэш этого конкретного источника
     */
    suspend fun clearAll() {
        Db.deleteByPrefix(storeName, "$prefix:") // Обращаемся напрямую к Db
    }

    /**
     * Вспомогательный метод расчета времени жизни кэша
     */
    fun calcExpirationTime(
        cacheLiveTime: Duration = 30.minutes,
        limitByMidnight: Boolean = false
    ): Instant {
        val now = Clock.System.now()
        var expirationTime = now.plus(cacheLiveTime)

        if (limitByMidnight) {
            val midnight = now.toLocalDateTime(TimeZone.currentSystemDefault())
                .date
                .plus(1, DateTimeUnit.DAY)
                .atStartOfDayIn(TimeZone.currentSystemDefault())

            if (expirationTime > midnight) {
                expirationTime = midnight
            }
        }
        return expirationTime
    }
}