package devtools

/**
 * Шаги пути в эталоне (`used`/`ignored`) и в реальном ответе.
 *
 * Для реального пути используются только `Key` и `ArrayItem`: элементы массива
 * сливаются, поэтому индексов нет. Wildcards и `Deep` встречаются только
 * в правилах эталона.
 */
internal sealed interface PathStep {

    /** Обычный ключ объекта. */
    data class Key(val name: String) : PathStep

    /** `[]`: любой элемент массива. */
    object ArrayItem : PathStep

    /** `*`: любой ключ на одном уровне. */
    object AnyKey : PathStep

    /** `**`: всё поддерево. Только последним сегментом и только в `ignored`. */
    object Deep : PathStep
}

/** «Специфичность» пути: больше литеральных сегментов, затем больше `*`. */
internal data class PathRank(val literals: Int, val wildcards: Int) : Comparable<PathRank> {
    override fun compareTo(other: PathRank): Int =
        compareValuesBy(this, other, { it.literals }, { it.wildcards })
}

private fun List<PathStep>.rank(): PathRank = PathRank(
    literals = count { it is PathStep.Key },
    wildcards = count { it is PathStep.AnyKey },
)

internal val List<PathStep>.pathRank: PathRank
    get() = rank()

/** Человекочитаемый путь (`total.intervals[].timestamp`, `items.*`). */
internal fun formatPath(steps: List<PathStep>): String = buildString {
    steps.forEach { step ->
        when (step) {
            is PathStep.Key -> {
                if (isNotEmpty()) append('.')
                step.name.replace("\\", "\\\\").replace(".", "\\.").let(::append)
            }

            PathStep.ArrayItem -> append("[]")
            PathStep.AnyKey -> {
                if (isNotEmpty()) append('.')
                append('*')
            }

            PathStep.Deep -> {
                if (isNotEmpty()) append('.')
                append("**")
            }
        }
    }
}

/**
 * Разбирает человекочитаемый путь обратно в шаги.
 *
 * В пути, записанном в расхождении, wildcards встречаться не обязаны: `parsePath`
 * понимает и фактические пути (только ключи и `[]`).
 */
internal fun parsePath(text: String): List<PathStep> {
    if (text.isEmpty()) return emptyList()

    val steps = mutableListOf<PathStep>()
    val current = StringBuilder()
    var hasCurrent = false
    fun flush() {
        if (hasCurrent) {
            steps += PathStep.Key(current.toString())
            current.setLength(0)
            hasCurrent = false
        }
    }

    var index = 0
    while (index < text.length) {
        when (val char = text[index]) {
            '\\' -> {
                if (index + 1 < text.length) {
                    current.append(text[index + 1])
                    hasCurrent = true
                    index += 2
                } else {
                    index++
                }
            }

            '.' -> {
                flush()
                index++
            }

            '[' -> {
                if (index + 1 < text.length && text[index + 1] == ']') {
                    flush()
                    steps += PathStep.ArrayItem
                    index += 2
                } else {
                    current.append(char)
                    hasCurrent = true
                    index++
                }
            }

            '*' -> {
                flush()
                if (index + 1 < text.length && text[index + 1] == '*') {
                    steps += PathStep.Deep
                    index += 2
                } else {
                    steps += PathStep.AnyKey
                    index++
                }
            }

            else -> {
                current.append(char)
                hasCurrent = true
                index++
            }
        }
    }
    flush()
    return steps
}

/**
 * Сравнивает сегменты правил по одной позиции: `AnyKey` совместим и с ключом,
 * и с `*`; `Key` — только с точно таким же ключом или с `*`.
 */
internal fun patternSegmentsMatch(a: PathStep, b: PathStep): Boolean = when {
    a is PathStep.Key && b is PathStep.Key -> a.name == b.name
    a is PathStep.Key && b is PathStep.AnyKey -> true
    a is PathStep.AnyKey && b is PathStep.Key -> true
    a is PathStep.AnyKey && b is PathStep.AnyKey -> true
    a === PathStep.ArrayItem && b === PathStep.ArrayItem -> true
    else -> false
}

/** Сравнивает два пути правил целиком (одна длина, все сегменты совпадают). */
internal fun patternPathsEqual(a: List<PathStep>, b: List<PathStep>): Boolean {
    if (a.size != b.size) return false
    return a.indices.all { patternSegmentsMatch(a[it], b[it]) }
}

/**
 * `ignored` покрывает реальный путь: правило совпадает либо с самим путём,
 * либо с любым его префиксом (и тогда скрывает все поддеревья под ним).
 */
internal fun ignoredCovers(pattern: List<PathStep>, actual: List<PathStep>): Boolean {
    if (pattern.isEmpty()) return false
    val length = if (pattern.last() === PathStep.Deep) pattern.size - 1 else pattern.size
    if (actual.size < length) return false
    return (0 until length).all { actualStepMatches(actual[it], pattern[it]) }
}

/** Правило может лежать под этим узлом: путь правила начинается с `actual`. */
internal fun usedIsBelow(pattern: List<PathStep>, actual: List<PathStep>): Boolean {
    if (pattern.size < actual.size) return false
    return actual.indices.all { actualStepMatches(actual[it], pattern[it]) }
}

/** Правило совпадает с реальным путём ровно. */
internal fun usedMatches(pattern: List<PathStep>, actual: List<PathStep>): Boolean =
    pattern.size == actual.size && actual.indices.all { actualStepMatches(actual[it], pattern[it]) }

/** Совпадение одного фактического сегмента с сегментом правила. */
internal fun actualStepMatches(actual: PathStep, pattern: PathStep): Boolean = when (pattern) {
    is PathStep.Key -> actual is PathStep.Key && actual.name == pattern.name
    PathStep.AnyKey -> actual is PathStep.Key
    PathStep.ArrayItem -> actual === PathStep.ArrayItem
    PathStep.Deep -> true
}

/**
 * Является ли `ancestor` предком `descendant` в дереве правил
 * (нужен для того, чтобы не порождать каскад одинаковых `missing`).
 */
internal fun isAncestorPattern(ancestor: List<PathStep>, descendant: List<PathStep>): Boolean {
    if (ancestor.size >= descendant.size) return false
    return ancestor.indices.all { patternSegmentsMatch(ancestor[it], descendant[it]) }
}
