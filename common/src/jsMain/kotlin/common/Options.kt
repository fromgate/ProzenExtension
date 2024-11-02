package common

import kotlinx.coroutines.await
import kotlin.js.Promise
import kotlin.js.json

enum class Option(
    val id: String,
    val title: String,
    val description: String? = null,
    val defaultValue: Boolean = true,
    val group: String = "default"
) {
    PROZEN("prozen-switch", "Расширение включено"),

    POPUP_INFO("prozen-popup-info-block", "Статистика во всплывающем окне",
        "Показывать блок с информацией о текущей статистке во всплывающем окне расширения"),

    // Студия
    PROZEN_MENU(
        "prozen-menu-switch", "Меню в сайдбаре",
        "Добавлять меню расширения в левую панель студии", group = "Студия"
    ),
    INFORMER("prozen-informer-switch", "Виджет расширения", group = "Студия"),
    SHORT_DASHBOARD_REALTIME(
        "prozen-realtime-switch", "Почасовая статистика (только график)",
        "Если отключено, то из почасовой статистики скрывается список статей", group = "Студия"
    ),
    COMMENTS_WIDGET(
        "prozen-comments-switch2", "Скрывать комментарии",
        defaultValue = false, group = "Студия"
    ),
    PROMOTE_SHOW(
        "prozen-promote-show", "Скрывать баннеры",
        defaultValue = false, group = "Студия"
    ),

    // Публикация

    SUBTITLE_LINKS(
    "prozen-article-link-switch2", "Ссылки в подзаголовках",
    "Добавлять ссылки в подзаголовки статей",
    defaultValue = false, group = "Публикация"
    ),
    CHECK_NOINDEX(
      "prozen-publication-noindex-check", "Проверять индексацию",
        defaultValue = false, group = "Публикация"
    ),
    RECHECK_NOINDEX(
        "prozen-publication-noindex-recheck", "Двойная перепроверка индексации",
        "Проводить дополнительную перепроверку, если обнаружена отключённая индексация",
        defaultValue = true, group = "Публикация"
    );

    /*
     TODO:
      Разделы
       - Включить / выключить
       - Студия
         - Меню (в сайдбаре)
         - Виджет
         - Кэширование виджета ?
         - Скрыть почасовой список
         - Скрыть баннеры
         - Скрыть коменты?
       - Публикации
         - Отображать статистику
         - Ссылки в подзаголовках
         - Перепроверять noindex         -
     */

    fun getValueOrDefault(value: Boolean?) = value ?: defaultValue

    fun value() = Options.get(this.id)

    suspend fun isSet(): Boolean = Options.get(this.id).await()

    fun set(value: Boolean) {
        Options.set(this.id, value)
    }

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

    fun get(option: Option) = get(option.id)

    /* fun set(optionId: String, value: Boolean) {
        load().then { loadedOptions ->
            val loaded = loadedOptions.toMutableMap()
            val options = json()
            loaded.forEach { (id, existValue) ->
                options[optionId] = if (id == optionId) value else existValue
            }
            chrome.storage.local.set(options)
        }
    } */

    fun set(optionId: String, value: Boolean) {
        save (mapOf(optionId to value))
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