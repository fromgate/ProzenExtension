// =============================================================================
// Файл 1 (общий код, попадает и в release): ApiObserver.kt
// =============================================================================
package requester

import kotlinx.serialization.json.JsonElement

/** Один выполненный запрос. Всё, что нужно наблюдателю; про функции Requester он не знает. */
class ApiCall(
    val method: String,          // пока всегда "GET"
    val url: String,             // полный URL с query, нормализует наблюдатель
    val status: Int,             // HTTP-статус
    val body: JsonElement?,      // null, если тело не JSON (или пустое)
    val caller: String? = null,  // необязательная подсказка "кто вызвал", только для отчёта
)

interface ApiObserver {
    /**
     * Вызывается после каждого ответа. Требования к реализации:
     *  - не бросать исключения (Requester всё равно обернёт в try/catch);
     *  - быстро вернуться: тяжёлый разбор и запись в storage делать в своём scope.
     */
    fun onResponse(call: ApiCall)
}

object NoopObserver : ApiObserver {
    override fun onResponse(call: ApiCall) {}
}

/** Точка регистрации. В release никто её не меняет, остаётся NoopObserver. */
object ApiObservers {
    var current: ApiObserver = NoopObserver
}


// =============================================================================
// Файл 2: Requester.kt — ТОЛЬКО изменённые части.
// Все публичные методы (getScr, getTimespentRewards, getStrikes, ...) не меняются.
// =============================================================================
package requester

import common.*
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.decodeFromJsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

open class Requester(
    val publisherId: String?,
    val token: String?,
    // Значение по умолчанию вычисляется при создании Requester, поэтому dev-регистрация
    // (ApiObservers.current = ...) должна выполниться раньше, чем создан первый Requester.
    private val observer: ApiObserver = ApiObservers.current,
) {
    // client, hasToken(), hasPublisherId(), defaultHeaders — без изменений

    /** Результат единой воронки: статус, текст и однократно разобранный JSON. */
    class Fetched(val status: HttpStatusCode, val text: String, val json: JsonElement?)

    /**
     * Единая воронка: запрос -> текст один раз -> JSON один раз -> наблюдатель -> результат.
     * @PublishedApi internal, потому что её вызывает публичная inline-функция getData<T>.
     */
    @PublishedApi
    internal suspend fun fetch(
        requestUrl: String,
        customHeaders: HeadersBuilder.() -> Unit,
        caller: String?,
    ): Fetched {
        val response = getHttpResponse(requestUrl, customHeaders)
        val text = response.bodyAsText()
        val element: JsonElement? = try {
            if (text.isBlank()) null else Json.parseToJsonElement(text)
        } catch (e: Exception) {
            null // тело не JSON — наблюдатель увидит body = null при статусе 200
        }
        // Сбой наблюдателя никогда не должен ломать запрос
        try {
            observer.onResponse(ApiCall("GET", requestUrl, response.status.value, element, caller))
        } catch (e: Throwable) {
            // намеренно молчим
        }
        return Fetched(response.status, text, element)
    }

    suspend fun getJson(
        requestUrl: String,
        customHeaders: HeadersBuilder.() -> Unit = defaultHeaders,
        caller: String? = null,
    ): JsonObject? {
        val r = fetch(requestUrl, customHeaders, caller)
        return if (r.status == HttpStatusCode.OK) r.json as? JsonObject else null
    }

    suspend fun getJsonSafe(
        label: String,
        requestUrl: String,
        customHeaders: HeadersBuilder.() -> Unit = defaultHeaders,
    ): JsonObject? {
        // label остаётся для safe() (логирование) и заодно уходит наблюдателю как caller
        return safe(label) {
            getJson(requestUrl, customHeaders, caller = label)
        }
    }

    suspend inline fun <reified T> getData(
        requestUrl: String,
        noinline customHeaders: HeadersBuilder.() -> Unit = defaultHeaders,
        caller: String? = null,
    ): T? {
        val r = fetch(requestUrl, customHeaders, caller)
        val element = r.json
        return if (r.status == HttpStatusCode.OK && element != null) {
            // Json по умолчанию (без ignoreUnknownKeys), как и в исходном getData
            Json.decodeFromJsonElement<T>(element)
        } else {
            console.dLog("Failed to get json from request: $requestUrl")
            null
        }
    }

    suspend inline fun <reified T> getDataSafe(
        label: String,
        requestUrl: String,
        noinline customHeaders: HeadersBuilder.() -> Unit = defaultHeaders,
    ): T? {
        return safe(label) {
            getData(requestUrl, customHeaders, caller = label)
        }
    }

    // getHttpResponse — без изменений. Прямые вызовы в обход fetch() наблюдатель не видит.
    suspend fun getHttpResponse(
        requestUrl: String,
        customHeaders: HeadersBuilder.() -> Unit = defaultHeaders,
    ): HttpResponse {
        return client.get(requestUrl) { headers { customHeaders() } }
    }
}

// Мелкая правка в вызывающих методах (необязательная, на эталон уже не влияет):
//  - getPublicationStat: getData(requestUrl) -> getData(requestUrl, caller = "getPublicationStat")
//  - getStrikes: метка "getStrikesInfo" -> "getStrikes"


// =============================================================================
// Файл 3 (только dev-вариант, модуль dev-validator): DevBootstrap.kt
// =============================================================================
// fun installDevValidator() {
//     ApiObservers.current = ReferenceValidator(/* эталон из storage, хранилище расхождений */)
// }
// Вызывается из точки входа dev-сборки ДО создания первого Requester.
// В release этого файла и модуля нет, ApiObservers.current остаётся NoopObserver.
