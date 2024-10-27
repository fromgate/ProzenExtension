import kotlinx.browser.document
import org.w3c.dom.HTMLDataListElement
import kotlin.js.json

var searchHistoryList: MutableList<String> = mutableListOf()

/**
 * Загрузка истории поиска из chrome.storage.local
 */
fun loadSearchHistory() {
    chrome.storage.local.get("prozenSearchHistory") { result ->
        val historyString = result["prozenSearchHistory"] as? String
        if (historyString != null) {
            searchHistoryList = historyString.split(",").toMutableList()
        }
        updateSearchHistoryUI()
    }
}

/**
 * Сохранение истории поиска в chrome.storage.local
 */
fun saveSearchHistory() {
    if (searchHistoryList.isNotEmpty()) {
        chrome.storage.local.set(json ("prozenSearchHistory" to searchHistoryList.joinToString(",")))
    }
}

/**
 * Обновление истории поиска в HTML-элементе <datalist>
 */
fun updateSearchHistoryUI() {
    val searchListEl = document.getElementById("search-history") as? HTMLDataListElement ?: return
    searchListEl.innerHTML = ""  // Очищаем элементы списка

    searchHistoryList.forEach { history ->
        val optionElement = document.createElement("option")
        optionElement.setAttribute("value", history)
        searchListEl.appendChild(optionElement)
    }
}

/**
 * Добавление нового запроса в историю
 */
fun addNewSearchToHistory(query: String) {
    searchHistoryList = searchHistoryList.filter { it != query }.toMutableList()
    searchHistoryList.add(0, query)  // Добавляем новый запрос в начало

    if (searchHistoryList.size > 10) {
        searchHistoryList = searchHistoryList.take(10).toMutableList()
    }
    updateSearchHistoryUI()
    saveSearchHistory()
}