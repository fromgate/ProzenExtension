package requester

import common.Cache
import common.parseInstant
import kotlinx.datetime.Instant
import kotlin.js.Json
import kotlin.js.json
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE", "UNCHECKED_CAST")
class RequesterCached(publisherId: String?, token: String?) : Requester(publisherId, token) {

    val cache = Cache (publisherId ?: "RequesterCache")

    override suspend fun getScr(from: String, to: String): Double? {
        return cache.getOrLoadFromCache("getScr", cache.calcExpirationTime(5.hours, true)) {
            super.getScr(from, to)
        }
    }

    override suspend fun getBannedUsers(): Int? {
        return cache.getOrLoadFromCache("getBannedUsers", cache.calcExpirationTime(30.minutes)) {
            super.getBannedUsers()
        }
    }

    override suspend fun getStatsActuality(): String? {
        return cache.getOrLoadFromCache("getStatsActuality", cache.calcExpirationTime(10.minutes)) {
            super.getStatsActuality()
        }
    }

    override suspend fun getTimespentRewards(from: String, to: String): List<Triple<String, Double, Double>> {
        var rewards = (cache.getFromCache("getTimespentRewards") as? Array<Json>)?.run {
            this.map { Triple(it["1"] as String, it["2"] as Double, it["3"] as Double) }
        }

        if (rewards == null) {
            rewards = super.getTimespentRewards(from, to)
            cache.saveToCache(
                "getTimespentRewards",
                rewards.map {
                    json("1" to it.first, "2" to it.second, "3" to it.third)
                }.toTypedArray(),
                cache.calcExpirationTime(60.minutes)
            )
        }
        return rewards
    }

    override suspend fun getChannelData(): Pair<Int?, Instant?> {
        var channelData: Pair<Int?, Instant?>? = (cache.getFromCache("getChannelData") as? Json)?.run {
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
                cache.saveToCache(
                    "getChannelData",
                    json(
                        "1" to it.first,
                        "2" to it.second.toString()
                    ),
                    cache.calcExpirationTime(5.hours, true)
                )
            }
        }
        return channelData
    }

    override suspend fun getStrikesInfo(): Pair<Boolean, Int>? {
        return (cache.getFromCache("getStrikesInfo") as? Json)?.run {
            Pair(this["1"] as Boolean, this["2"] as Int)
        } ?: super.getStrikesInfo()?.also {
            cache.saveToCache("getStrikesInfo",
                json("1" to it.first, "2" to it.second),
                cache.calcExpirationTime(5.minutes)
            )
        }
    }

}

