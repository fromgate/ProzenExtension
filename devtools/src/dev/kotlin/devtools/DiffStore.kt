package devtools

import common.getFromStorageStr
import common.saveToStorage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.js.Date
import kotlin.js.json

/** `storage.local` закешированный хеш эталона; главный признак того, что эталон поменялся. */
private const val REFERENCE_HASH_KEY = "prozen-api-reference-hash"

/** Префикс ключей расхождений. */
private const val DIFF_PREFIX = "diff:"

/** Виды расхождений по пути и по запросу — то же, что в copy-тексте. */
@Serializable
internal enum class DiffKind(val wire: String) {
    NEW("new"),
    MISSING("missing"),
    TYPE("type"),
    STATUS("status"),
    UNVERIFIED("unverified"),
    EMPTY("empty"),
    NEW_ENDPOINT("new-endpoint"),
    NEW_VARIANT("new-variant"),
}

/** Кандидат в расхождения, пока без счётчика и времени: результат одного ответа. */
internal data class PendingDiff(
    val endpoint: String,
    val kind: DiffKind,
    val path: String = "",
    val expected: String? = null,
    val observed: String? = null,
    val sample: String? = null,
    val note: String? = null,
    /** Канонический запрос без volatileParams: нужен для заготовок new-endpoint/new-variant. */
    val request: String? = null,
    val caller: String? = null,
)

/** Запись в storage.local: расхождение с накопленной статистикой. */
@Serializable
internal data class DiffRecord(
    val endpoint: String,
    val kind: DiffKind,
    val path: String = "",
    val expected: String? = null,
    val observed: String? = null,
    val sample: String? = null,
    val note: String? = null,
    val request: String? = null,
    val count: Int = 1,
    val firstSeen: Long = 0,
    val lastSeen: Long = 0,
    val caller: String? = null,
    /** Эндпоинт исчез из эталона: запись осиротела, её нужно разобрать вручную. */
    val orphaned: Boolean = false,
)

/**
 * Стабильный ключ записи: расхождения от разных вкладок складываются в одну запись.
 */
internal fun diffKeyOf(record: DiffRecord): String = DIFF_PREFIX + fnv1a64(
    listOf(record.endpoint, record.kind.wire, record.path, record.request ?: "").joinToString("!")
)

internal fun diffKeyOf(pending: PendingDiff): String = DIFF_PREFIX + fnv1a64(
    listOf(pending.endpoint, pending.kind.wire, pending.path, pending.request ?: "").joinToString("!")
)

/** FNV-1a 64-bit: детерминированный хеш без внешних зависимостей. */
internal fun fnv1a64(text: String): String {
    // Начальное значение FNV_OFFSET_BASIS, записанное со знаком, потому что Kotlin/JS Long знаковый.
    var hash: Long = -3750763034362895579L
    text.forEach { char ->
        hash = hash xor char.code.toLong()
        hash *= 1099511628211L
    }
    return hash.toULong().toString(16)
}

/**
 * Хранилище расхождений: один ключ `diff:<hash>` на запись, как договорились.
 *
 * Изменения никогда не происходят поверх мутации storage: кеш контекста читается
 * один раз, обновляется и записывается целиком. Разные контексты расширения
 * могут стирать счётчики друг друга — счётчики приблизительные, это принято.
 */
internal object DiffStore {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val mutex = Mutex()
    private var cache: MutableMap<String, DiffRecord>? = null

    /**
     * Накапливает расхождения, собранные по одному ответу, и снимает
     * разрешившиеся: `missing` и `unverified`, чей путь появился в ответе
     * или чей массив проверен/пуст по правилу, `type`, чей тип теперь допустим,
     * `empty`, если массив стал непустым, и `status` на запросе с успехом.
     */
    internal suspend fun applyResponse(
        endpointName: String,
        pending: List<PendingDiff>,
        snapshot: ResponseSnapshot?,
        requestSucceeded: Boolean,
        caller: String?,
    ): Unit = mutex.withLock {
        val records = currentRecords()
        val before = records.toMap()
        val now = nowMillis()

        if (requestSucceeded) {
            records.entries.removeAll { (_, record) ->
                record.endpoint == endpointName && record.kind == DiffKind.STATUS
            }
        }

        snapshot?.let { snap ->
            records.entries.removeAll { (_, record) ->
                record.endpoint == endpointName && snap.resolved(record)
            }
        }

        pending.forEach { item ->
            val key = diffKeyOf(item)
            val previous = records[key]
            records[key] = DiffRecord(
                endpoint = item.endpoint,
                kind = item.kind,
                path = item.path,
                expected = item.expected,
                observed = item.observed ?: previous?.observed,
                sample = item.sample ?: previous?.sample,
                note = item.note ?: previous?.note,
                request = item.request ?: previous?.request,
                count = (previous?.count ?: 0) + 1,
                firstSeen = previous?.firstSeen ?: now,
                lastSeen = now,
                caller = item.caller ?: caller ?: previous?.caller,
                orphaned = false,
            )
        }

        persist(before, records)
    }

    /**
     * После перечитываний storage и если хеш эталона поменял/storage отсутствовал:
     * переоценивает накопленные расхождения по новому эталону.
     */
    internal suspend fun ensureReferenceCurrent(): ReferenceDocument = mutex.withLock {
        val reference = ReferenceHolder.document
        val stored = getFromStorageStr(REFERENCE_HASH_KEY)

        val records = readAll().toMutableMap()
        val before = records.toMap()

        if (stored != reference.hash) {
            revalidate(records, reference)
            saveToStorage(REFERENCE_HASH_KEY, reference.hash)
        }

        cache = records
        persist(before, records)
        reference
    }

    /** Свежие записи из storage: для интерфейса, чтобы увидеть чужие контексты. */
    internal suspend fun snapshot(): List<DiffRecord> = mutex.withLock {
        val records = readAll().toMutableMap()
        cache = records
        records.values.sortedWith(
            compareBy({ it.endpoint }, { it.kind.name }, { it.path })
        )
    }

    // ------------------------------------------------------------------ переоценка

    private fun revalidate(records: MutableMap<String, DiffRecord>, reference: ReferenceDocument) {
        val now = nowMillis()

        for ((key, record) in records.entries.toList()) {
            val endpoint = reference.endpoint(record.endpoint)
            if (endpoint == null) {
                // У new-endpoint имени в эталоне нет по определению — он не осиротел.
                // Осиротевшими считаем только записи по когда-то описанному эндпоинту.
                if (record.kind != DiffKind.NEW_ENDPOINT && !record.orphaned) {
                    records[key] = record.copy(orphaned = true, lastSeen = now)
                }
                continue
            }

            val remove = when (record.kind) {
                DiffKind.NEW -> endpoint.isCovered(parsePath(record.path))
                DiffKind.TYPE -> typeIsSettled(endpoint, record)
                DiffKind.MISSING -> missingIsSettled(endpoint, record)
                DiffKind.EMPTY -> emptyIsSettled(endpoint, record)
                DiffKind.UNVERIFIED -> unverifiedIsSettled(endpoint, record)
                DiffKind.NEW_ENDPOINT, DiffKind.NEW_VARIANT ->
                    record.request != null && reference.match(ActualRequest.parseCanonical(record.request)) != null
                DiffKind.STATUS -> false
            }
            if (remove) {
                records.remove(key)
            }
        }
    }

    /** `type`: требование исчезло или фактический тип теперь допустим. */
    private fun typeIsSettled(endpoint: Endpoint, record: DiffRecord): Boolean {
        val steps = parsePath(record.path)
        val rule = endpoint.bestUsedExact(steps) ?: return true
        if ("any" in rule.types) return true
        val observed = record.observed?.split('|')?.map { it.trim() }.orEmpty()
        return observed.isNotEmpty() && observed.all { it in rule.types }
    }

    /** `missing`: путь убрали из `used`, сделали необязательным или спрятали под `ignored`. */
    private fun missingIsSettled(endpoint: Endpoint, record: DiffRecord): Boolean {
        val steps = parsePath(record.path)
        val rule = endpoint.used.firstOrNull { usedMatches(it.path, steps) } ?: return true
        return rule.optional || endpoint.hasMatchingIgnored(rule)
    }

    /** `empty`: правило стало необязательным (`array?`), исчезло или массив больше не требуется. */
    private fun emptyIsSettled(endpoint: Endpoint, record: DiffRecord): Boolean {
        val steps = parsePath(record.path)
        val rule = endpoint.bestUsedExact(steps) ?: return true
        if (rule.optional) return true
        if (endpoint.hasMatchingIgnored(rule)) return true
        return "array" !in rule.types
    }

    /**
     * Одноразовая миграция: старые `unverified` из-за пустого массива больше не
     * воспроизводятся, если на пути массива появилось правило (`empty` или `array?`).
     * Без правила поведение прежнее, такую запись оставляем.
     */
    private fun unverifiedIsSettled(endpoint: Endpoint, record: DiffRecord): Boolean {
        if (!record.note.orEmpty().startsWith("пустой массив")) return false
        val steps = parsePath(record.path)
        return steps.indices.any { index ->
            steps[index] === PathStep.ArrayItem && endpoint.bestUsedExact(steps.take(index)) != null
        }
    }

    // ------------------------------------------------------------------ storage

    private suspend fun currentRecords(): MutableMap<String, DiffRecord> =
        cache ?: readAll().toMutableMap().also { cache = it }

    private suspend fun readAll(): Map<String, DiffRecord> = suspendCoroutine { continuation ->
        chrome.storage.local.get(null as Nothing?) { result ->
            val records = mutableMapOf<String, DiffRecord>()
            js("Object.keys")(result).unsafeCast<Array<String>>().forEach { key ->
                if (!key.startsWith(DIFF_PREFIX)) return@forEach
                val text = result[key] as? String ?: return@forEach
                try {
                    val record = json.decodeFromString(DiffRecord.serializer(), text)
                    records[key] = record
                } catch (error: Exception) {
                    // Битую запись не лечим молча: сообщаем и выбрасываем.
                    console.error("DiffStore: не разобрана запись $key: ${error.message}")
                }
            }
            continuation.resume(records)
        }
    }

    private suspend fun persist(before: Map<String, DiffRecord>, records: Map<String, DiffRecord>) {
        val updates = mutableMapOf<String, String?>()
        records.forEach { (key, record) ->
            if (before[key] != record) {
                updates[key] = json.encodeToString(DiffRecord.serializer(), record)
            }
        }
        before.keys.filterNot { it in records }.forEach { key ->
            updates[key] = null
        }

        if (updates.isEmpty()) return
        writeAll(updates)
    }

    /** Записывает группу ключей за один вызов `chrome.storage.local.set`; null удаляет ключ. */
    private suspend fun writeAll(updates: Map<String, String?>): Unit = suspendCoroutine { continuation ->
        val items = json()
        updates.forEach { (key, value) -> items[key] = value }
        chrome.storage.local.set(items) { continuation.resume(Unit) }
    }
}

private fun nowMillis(): Long = Date.now().toLong()
