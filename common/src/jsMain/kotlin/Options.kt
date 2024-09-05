import kotlin.js.Promise
import kotlin.js.json

enum class Option(val id: String, val defaultValue: Boolean = true) {
    PROZEN("prozen-switch"),
    SHORT_DASHBOARD_REALTIME("prozen-realtime-switch"),
    PROZEN_MENU("prozen-menu-switch"),
    INFORMER("prozen-informer-switch"),
    SUBTITLE_LINKS("prozen-article-link-switch2", false),
    COMMENTS_WIDGET("prozen-comments-switch2", false),
    PROMOTE_SHOW("prozen-promote-show", false);

    fun getValueOrDefault(value: Boolean?) = value ?: defaultValue

    companion object {
        fun getIds(): List<String> = entries.map { it.id }
        fun getById(id: String): Option? = entries.firstOrNull { it.id == id }
        fun defaultValue(id: String) = getById(id)?.defaultValue ?: true
        fun getValueOrDefault(id: String, value: Boolean?): Boolean {
            return getById(id)?.getValueOrDefault(value) ?: true
        }
    }
}

object Options {
    fun get(optionId: String): Promise<Boolean> = Promise { resolve, _ ->
        chrome.storage.local.get(Option.getIds().toTypedArray()) { option ->
            resolve(option[optionId] as Boolean? ?: Option.defaultValue(optionId))
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
            chrome.storage.local.get(Option.getIds().toTypedArray()) { options ->
                val result = mutableMapOf<String, Boolean>()
                Option.getIds().forEach {
                    result[it] = options[it] as Boolean? ?: Option.defaultValue(it)
                }
                resolve(result)
            }
        }
    }

    fun save(values: Map<String, Boolean>) {
        load().then { loadedOptions ->
            val options = json()
            Option.getIds().forEach { id ->
                val value = Option.getValueOrDefault(id, values[id] ?: loadedOptions[id])
                options[id] = value
            }
            chrome.storage.local.set(options)
        }
    }
}