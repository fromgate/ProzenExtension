package common

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.Json
import kotlin.js.json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class Cache(private val prefix: String) {

    suspend fun <T> getOrLoadFromCache(
        cacheKey: String,
        expirationTime: Instant,
        loader: suspend () -> T?,
    ): T? {
        try {
            val cachedData = getFromCache(cacheKey) as? T
            if (cachedData != null) {
                return cachedData
            }
            val data = loader()
            if (data != null) {
                saveToCache(cacheKey, data, expirationTime)
            }
            return data
        } catch (e: Throwable) {
            console.error("Error parsing cache for $cacheKey: ${e.message}")
            return null
        }
    }

    suspend fun getFromCache(key: String): Any? = suspendCancellableCoroutine { cont ->
        val storageKey = "$prefix-$key"
        try {
            chrome.storage.local.get(arrayOf(storageKey)) { result ->
                val cacheItem = result[storageKey] as? Json
                if (cacheItem == null) {
                    cont.resume(null)
                    return@get
                }

                val data = cacheItem["data"]
                val timeStr = cacheItem["time"] as? String

                if (data == null || timeStr == null) {
                    cont.resume(null)
                    return@get
                }

                try {
                    val expirationTime = Instant.parse(timeStr)
                    val currentTime = Clock.System.now()

                    if (expirationTime > currentTime) {
                        cont.resume(data)
                    } else {
                        cont.resume(null)
                    }
                } catch (e: Throwable) {
                    console.error("Error parsing cache for $key: ${e.message}")
                    cont.resume(null)
                }
            }
        } catch (e: Throwable) {
            cont.resumeWithException(e)
        }
    }

    fun saveToCache(key: String, data: Any, expirationTime: Instant = calcExpirationTime()) {
        val dataObj = json(
            "data" to data,
            "time" to expirationTime.toString(),
        )
        chrome.storage.local.set(json("$prefix-$key" to dataObj))
    }

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