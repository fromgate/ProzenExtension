package zenapi

/**
 * Компактный JSON, который попадает в бандл. Ключи короткие: JSON встраивается
 * в каждый dev-бандл, и он же хранится в `storage.local` при сравнении хешей.
 *
 * Схема (format 1):
 *
 * ```
 * {
 *   "format": 1,
 *   "hash": "sha256:...",
 *   "meta": { "zenVersion": "...", "updated": "..." },
 *   "volatileParams": ["page", ...],
 *   "endpoints": [
 *     {
 *       "name": "stats2-flight-scr",
 *       "note": "...",
 *       "match": {
 *         "method": "GET",
 *         "segments": [ { "k": "lit", "v": "editor-api" }, { "k": "var", "v": "id" } ],
 *         "params": [ { "n": "fields", "v": ["impressions"] } ],
 *         "canonical": "GET ...",
 *         "specificity": [2, 3]
 *       },
 *       "used": [ { "path": [...], "types": ["object"], "optional": false, "specificity": [1, 0] } ],
 *       "ignored": [ { "path": [...], "reason": "...", "specificity": [2, 0] } ]
 *     }
 *   ]
 * }
 * ```
 *
 * Сегменты пути: `{ "k": "key", "n": "total" }`, `{ "k": "arr" }`, `{ "k": "any" }`,
 * `{ "k": "deep" }`. Параметр-переменная в `match` записан как `"{имя}"`.
 */
internal object ReferenceJson {

    fun write(document: ReferenceDocument, hash: String): String = buildString {
        append('{')
        append("\"format\":").append(document.meta.format)
        append(",\"hash\":").append(quote(hash))
        append(",\"meta\":").append(
            obj(
                "\"zenVersion\"" to quote(document.meta.zenVersion),
                "\"updated\"" to quote(document.meta.updated),
            )
        )
        append(",\"volatileParams\":").append(
            array(document.meta.volatileParams.map(::quote))
        )
        append(",\"endpoints\":").append(
            array(document.endpoints.map(::endpoint))
        )
        append('}')
    }

    private fun endpoint(endpoint: EndpointDefinition): String = obj(
        "\"name\"" to quote(endpoint.name),
        "\"note\"" to quote(endpoint.note ?: ""),
        "\"match\"" to match(endpoint.match),
        "\"used\"" to array(endpoint.used.map(::usedPath)),
        "\"ignored\"" to array(endpoint.ignored.map(::ignoredPath)),
    )

    private fun match(match: EndpointMatch): String = obj(
        "\"method\"" to quote(match.method),
        "\"segments\"" to array(
            match.segments.map { segment ->
                when (segment) {
                    is MatchSegment.Literal -> obj("\"k\"" to quote("lit"), "\"v\"" to quote(segment.value))
                    is MatchSegment.Variable -> obj("\"k\"" to quote("var"), "\"v\"" to quote(segment.name))
                }
            }
        ),
        "\"params\"" to array(
            match.params.map { param ->
                obj(
                    "\"n\"" to quote(param.name),
                    "\"v\"" to array(
                        param.values.map { value ->
                            when (value) {
                                is MatchParamValue.Literal -> quote(value.value)
                                is MatchParamValue.Any -> quote("{${value.name}}")
                            }
                        }
                    ),
                )
            }
        ),
        "\"canonical\"" to quote(match.canonical),
        "\"specificity\"" to array(listOf(match.specificity.literals.toString(), match.specificity.params.toString())),
    )

    private fun usedPath(used: UsedPath): String = obj(
        "\"path\"" to path(used.path),
        "\"types\"" to array(used.type.wireTypes().map(::quote)),
        "\"optional\"" to used.type.optional.toString(),
        "\"specificity\"" to array(
            listOf(used.path.specificity.literals.toString(), used.path.specificity.wildcards.toString())
        ),
    )

    private fun ignoredPath(ignored: IgnoredPath): String = obj(
        "\"path\"" to path(ignored.path),
        "\"reason\"" to quote(ignored.reason),
        "\"specificity\"" to array(
            listOf(ignored.path.specificity.literals.toString(), ignored.path.specificity.wildcards.toString())
        ),
    )

    private fun path(path: ReferencePath): String = array(
        path.segments.map { segment ->
            when (segment) {
                is PathSegment.Key -> obj("\"k\"" to quote("key"), "\"n\"" to quote(segment.name))
                PathSegment.ArrayItem -> obj("\"k\"" to quote("arr"))
                PathSegment.AnyKey -> obj("\"k\"" to quote("any"))
                PathSegment.Deep -> obj("\"k\"" to quote("deep"))
            }
        }
    )

    private fun obj(vararg fields: Pair<String, String>): String =
        fields.joinToString(",", "{", "}") { (name, value) -> "$name:$value" }

    private fun obj(fields: List<Pair<String, String>>): String =
        fields.joinToString(",", "{", "}") { (name, value) -> "$name:$value" }

    private fun array(items: List<String>): String = items.joinToString(",", "[", "]")

    private fun quote(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char < ' ') {
                    append("\\u").append("%04x".format(char.code))
                } else {
                    append(char)
                }
            }
        }
        append('"')
    }
}
