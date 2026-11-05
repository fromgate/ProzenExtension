package devtools

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.js.Date
import kotlin.js.json

/** Префикс ключей накопленных значений путей `monitor`. */
private const val MONITOR_PREFIX = "monitor:"

/**
 * Накопленные значения путей `monitor`: один ключ `monitor:<hash>` на путь.
 *
 * Тот же паттерн, что у `DiffStore`: кеш контекста читается один раз, обновляется
 * и записывается целиком; вкладки могут стирать счётчики друг друга — счётчики
 * приблизительные, это принято. Лимит значений на путь совпадает с лимитом
 * PathValidator: накопленное не разрастается бесконечно.
 */
internal object MonitorStore {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val mutex = Mutex()
    private var cache: MutableMap<String, MonitorRecord>? = null

    /** Запись в storage.local: значения пути с накопленной статистикой. */
    @Serializable
    internal data class MonitorRecord(
        val endpoint: String,
        val path: String,
        /** value -> сколько раз встречалось. */
        val values: Map<String, Int>,
        val firstSeen: Long = 0,
        val lastSeen: Long = 0,
    )

    internal fun keyOf(endpoint: String, path: String): String = MONITOR_PREFIX + fnv1a64("$endpoint!$path")

    /** Добавляет значения одного ответа, разворачивая множества в счётчики. */
    internal suspend fun addObserved(endpointName: String, values: Map<String, Set<String>>): Unit = mutex.withLock {
        if (values.isEmpty()) return@withLock
        val records = currentRecords()
        val before = records.toMap()
        val now = nowMillis()

        values.forEach { (path, observed) ->
            val key = keyOf(endpointName, path)
            val previous = records[key]
            val merged = (previous?.values ?: emptyMap()).toMutableMap()
            observed.forEach { value ->
                if (merged.size < monitorValueCountLimit || value in merged) {
                    merged[value] = (merged[value] ?: 0) + 1
                }
            }
            records[key] = MonitorRecord(
                endpoint = endpointName,
                path = path,
                values = merged,
                firstSeen = previous?.firstSeen ?: now,
                lastSeen = now,
            )
        }

        persist(before, records)
    }

    /** Все записи: для слияния в `monitor.values` при копировании схемы. */
    internal suspend fun snapshot(): List<MonitorRecord> = mutex.withLock {
        val records = readAll().toMutableMap()
        cache = records
        records.values.sortedWith(compareBy({ it.endpoint }, { it.path }))
    }

    private suspend fun currentRecords(): MutableMap<String, MonitorRecord> =
        cache ?: readAll().toMutableMap().also { cache = it }

    private suspend fun readAll(): Map<String, MonitorRecord> = suspendCoroutine { continuation ->
        chrome.storage.local.get(null as Nothing?) { result ->
            val records = mutableMapOf<String, MonitorRecord>()
            js("Object.keys")(result).unsafeCast<Array<String>>().forEach { key ->
                if (!key.startsWith(MONITOR_PREFIX)) return@forEach
                val text = result[key] as? String ?: return@forEach
                try {
                    records[key] = json.decodeFromString(MonitorRecord.serializer(), text)
                } catch (error: Exception) {
                    console.error("MonitorStore: не разобрана запись $key: ${error.message}")
                }
            }
            continuation.resume(records)
        }
    }

    private suspend fun persist(before: Map<String, MonitorRecord>, records: Map<String, MonitorRecord>) {
        val updates = mutableMapOf<String, String?>()
        records.forEach { (key, record) ->
            if (before[key] != record) {
                updates[key] = json.encodeToString(MonitorRecord.serializer(), record)
            }
        }
        before.keys.filterNot { it in records }.forEach { key ->
            updates[key] = null
        }

        if (updates.isEmpty()) return
        writeAll(updates)
    }

    private suspend fun writeAll(updates: Map<String, String?>): Unit = suspendCoroutine { continuation ->
        val items = json()
        updates.forEach { (key, value) -> items[key] = value }
        chrome.storage.local.set(items) { continuation.resume(Unit) }
    }
}

private fun nowMillis(): Long = Date.now().toLong()
