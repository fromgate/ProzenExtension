import kotlin.js.Promise
import kotlin.js.json

enum class OPTIONS(val id: String, val defaultValue: Boolean = true) {
    prozen("prozen-switch"),
    shortDashboardRealtime("prozen-realtime-switch"),
    prozenMenu("prozen-menu-switch"),
    informer("prozen-informer-switch"),
    subtitleLinks("prozen-article-link-switch2", false),
    commentsWidget("prozen-comments-switch2", false),
    promoteShow("prozen-promote-show", false);

    fun getValueOrDefault(value: Boolean?) = value ?: defaultValue

    companion object {
        fun getIds(): List<String> = entries.map { it.id }
        fun getById(id: String): OPTIONS? = entries.firstOrNull { it.id == id }
        fun defaultValue(id: String) = getById(id)?.defaultValue ?: true
        fun getValueOrDefault(id: String, value: Boolean?): Boolean {
            return getById(id)?.getValueOrDefault(value) ?: true
        }
    }
}

object Options {
    fun get(optionId: String): Promise<Boolean> = Promise { resolve, _ ->
        chrome.storage.local.get(OPTIONS.getIds().toTypedArray()) { option ->
            resolve(option[optionId] as Boolean? ?: OPTIONS.defaultValue(optionId))
        }
    }

    fun set(optionId: String, value: Boolean) {
        load().then { loadedOptions ->
            val loaded = loadedOptions.toMutableMap()
            val options = json()
            loaded.forEach { (id, existValue) ->
                options[optionId] = if (id == optionId) value else existValue
            }
            chrome.storage.local.set(options)
        }
    }

    fun load(): Promise<Map<String, Boolean>> {
        return Promise { resolve, _ ->
            chrome.storage.local.get(OPTIONS.getIds().toTypedArray()) { options ->
                val result = mutableMapOf<String, Boolean>()
                OPTIONS.getIds().forEach {
                    result[it] = options[it] as Boolean? ?: OPTIONS.defaultValue(it)
                }
                resolve(result)
            }
        }
    }

    fun save(values: Map<String, Boolean>) {
        load().then { loadedOptions ->
            val options = json()
            OPTIONS.getIds().forEach { id ->
                val value = OPTIONS.getValueOrDefault(id, values[id] ?: loadedOptions[id])
                options[id] = value
            }
            chrome.storage.local.set(options)
        }
    }
}