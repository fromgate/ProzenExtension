package common

import kotlinx.coroutines.await
import kotlinx.datetime.*
import kotlin.js.Json
import kotlin.js.Promise
import kotlin.js.json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class Cache (val prefix: String){

    suspend fun <T> getOrLoadFromCache(
        cacheKey: String,
        expirationTime: Instant,
        loader: suspend () -> T?,
    ): T? {
        val cachedData = getFromCache(cacheKey) as? T
        if (cachedData != null) {
            return cachedData
        }
        val data = loader()
        if (data != null) {
            saveToCache(cacheKey, data, expirationTime)
        }
        return data
    }

    suspend fun getFromCache(key: String): Any? {
        val storageKey = "$prefix-$key"

        return Promise { resolve, _ ->
            chrome.storage.local.get(arrayOf(storageKey)) {
                val cacheItem = it[storageKey] as? Json
                if (cacheItem == null) {
                    resolve(null)
                    return@get
                }
                val data = cacheItem["data"]
                val timeStr = cacheItem["time"] as? String
                if (data == null || timeStr == null) {
                    resolve(null)
                    return@get
                }
                try {
                    val expirationTime = Instant.parse(timeStr)
                    val currentTime = Clock.System.now()
                    if (expirationTime > currentTime) {
                        resolve(data)
                    } else {
                        resolve(null)
                    }
                } catch (e: Exception) {
                    console.error("Error parsing cache for $key: ${e.message}")
                    resolve(null)
                }
            }
        }.await()
    }

    fun saveToCache(key: String, data: Any, expirationTime: Instant = calcExpirationTime()) {
        val dataObj = json(
            "data" to data,
            "time" to expirationTime.toString(),
        )
        chrome.storage.local.set(json("$prefix-$key" to dataObj))
    }

    fun calcExpirationTime(cacheLiveTime: Duration = 30.minutes, limitByMidnight: Boolean = false): Instant {
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