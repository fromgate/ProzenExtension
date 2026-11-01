package devtools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Какое количество элементов массива обходить: голова + хвост, остальное пропускается. */
internal const val ARRAY_SAMPLE_SIZE: Int = 40

/** Эталон целиком: разобранный `devtools/build/generated/reference/ZenApiReference.kt`. */
internal object ReferenceHolder {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ReferenceJson(
        val format: Int,
        val hash: String,
        val meta: MetaJson = MetaJson(),
        val volatileParams: List<String> = emptyList(),
        val endpoints: List<EndpointJson> = emptyList(),
    )

    @Serializable
    private data class MetaJson(val zenVersion: String = "", val updated: String = "")

    @Serializable
    private data class EndpointJson(
        val name: String,
        val note: String = "",
        val match: MatchJson,
        val used: List<UsedJson> = emptyList(),
        val ignored: List<IgnoredJson> = emptyList(),
    )

    @Serializable
    private data class MatchJson(
        val method: String,
        val segments: List<SegmentJson> = emptyList(),
        val params: List<ParamJson> = emptyList(),
        val canonical: String = "",
        val specificity: List<Int> = emptyList(),
    )

    @Serializable
    private data class SegmentJson(val k: String, val v: String = "")

    @Serializable
    private data class ParamJson(val n: String, val v: List<String> = emptyList())

    @Serializable
    private data class UsedJson(
        val path: List<PathStepJson> = emptyList(),
        val types: List<String> = emptyList(),
        val optional: Boolean = false,
        val specificity: List<Int> = emptyList(),
    )

    @Serializable
    private data class IgnoredJson(val path: List<PathStepJson> = emptyList(), val reason: String = "")

    @Serializable
    private data class PathStepJson(val k: String, val n: String = "")

    val document: ReferenceDocument by lazy { parse(ZEN_API_REFERENCE_JSON) }

    private fun parse(text: String): ReferenceDocument {
        val dto = json.decodeFromString(ReferenceJson.serializer(), text)
        check(dto.format == 1) { "Неизвестная версия эталона API: ${dto.format}" }
        return ReferenceDocument(
            format = dto.format,
            hash = dto.hash,
            zenVersion = dto.meta.zenVersion,
            updated = dto.meta.updated,
            volatileParams = dto.volatileParams.toSet(),
            endpoints = dto.endpoints.map { it.toEndpoint() },
        )
    }

    private fun EndpointJson.toEndpoint() = Endpoint(
        name = name,
        note = note,
        match = match.toMatch(),
        used = used.mapIndexed { index, used -> used.toUsed(index) },
        ignored = ignored.map { it.toIgnored() },
    )

    private fun MatchJson.toMatch(): EndpointMatch {
        val literals = params.count { it.v.any { value -> !value.startsWith("{") } }
        val rank = if (specificity.size >= 2) MatchRank(specificity[0], specificity[1]) else MatchRank(literals, params.size)
        return EndpointMatch(
            method = method.uppercase(),
            segments = segments.map {
                when (it.k) {
                    "lit" -> MatchSegment.Literal(it.v)
                    "var" -> MatchSegment.Variable(it.v)
                    else -> throw IllegalStateException("Неизвестный сегмент пути эталона: ${it.k}")
                }
            },
            params = params.map { param ->
                MatchParam(
                    name = param.n,
                    values = param.v.map { value ->
                        if (value.startsWith("{") && value.endsWith("}")) {
                            MatchValue.Any(value.substring(1, value.length - 1))
                        } else {
                            MatchValue.Literal(value)
                        }
                    },
                )
            },
            canonical = canonical,
            rank = rank,
        )
    }

    private fun List<PathStepJson>.toSteps(): List<PathStep> =
        map {
            when (it.k) {
                "key" -> PathStep.Key(it.n)
                "arr" -> PathStep.ArrayItem
                "any" -> PathStep.AnyKey
                "deep" -> PathStep.Deep
                else -> throw IllegalStateException("Неизвестный шаг пути эталона: ${it.k}")
            }
        }

    private fun UsedJson.toUsed(index: Int) = Endpoint.UsedRule(
        index = index,
        path = path.toSteps(),
        // "any" остаётся в наборе: правило с any не вызывает расхождение по типу.
        types = types.toSet(),
        optional = optional,
    )

    private fun IgnoredJson.toIgnored() = Endpoint.IgnoredRule(path = path.toSteps(), reason = reason)
}

/** Результат сопоставления запроса с эндпоинтом. */
internal data class MatchResult(val endpoint: Endpoint, val rank: MatchRank)

/** Специфичность match: больше литеральных параметров, затем больше параметров вообще. */
internal data class MatchRank(val literals: Int, val params: Int) : Comparable<MatchRank> {
    override fun compareTo(other: MatchRank): Int =
        compareValuesBy(this, other, { it.literals }, { it.params })
}

/**
 * Один вариант эндпоинта: `match`, правила `used` и `ignored`.
 * Даёт выборку правил: точное совпадение пути, покрытие ignored, ближайшие ниже.
 */
internal class Endpoint(
    val name: String,
    val note: String,
    val match: EndpointMatch,
    val used: List<UsedRule>,
    val ignored: List<IgnoredRule>,
) {
    /** Тип поля, который проверяет правило. */
    internal class UsedRule(
        val index: Int,
        val path: List<PathStep>,
        val types: Set<String>,
        val optional: Boolean,
    ) {
        val canonical: String = formatPath(path)
        val rank: PathRank = path.pathRank

        fun matchesAt(actual: List<PathStep>): Boolean = usedMatches(path, actual)
    }

    internal class IgnoredRule(val path: List<PathStep>, val reason: String) {
        val canonical: String = formatPath(path)
        val rank: PathRank = path.pathRank
    }

    fun bestUsedExact(actual: List<PathStep>): UsedRule? =
        used.filter { it.matchesAt(actual) }.maxByOrNull { it.rank }

    /** Все правила `used`, совпадающие с реальным путём ровно (нужны, чтобы не считать совпадение `missing`). */
    fun allUsedExact(actual: List<PathStep>): List<UsedRule> = used.filter { it.matchesAt(actual) }

    /** Самое специфичное правило, которое может лежать под этим узлом или точно на нём. */
    fun bestUsedAtOrBelow(actual: List<PathStep>): UsedRule? =
        used.filter { usedIsBelow(it.path, actual) }.maxByOrNull { it.rank }

    /** Самое специфичное правило `ignored`, покрывающее реальный путь. */
    fun bestIgnored(actual: List<PathStep>): IgnoredRule? =
        ignored.filter { ignoredCovers(it.path, actual) }.maxByOrNull { it.rank }

    /** Путь больше не «новый»: он либо в `used`, либо целиком под `ignored`. */
    fun isCovered(actual: List<PathStep>): Boolean =
        bestUsedExact(actual) != null || bestIgnored(actual) != null

    /** Правило `used` перекрыто правилом `ignored` с не меньшей специфичностью. */
    fun hasMatchingIgnored(rule: UsedRule): Boolean =
        ignored.any { ignoredCovers(it.path, rule.path) && it.rank >= rule.rank }
}

internal sealed interface MatchSegment {
    data class Literal(val value: String) : MatchSegment
    data class Variable(val name: String) : MatchSegment
}

internal sealed interface MatchValue {
    data class Literal(val value: String) : MatchValue

    /** Параметр обязан присутствовать, значение любое. */
    data class Any(val name: String) : MatchValue
}

internal data class MatchParam(val name: String, val values: List<MatchValue>)

/** Разобранная строка `match` одного эндпоинта. */
internal class EndpointMatch(
    val method: String,
    val segments: List<MatchSegment>,
    val params: List<MatchParam>,
    val canonical: String,
    val rank: MatchRank,
) {
    fun pathMatches(request: ActualRequest): Boolean {
        if (request.method != method) return false
        if (request.segments.size != segments.size) return false
        return segments.indices.all { index ->
            when (val segment = segments[index]) {
                is MatchSegment.Literal -> segment.value == request.segments[index]
                is MatchSegment.Variable -> request.segments[index].isNotEmpty()
            }
        }
    }

    fun matches(request: ActualRequest): Boolean {
        if (!pathMatches(request)) return false
        return params.all { param ->
            val actual = request.params[param.name] ?: return@all false
            param.values.all { value ->
                when (value) {
                    is MatchValue.Literal -> value.value in actual
                    is MatchValue.Any -> true
                }
            }
        }
    }
}

internal class ReferenceDocument(
    val format: Int,
    val hash: String,
    val zenVersion: String,
    val updated: String,
    val volatileParams: Set<String>,
    val endpoints: List<Endpoint>,
) {
    fun endpoint(name: String): Endpoint? = endpoints.firstOrNull { it.name == name }

    /**
     * Подбирает эндпоинт для запроса: подходят все, у кого совпали путь и параметры
     * (порядок и значения не из `match` не важны). Побеждает самый специфичный.
     */
    fun match(request: ActualRequest): MatchResult? {
        var best: MatchResult? = null
        endpoints.forEach { endpoint ->
            if (endpoint.match.matches(request)) {
                val candidate = MatchResult(endpoint, endpoint.match.rank)
                val current = best
                if (current == null || candidate.rank > current.rank) {
                    best = candidate
                }
            }
        }
        return best
    }

    /** Эндпоинты, у которых совпал путь и метод независимо от параметров. */
    fun pathCandidates(request: ActualRequest): List<Endpoint> =
        endpoints.filter { it.match.pathMatches(request) }
}

/** Сколько шагов пути совпало у эндпоинта с реальным запросом: для выбора имени при new-variant. */
internal fun matchScore(endpoint: Endpoint, request: ActualRequest): Int {
    var score = 0
    endpoint.match.params.forEach { param ->
        val values = request.params[param.name] ?: return@forEach
        param.values.forEach { value ->
            when (value) {
                is MatchValue.Literal -> if (value.value in values) score++
                is MatchValue.Any -> score++
            }
        }
    }
    return score
}
