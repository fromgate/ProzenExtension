package chrome

import kotlin.js.Json

@Suppress("ClassName") // Чтобы было похоже на JS-синтаксис
external object chrome {
    val runtime: Runtime
    val storage: Storage
    val i18n: I18n
    val webRequest: WebRequest
    val tabs: Tabs

    //chrome.runtime
    interface Runtime {
        fun getManifest(): Manifest
        val onMessage: ExtensionMessageEvent
    }

    interface Manifest {
        val version: String
    }

    interface ExtensionMessageEvent {
        fun addListener(callback: (message: dynamic, sender: MessageSender, sendResponse: (response: dynamic) -> Unit) -> Boolean)
    }

    interface MessageSender {
        val tab: Tab?
        val frameId: Int?
        val id: String?
        val url: String?
    }

    interface Tab {
        val id: Int?
        val index: Int?
        val windowId: Int?
        val openerTabId: Int?
        val highlighted: Boolean?
        val active: Boolean?
        val pinned: Boolean?
        val url: String?
        val title: String?
        val favIconUrl: String?
        val status: String?
    }

    //chrome.storage
    interface Storage {
        val local: LocalStorage
    }

    interface LocalStorage {
        fun get(keys: Array<String>, callback: (dynamic) -> Unit)
        fun set(items: dynamic)
    }


    //chrome.i18n
    interface I18n {
        fun getMessage(key: String): String?
    }

    //chrome.webRequest
    interface WebRequest {
        val onBeforeSendHeaders: WebRequestEvent
    }

    interface WebRequestEvent {
        fun addListener(
            callback: (details: WebRequestDetails) -> Unit,
            filter: dynamic,
            //filter: RequestFilter,
            extraInfoSpec: Array<String>
        )
    }

    interface WebRequestDetails {
        val url: String
        val requestHeaders: Array<HttpHeader>
        val tabId: Int
    }

    interface HttpHeader {
        val name: String
        val value: String?
    }

    interface RequestFilter {
        val urls: Array<String>
    }

    interface Tabs {
        fun sendMessage(tabId: Int, message: dynamic, callback: ((response: dynamic) -> Unit)? = definedExternally)
    }
}