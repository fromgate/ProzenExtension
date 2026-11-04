package zenapi

/**
 * Модель эталона API Дзена.
 *
 * Здесь только разобранные данные: пути уже разбиты на сегменты, типы — на набор
 * допустимых типов, `match` — на метод, сегменты пути и параметры. Строковое
 * представление (TOML) остаётся в `reference/zen-api.toml`.
 */

enum class JsonType(val wire: String) {
    STRING("string"),
    NUMBER("number"),
    BOOLEAN("boolean"),
    OBJECT("object"),
    ARRAY("array"),
    NULL("null"),
    ANY("any");

    companion object {
        fun byWire(value: String): JsonType? = values().firstOrNull { it.wire == value }
    }
}

/** Разобранный тип из `used`: набор допустимых типов и признак необязательности. */
data class TypeSpec(val types: Set<JsonType>, val optional: Boolean) {
    fun wireTypes(): List<String> = types.map { it.wire }.sorted()
}

sealed interface PathSegment {
    /** Обычный ключ объекта. */
    data class Key(val name: String) : PathSegment

    /** `[]`: любой элемент массива, у которого путь продолжается. */
    object ArrayItem : PathSegment

    /** `*`: любой ключ на одном уровне. */
    object AnyKey : PathSegment

    /** `**`: всё поддерево. Только последним сегментом и только в `ignored`. */
    object Deep : PathSegment
}

/** Специфичность пути: больше литеральных сегментов, затем больше `*`. */
data class PathSpecificity(val literals: Int, val wildcards: Int) : Comparable<PathSpecificity> {
    override fun compareTo(other: PathSpecificity): Int =
        compareValuesBy(this, other, { it.literals }, { it.wildcards })
}

data class ReferencePath(val segments: List<PathSegment>) {
    val canonical: String = buildString {
        segments.forEach { segment ->
            when (segment) {
                is PathSegment.Key -> {
                    if (isNotEmpty()) append('.')
                    append(segment.name.replace(".", "\\."))
                }

                PathSegment.ArrayItem -> append("[]")
                PathSegment.AnyKey -> {
                    if (isNotEmpty()) append('.')
                    append('*')
                }

                PathSegment.Deep -> {
                    if (isNotEmpty()) append('.')
                    append("**")
                }
            }
        }
    }

    val specificity: PathSpecificity = PathSpecificity(
        literals = segments.count { it is PathSegment.Key },
        wildcards = segments.count { it == PathSegment.AnyKey },
    )
}

sealed interface MatchSegment {
    data class Literal(val value: String) : MatchSegment
    data class Variable(val name: String) : MatchSegment
}

sealed interface MatchParamValue {
    data class Literal(val value: String) : MatchParamValue
    data class Any(val name: String) : MatchParamValue
}

data class MatchParam(val name: String, val values: List<MatchParamValue>) {
    fun literals(): Set<String> = values.filterIsInstance<MatchParamValue.Literal>().map { it.value }.toSet()
    fun hasLiterals(): Boolean = values.any { it is MatchParamValue.Literal }
}

/** Специфичность match: больше литеральных параметров, затем больше параметров вообще. */
data class MatchSpecificity(val literals: Int, val params: Int) : Comparable<MatchSpecificity> {
    override fun compareTo(other: MatchSpecificity): Int =
        compareValuesBy(this, other, { it.literals }, { it.params })
}

data class EndpointMatch(
    val method: String,
    /** Сегменты пути, отсортированными не бывают: порядок значим. */
    val segments: List<MatchSegment>,
    /** Параметры отсортированы по имени, значения — по строковому представлению. */
    val params: List<MatchParam>,
) {
    val canonical: String = buildString {
        append(method)
        append(' ')
        segments.forEachIndexed { index, segment ->
            if (index > 0) append('/')
            when (segment) {
                is MatchSegment.Literal -> append(segment.value)
                is MatchSegment.Variable -> append('{').append(segment.name).append('}')
            }
        }
        if (params.isNotEmpty()) {
            append('?')
            append(
                params.flatMap { param ->
                    param.values.map { value ->
                        param.name + "=" + when (value) {
                            is MatchParamValue.Literal -> MatchSyntax.encodeQueryValue(value.value)
                            is MatchParamValue.Any -> "{" + value.name + "}"
                        }
                    }
                }.joinToString("&")
            )
        }
    }

    val specificity: MatchSpecificity = MatchSpecificity(
        literals = params.count { it.hasLiterals() },
        params = params.size,
    )

    fun param(name: String): MatchParam? = params.firstOrNull { it.name == name }

    fun literalValues(name: String): Set<String>? = param(name)?.literals()?.takeIf { it.isNotEmpty() }
}

data class Meta(
    val format: Int,
    val zenVersion: String,
    val updated: String,
    val volatileParams: List<String>,
)

data class UsedPath(val path: ReferencePath, val type: TypeSpec)

data class IgnoredPath(val path: ReferencePath, val reason: String)

/** Наблюдённый, но ещё не разобранный путь: временный склад для ручного разбора. */
data class UnknownPath(val path: ReferencePath, val type: TypeSpec)

/** Путь в `monitor`: валидатор собирает значения, которые встречаются в ответе. */
data class MonitorPath(val path: ReferencePath, val type: TypeSpec)

/** Найденные значения пути `monitor`: секция пополняется копированием схемы. */
data class MonitorValues(val path: ReferencePath, val values: List<String>)

data class EndpointDefinition(
    val name: String,
    val note: String?,
    val match: EndpointMatch,
    val used: List<UsedPath>,
    val ignored: List<IgnoredPath>,
    val unknown: List<UnknownPath>,
    val monitor: List<MonitorPath> = emptyList(),
    val monitorValues: List<MonitorValues> = emptyList(),
)

data class ReferenceDocument(
    val meta: Meta,
    val endpoints: List<EndpointDefinition>,
)

/** Ошибки эталона. Показываются разом, чтобы не чинить файл по одной. */
class ReferenceException(val errors: List<String>) : RuntimeException(errors.joinToString("\n"))
