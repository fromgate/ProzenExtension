package devtools

import common.ApiCall
import common.ApiObserver
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/**
 * Наблюдатель Requester: разбор запроса, выбор эндпоинта и накопление расхождений.
 *
 * Работа наблюдателя выталкивается в корутину: Requester не должен ждать
 * валидацию. Ошибки валидатора выводятся в консоль, но запрос не ломают.
 */
internal object ApiValidator : ApiObserver {

    @OptIn(DelicateCoroutinesApi::class)
    override fun onResponse(call: ApiCall) {
        GlobalScope.launch {
            try {
                process(call)
            } catch (error: Throwable) {
                console.error("Prozen API validator: сбой на ${call.url}: ${error.message}")
            }
        }
    }

    private suspend fun process(call: ApiCall) {
        val reference = ReferenceHolder.document
        val request = ActualRequest.parse(call.method, call.url)
        val match = reference.match(request)

        val bodyOk = call.status == 200 && call.body != null
        if (!bodyOk) {
            // Без тела проверить форму нельзя. Спускаем только статус, чтобы
            // непонятный 50x не породил ложный new-endpoint.
            val endpointName = match?.endpoint?.name ?: suggestEndpointName(reference, request)
            val observed = when {
                call.status != 200 -> "статус ${call.status}"
                call.body == null -> "тело не JSON"
                else -> "неизвестно"
            }
            DiffStore.applyResponse(
                endpointName = endpointName,
                pending = listOf(
                    PendingDiff(
                        endpoint = endpointName,
                        kind = DiffKind.STATUS,
                        expected = "200",
                        observed = observed,
                        sample = call.body?.let(::truncateSample),
                        // Без volatileParams: пагинация и сдвиги периодов не создают новых записей.
                        request = request.canonical(reference.volatileParams),
                    )
                ),
                snapshot = null,
                requestSucceeded = false,
                caller = call.caller,
            )
            return
        }

        if (match == null) {
            val candidates = reference.pathCandidates(request)
            val endpointName = candidates
                .maxByOrNull { matchScore(it, request) }
                ?.name
                ?: suggestEndpointName(reference, request)
            val kind = if (candidates.isEmpty()) DiffKind.NEW_ENDPOINT else DiffKind.NEW_VARIANT

            DiffStore.applyResponse(
                endpointName = endpointName,
                pending = listOf(
                    PendingDiff(
                        endpoint = endpointName,
                        kind = kind,
                        observed = if (kind == DiffKind.NEW_ENDPOINT) "эндпоинт не описан" else "вариант параметров не описан",
                        request = request.canonical(reference.volatileParams),
                        note = if (kind == DiffKind.NEW_ENDPOINT) "новый эндпоинт" else "новый вариант параметров",
                    )
                ),
                snapshot = null,
                requestSucceeded = true,
                caller = call.caller,
            )
            return
        }

        val outcome = PathValidator(match.endpoint).validate(call.body!!, call.caller)
        DiffStore.applyResponse(
            endpointName = match.endpoint.name,
            pending = outcome.pending,
            snapshot = ResponseSnapshot(
                endpoint = match.endpoint,
                types = outcome.observedTypes,
                nonEmptyArrays = outcome.nonEmptyArrays,
                resolvedArrays = outcome.resolvedArrays,
            ),
            requestSucceeded = true,
            caller = outcome.caller,
        )
    }
}

/**
 * Предлагаемое имя для неописанного эндпоинта: последний осмысленный сегмент пути
 * в kebab-case, без совпадения с существующим именем.
 */
private fun suggestEndpointName(reference: ReferenceDocument, request: ActualRequest): String {
    val tail = request.segments.lastOrNull { it.isMeaningful() } ?: "endpoint"
    val base = tail
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifEmpty { "endpoint" }
    if (reference.endpoint(base) == null) return base
    var variant = 2
    while (reference.endpoint("$base-$variant") != null) variant++
    return "$base-$variant"
}

/** Хвост пути вида `{id}` или `123456` не тянет на имя. */
private fun String.isMeaningful(): Boolean {
    if (isEmpty()) return false
    return any { !it.isDigit() }
}

private fun truncateSample(element: JsonElement): String {
    val text = element.toString()
    return if (text.length <= diffSampleLimit) text else text.take(diffSampleLimit) + "…"
}
