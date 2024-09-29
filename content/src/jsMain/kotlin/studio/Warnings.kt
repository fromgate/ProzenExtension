package studio

import common.showNotification
import kotlin.js.Json
import kotlin.js.Promise
import kotlin.js.json


class Warnings(publisherId: String, strikes: Pair<Boolean, Int>) {

    private val storeKey: String = "$publisherId-warnings"
    private val channeBlocked = strikes.first
    private val limitations = strikes.second

    fun notify() {
        getOldWarnings().then {oldStrikes ->
            //val oldStrikes = Pair(false, 0) /
            if (oldStrikes != null) {
                val oldChannelBlocked = oldStrikes.first
                val oldLimitations = oldStrikes.second
                var text =""
                if (!oldChannelBlocked && channeBlocked) {
                    text +="Ваш канал ограничен!\n"
                }
                if (oldLimitations < limitations || text.isNotEmpty()) {
                    text += "Обнаружены предупреждения: $limitations"
                }
                if (text.isNotEmpty()) {
                    showNotification(text, timeout = 10000)
                }
            }
            saveWarnings(channeBlocked, limitations)
        }
    }

    private fun saveWarnings(channelBlocked: Boolean, limitations: Int) {
        val dataObj = json(
            "channelBlocked" to channelBlocked,
            "limitations" to limitations,
        )
        chrome.storage.local.set(json(storeKey to dataObj))
    }

    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    private fun getOldWarnings(): Promise<Pair<Boolean, Int>?> {
        return Promise() { resolve, _ ->
            chrome.storage.local.get(arrayOf(storeKey)) {
                val dataObj = it[storeKey] as? Json
                if (dataObj != null) {
                    val channelBlocked = dataObj["channelBlocked"] as? Boolean
                    val limitations = dataObj["limitations"] as? Int
                    if (channelBlocked != null && limitations != null) {
                        resolve(Pair(channelBlocked, limitations))
                    }
                }
                resolve(null)
            }
        }
    }
}