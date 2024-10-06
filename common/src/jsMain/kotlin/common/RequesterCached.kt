package common

import kotlinx.coroutines.await
import kotlinx.datetime.*
import kotlin.js.Json
import kotlin.js.Promise
import kotlin.js.json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE", "UNCHECKED_CAST")
class RequesterCached(publisherId: String?, token: String?) : Requester(publisherId, token) {

    override suspend fun getScr(from: String, to: String): Double? {
        return getOrLoadFromCache("getScr", calcExpirationTime(3.hours, true)) {
            super.getScr(from, to)
        }
    }

    override suspend fun getBannedUsers(): Int? {
        return getOrLoadFromCache("getBannedUsers", calcExpirationTime(3.hours)) {
            super.getBannedUsers()
        }
    }

    override suspend fun getStatsActuality(): String? {
        return getOrLoadFromCache("getStatsActuality", calcExpirationTime(10.minutes)) {
            super.getStatsActuality()
        }
    }

    override suspend fun getTimespentRewards(from: String, to: String): List<Triple<String, Double, Double>> {
        var rewards = (getFromCache("getTimespentRewards") as? Array<Json>)?.run {
            this.map { Triple(it["1"] as String, it["2"] as Double, it["3"] as Double) }
        }

        if (rewards == null) {
            rewards = super.getTimespentRewards(from, to)
            saveToCache(
                "getTimespentRewards",
                rewards.map {
                    json("1" to it.first, "2" to it.second, "3" to it.third)
                }.toTypedArray(),
                calcExpirationTime(30.minutes)
            )
        }
        return rewards
    }

    override suspend fun getChannelData(): Pair<Int?, Instant?> {
        var channelData: Pair<Int?, Instant?>? = (getFromCache("getChannelData") as? Json)?.run {
            val metrikaCounterId = this["1"] as? Int?
            val regTime = (this["2"] as? String?)?.parseInstant()
            if (regTime != null) {
                Pair(metrikaCounterId, regTime)
            } else {
                null
            }
        }
        if (channelData == null) {
            channelData = super.getChannelData().also {
                saveToCache(
                    "getChannelData",
                    json(
                        "1" to it.first,
                        "2" to it.second.toString()
                    ),
                    calcExpirationTime(5.hours, true)
                )
            }
        }
        return channelData
    }

    override suspend fun getStrikesInfo(): Pair<Boolean, Int>? {
        return (getFromCache("getStrikesInfo") as? Json)?.run {
            Pair(this["1"] as Boolean, this["2"] as Int)
        } ?: super.getStrikesInfo()?.also {
            saveToCache("getStrikesInfo", json("1" to it.first, "2" to it.second))
        }
    }

    private suspend fun <T> getOrLoadFromCache(
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

    private suspend fun getFromCache(key: String): Any? {
        val storeKey = "$publisherId-$key"

        return Promise { resolve, _ ->
            chrome.storage.local.get(arrayOf(storeKey)) {
                val cacheItem = it[storeKey] as? Json
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

    private fun saveToCache(key: String, data: Any, expirationTime: Instant = calcExpirationTime()) {
        val dataObj = json(
            "data" to data,
            "time" to expirationTime.toString(),
        )
        chrome.storage.local.set(json("$publisherId-$key" to dataObj))
    }

    private fun calcExpirationTime(cacheLiveTime: Duration = 30.minutes, limitByMidnight: Boolean = false): Instant {
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

