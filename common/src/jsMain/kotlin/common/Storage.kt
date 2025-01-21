package common

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.js.json

suspend fun saveToStorage(key: String, value: String) {
    suspendCoroutine { continuation ->
        chrome.storage.local.set(json(key to value)) {
            continuation.resume(Unit)
        }
    }
}

suspend fun saveToStorage(data: Map<String, Any?>) {
    suspendCoroutine { continuation ->
        chrome.storage.local.set(js("Object").assign(js("{}"), data) as Any) {
            continuation.resume(Unit)
        }
    }
}

suspend fun getFromStorageStr(key: String): String? {
    return suspendCoroutine { continuation ->
        chrome.storage.local.get(key) { result ->
            val value = result[key] as? String
            continuation.resume(value)
        }
    }
}

suspend fun getFromStorageStr(keys: Array<String>): Map<String, String?> {
    return suspendCoroutine { continuation ->
        chrome.storage.local.get(keys) { result ->
            val data = keys.associateWith { key -> result[key] as? String }
            continuation.resume(data)
        }
    }
}


suspend fun getFromStorage(keys: Array<String>): Map<String, Any?> {
    return suspendCoroutine { continuation ->
        chrome.storage.local.get(keys) { result ->
            val data = keys.associateWith { key -> result[key] }
            continuation.resume(data)
        }
    }
}