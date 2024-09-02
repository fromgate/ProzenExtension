import kotlin.js.Promise
import kotlin.js.json

enum class OPTIONS(val id: String, val disableByDefault: Boolean = true) {
    prozen("prozen-switch"),
    shortDashboardRealtime("prozen-realtime-switch"),
    prozenMenu("prozen-menu-switch"),
    informer("prozen-informer-switch"),
    subtitleLinks("prozen-article-link-switch2", true),
    commentsWidget("prozen-comments-switch2", true),
    promoteShow("prozen-promote-show", true);

    fun getValueOrDefault(value: Boolean?) = value ?: disableByDefault

    companion object {
        fun getIds(): List<String> = entries.map { it.id }
        fun getById(id: String): OPTIONS? = entries.firstOrNull { it.id == id }
        fun disabledByDefault(id: String) = getById(id)?.disableByDefault ?: false
        fun getValueOrDefault(id: String, value: Boolean?): Boolean {
            return getById(id)?.getValueOrDefault(value) ?: false
        }

    }
}

object Options {
    fun get(optionId: String): Promise<Boolean> = Promise { resolve, _ ->
        chrome.storage.local.get(OPTIONS.getIds().toTypedArray()) { option ->
            resolve(option[optionId] as Boolean? ?: OPTIONS.disabledByDefault(optionId))
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
                    result[it] = options[it] as Boolean? ?: OPTIONS.disabledByDefault(it)
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