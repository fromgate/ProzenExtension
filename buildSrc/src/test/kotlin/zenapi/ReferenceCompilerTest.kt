package zenapi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ReferenceCompilerTest {

    // ------------------------------------------------------------------ helpers

    private fun reference(
        vararg endpoints: String,
        volatileParams: String = "\"page\"",
    ): String = buildString {
        appendLine(
            """
            [meta]
            format = 1
            zenVersion = "2.0.0"
            updated = 2026-10-31
            volatileParams = [$volatileParams]
            """.trimIndent()
        )
        appendLine()
        endpoints.forEach { endpoint ->
            appendLine(endpoint.trimIndent())
            appendLine()
        }
    }

    private fun endpoint(
        name: String = "sample",
        match: String = "GET editor-api/v2/sample/{id}",
        used: Map<String, String> = mapOf("id" to "string"),
        ignored: Map<String, String> = emptyMap(),
        unknown: Map<String, String> = emptyMap(),
        monitor: Map<String, String> = emptyMap(),
        monitorValues: Map<String, List<String>> = emptyMap(),
    ): String = buildString {
        appendLine("[endpoints.$name]")
        appendLine("match = \"$match\"")
        if (used.isNotEmpty()) {
            appendLine("[endpoints.$name.used]")
            used.forEach { (path, type) -> appendLine("\"$path\" = \"$type\"") }
        }
        if (ignored.isNotEmpty()) {
            appendLine("[endpoints.$name.ignored]")
            ignored.forEach { (path, reason) -> appendLine("\"$path\" = \"$reason\"") }
        }
        if (unknown.isNotEmpty()) {
            appendLine("[endpoints.$name.unknown]")
            unknown.forEach { (path, type) -> appendLine("\"$path\" = \"$type\"") }
        }
        if (monitor.isNotEmpty()) {
            appendLine("[endpoints.$name.monitor]")
            monitor.forEach { (path, type) -> appendLine("\"$path\" = \"$type\"") }
        }
        if (monitorValues.isNotEmpty()) {
            appendLine("[endpoints.$name.monitor.values]")
            monitorValues.forEach { (path, values) ->
                appendLine("\"$path\" = [${values.joinToString(", ") { "\"$it\"" }}]")
            }
        }
    }

    private fun assertError(contains: String, block: () -> Unit) {
        val error = assertThrows(ReferenceException::class.java, block)
        assertTrue(
            error.errors.any { it.contains(contains) },
            "Ожидалась ошибка, содержащая \"$contains\", получено:\n${error.errors.joinToString("\n")}",
        )
    }

    // -------------------------------------------------------------------- good

    @Test
    fun `valid reference compiles`() {
        val compilation = ReferenceCompiler.compile(
            reference(
                endpoint(used = mapOf("publisher" to "object", "publisher.regTime" to "number?")),
            )
        )

        assertEquals(1, compilation.document.endpoints.size)
        assertTrue(compilation.hash.startsWith("sha256:"))
        assertTrue(compilation.json.contains("\"format\":1"))
        assertTrue(compilation.json.contains("\"canonical\":\"GET editor-api/v2/sample/{id}\""))
        assertTrue(compilation.json.contains("\"optional\":true"))
    }

    @Test
    fun `real reference file compiles`() {
        val file = listOf(File("../reference/zen-api.toml"), File("reference/zen-api.toml"))
            .firstOrNull { it.isFile }
            ?: error("reference/zen-api.toml не найден относительно ${File(".").absolutePath}")

        val compilation = ReferenceCompiler.compile(file.readText())
        assertEquals(10, compilation.document.endpoints.size)
        assertTrue(compilation.document.endpoints.any { it.name == "publications-list" })
        assertTrue(compilation.document.endpoints.any { it.name == "publication-view-stat" })
    }

    @Test
    fun `endpoint without used and ignored is allowed`() {
        val compilation = ReferenceCompiler.compile(reference(endpoint(used = emptyMap())))
        assertEquals(0, compilation.document.endpoints.single().used.size)
    }

    // ---------------------------------------------------------------- validation

    @Test
    fun `empty ignored reason is an error`() {
        assertError("пустая причина") {
            ReferenceCompiler.compile(
                reference(endpoint(ignored = mapOf("extra.**" to " ")))
            )
        }
    }

    @Test
    fun `unknown type is an error`() {
        assertError("неизвестный тип") {
            ReferenceCompiler.compile(reference(endpoint(used = mapOf("id" to "text"))))
        }
    }

    @Test
    fun `deep wildcard must be last`() {
        assertError("только последним") {
            ReferenceCompiler.compile(reference(endpoint(ignored = mapOf("a.**.b" to "причина"))))
        }
    }

    @Test
    fun `deep wildcard is forbidden in used`() {
        assertError("только в ignored") {
            ReferenceCompiler.compile(reference(endpoint(used = mapOf("a.**" to "object"))))
        }
    }

    @Test
    fun `array item cannot be terminal`() {
        assertError("не может быть последним") {
            ReferenceCompiler.compile(reference(endpoint(used = mapOf("a[]" to "array"))))
        }
    }

    @Test
    fun `by field is rejected`() {
        val body = """
            [endpoints.sample]
            match = "GET editor-api/v2/sample/{id}"
            [endpoints.sample.used]
            "id" = { type = "string", by = ["getSample"] }
        """.trimIndent()

        assertError("by") { ReferenceCompiler.compile(reference(body)) }
    }

    @Test
    fun `invalid endpoint name is an error`() {
        assertError("A-Za-z0-9_-") {
            ReferenceCompiler.compile(reference(endpoint(name = "\"bad.name\"")))
        }
    }

    @Test
    fun `unknown endpoint key is an error`() {
        val body = """
            [endpoints.sample]
            match = "GET editor-api/v2/sample/{id}"
            use = "..."
        """.trimIndent()

        assertError("неизвестный ключ \"use\"") { ReferenceCompiler.compile(reference(body)) }
    }

    // ------------------------------------------------------------------- match

    @Test
    fun `duplicate match ignoring parameter order is an error`() {
        assertError("одинаковый match") {
            ReferenceCompiler.compile(
                reference(
                    endpoint(name = "one", match = "GET x/y?a=1&b=2"),
                    endpoint(name = "two", match = "GET x/y?b=2&a=1", used = emptyMap()),
                )
            )
        }
    }

    @Test
    fun `ambiguous match with equal specificity is an error`() {
        assertError("могут совпасть") {
            ReferenceCompiler.compile(
                reference(
                    endpoint(name = "one", match = "GET x/{id}/y?k=1"),
                    endpoint(name = "two", match = "GET x/{other}/y?k=1", used = emptyMap()),
                )
            )
        }
    }

    @Test
    fun `different literal parameter values are not ambiguous`() {
        val compilation = ReferenceCompiler.compile(
            reference(
                endpoint(name = "one", match = "GET x/y?fields=views&groupBy=flight"),
                endpoint(name = "two", match = "GET x/y?fields=impressions&groupBy=flight", used = emptyMap()),
            )
        )
        assertEquals(2, compilation.document.endpoints.size)
    }

    @Test
    fun `volatile param in match produces warning`() {
        val compilation = ReferenceCompiler.compile(
            reference(
                endpoint(match = "GET x/y?page=1", used = emptyMap()),
                volatileParams = "\"page\"",
            )
        )
        assertTrue(compilation.warnings.any { it.contains("\"page\"") }, compilation.warnings.toString())
    }

    @Test
    fun `query values are percent decoded`() {
        val compilation = ReferenceCompiler.compile(
            reference(endpoint(match = "GET x?q=%D0%B4%D0%B5%D0%BD%D1%8C&r=a+b", used = emptyMap()))
        )
        val match = compilation.document.endpoints.single().match
        assertEquals(setOf("день"), match.literalValues("q"))
        assertEquals(setOf("a b"), match.literalValues("r"))
    }

    @Test
    fun `canonical sorts parameters and repeated values`() {
        val compilation = ReferenceCompiler.compile(
            reference(endpoint(match = "GET x?b=2&fields=vtr&a=1&fields=ctr", used = emptyMap()))
        )
        assertEquals(
            "GET x?a=1&b=2&fields=ctr&fields=vtr",
            compilation.document.endpoints.single().match.canonical,
        )
    }

    @Test
    fun `broken percent escape is an error`() {
        assertError("%-код") {
            ReferenceCompiler.compile(reference(endpoint(match = "GET x?q=%ZZ", used = emptyMap())))
        }
    }

    // ------------------------------------------------------------------- paths

    @Test
    fun `same path in used and ignored is an error`() {
        assertError("одновременно в used и ignored") {
            ReferenceCompiler.compile(
                reference(
                    endpoint(
                        used = mapOf("a.b" to "object"),
                        ignored = mapOf("a.b" to "пояснение"),
                    )
                )
            )
        }
    }

    @Test
    fun `more specific ignored path is allowed`() {
        val compilation = ReferenceCompiler.compile(
            reference(
                endpoint(
                    used = mapOf("a.b" to "object"),
                    ignored = mapOf("a.*" to "соседи"),
                )
            )
        )
        assertEquals(1, compilation.document.endpoints.single().ignored.size)
    }

    @Test
    fun `array paths are parsed into segments`() {
        val compilation = ReferenceCompiler.compile(
            reference(
                endpoint(
                    used = mapOf(
                        "publications[].content.images[].urlTemplate" to "string?",
                        "[].id" to "string",
                    )
                )
            )
        )
        val paths = compilation.document.endpoints.single().used.map { it.path.canonical }
        assertEquals(
            listOf("publications[].content.images[].urlTemplate", "[].id"),
            paths,
        )
    }

    @Test
    fun `escaped dot stays inside key name`() {
        val path = PathSyntax.parse("""a\.b.c""")
        assertEquals(listOf("a.b", "c"), path.segments.map { (it as PathSegment.Key).name })
        assertEquals("""a\.b.c""", path.canonical)
    }

    // ----------------------------------------------------------------- unknown

    @Test
    fun `unknown paths and types are parsed`() {
        val compilation = ReferenceCompiler.compile(
            reference(
                endpoint(
                    unknown = mapOf(
                        "payload" to "object",
                        "items[].id" to "string?",
                        "count" to "number",
                    )
                )
            )
        )
        val unknown = compilation.document.endpoints.single().unknown
        assertEquals(listOf("payload", "items[].id", "count"), unknown.map { it.path.canonical })
        assertEquals(setOf(JsonType.NUMBER), unknown.first { it.path.canonical == "count" }.type.types)
        assertTrue(unknown.first { it.path.canonical == "items[].id" }.type.optional)
    }

    @Test
    fun `empty unknown table is allowed`() {
        val body = """
            [endpoints.sample]
            match = "GET editor-api/v2/sample/{id}"
            [endpoints.sample.used]
            "id" = "string"
            [endpoints.sample.unknown]
        """.trimIndent()
        val compilation = ReferenceCompiler.compile(reference(body))
        assertEquals(0, compilation.document.endpoints.single().unknown.size)
    }

    @Test
    fun `unknown type can be an extended record`() {
        val body = """
            [endpoints.sample]
            match = "GET editor-api/v2/sample/{id}"
            [endpoints.sample.unknown]
            "id" = { type = "number|null" }
        """.trimIndent()
        val compilation = ReferenceCompiler.compile(reference(body))
        assertEquals(
            setOf(JsonType.NUMBER, JsonType.NULL),
            compilation.document.endpoints.single().unknown.single().type.types,
        )
    }

    @Test
    fun `wildcards are forbidden in unknown`() {
        assertError("путь должен быть точным") {
            ReferenceCompiler.compile(reference(endpoint(unknown = mapOf("items.*" to "object"))))
        }
        assertError("путь должен быть точным") {
            ReferenceCompiler.compile(reference(endpoint(unknown = mapOf("items.**" to "object"))))
        }
    }

    @Test
    fun `same path in used and unknown is an error`() {
        assertError("одновременно в used и unknown") {
            ReferenceCompiler.compile(
                reference(endpoint(used = mapOf("a.b" to "object"), unknown = mapOf("a.b" to "object")))
            )
        }
    }

    @Test
    fun `same path in ignored and unknown is an error`() {
        assertError("одновременно в ignored и unknown") {
            ReferenceCompiler.compile(
                reference(endpoint(ignored = mapOf("a.b" to "причина"), unknown = mapOf("a.b" to "object")))
            )
        }
    }

    @Test
    fun `unknown covered by ignored produces warning`() {
        val compilation = ReferenceCompiler.compile(
            reference(
                endpoint(
                    ignored = mapOf("publisher.*" to "не используем"),
                    unknown = mapOf("publisher.name" to "string"),
                )
            )
        )
        assertTrue(
            compilation.warnings.any { it.contains("уже покрыт ignored") },
            compilation.warnings.toString(),
        )
    }

    @Test
    fun `unknown path under ignored parent produces warning`() {
        val compilation = ReferenceCompiler.compile(
            reference(
                endpoint(
                    ignored = mapOf("publisher" to "не используем"),
                    unknown = mapOf("publisher.name" to "string"),
                )
            )
        )
        assertTrue(compilation.warnings.any { it.contains("уже покрыт ignored") }, compilation.warnings.toString())
    }

    @Test
    fun `emitted json contains unknown paths`() {
        val compilation = ReferenceCompiler.compile(
            reference(endpoint(unknown = mapOf("items[].extra" to "number")))
        )
        val root = Json.parseToJsonElement(compilation.json).jsonObject
        val unknown = root["endpoints"]!!.jsonArray.single().jsonObject["unknown"]!!.jsonArray.single().jsonObject
        assertEquals(listOf("number"), unknown["types"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("extra", unknown["path"]!!.jsonArray.last().jsonObject["n"]!!.jsonPrimitive.content)
        assertEquals(listOf(2, 0), unknown["specificity"]!!.jsonArray.map { it.jsonPrimitive.int })
    }

    @Test
    fun `empty unknown is present in json`() {
        val compilation = ReferenceCompiler.compile(reference(endpoint()))
        assertTrue(compilation.json.contains("\"unknown\":[]"), compilation.json)
    }

    // ----------------------------------------------------------------- monitor

    @Test
    fun `monitor paths and types are parsed`() {
        val compilation = ReferenceCompiler.compile(
            reference(
                endpoint(
                    monitor = mapOf(
                        "publications[].status" to "string|null?",
                        "items[].tags" to "array",
                        "payload" to "object",
                        "counters.*.kind" to "any",
                    )
                )
            )
        )
        val monitor = compilation.document.endpoints.single().monitor
        assertEquals(
            listOf("publications[].status", "items[].tags", "payload", "counters.*.kind"),
            monitor.map { it.path.canonical },
        )
        assertEquals(setOf(JsonType.STRING, JsonType.NULL), monitor.first().type.types)
        assertTrue(monitor.first().type.optional)
    }

    @Test
    fun `deep wildcard is forbidden in monitor`() {
        assertError("только в ignored") {
            ReferenceCompiler.compile(reference(endpoint(monitor = mapOf("a.**" to "object"))))
        }
    }

    @Test
    fun `monitor values are parsed`() {
        val compilation = ReferenceCompiler.compile(
            reference(
                endpoint(
                    monitor = mapOf("publications[].status" to "string|null?"),
                    monitorValues = mapOf("publications[].status" to listOf("banned", "published")),
                )
            )
        )
        val values = compilation.document.endpoints.single().monitorValues.single()
        assertEquals("publications[].status", values.path.canonical)
        assertEquals(listOf("banned", "published"), values.values)
    }

    @Test
    fun `monitor values require declared monitor path`() {
        assertError("не объявлен в monitor") {
            ReferenceCompiler.compile(
                reference(endpoint(monitorValues = mapOf("extra" to listOf("a"))))
            )
        }
    }

    @Test
    fun `wildcards are forbidden in monitor values`() {
        assertError("путь должен быть точным") {
            ReferenceCompiler.compile(
                reference(
                    endpoint(
                        monitor = mapOf("items[].*" to "string"),
                        monitorValues = mapOf("items[].*" to listOf("a")),
                    )
                )
            )
        }
    }

    @Test
    fun `duplicate monitor value is an error`() {
        assertError("повторяется") {
            ReferenceCompiler.compile(
                reference(
                    endpoint(
                        monitor = mapOf("status" to "string"),
                        monitorValues = mapOf("status" to listOf("a", "a")),
                    )
                )
            )
        }
    }

    @Test
    fun `empty monitor values table is allowed`() {
        val body = """
            [endpoints.sample]
            match = "GET editor-api/v2/sample/{id}"
            [endpoints.sample.monitor]
            "id" = "string"
            [endpoints.sample.monitor.values]
        """.trimIndent()
        val compilation = ReferenceCompiler.compile(reference(body))
        assertEquals(1, compilation.document.endpoints.single().monitor.size)
        assertEquals(0, compilation.document.endpoints.single().monitorValues.size)
    }

    @Test
    fun `emitted json contains monitor and monitor values`() {
        val compilation = ReferenceCompiler.compile(
            reference(
                endpoint(
                    monitor = mapOf("publications[].status" to "string|null?"),
                    monitorValues = mapOf("publications[].status" to listOf("banned", "published")),
                )
            )
        )
        val endpoint = Json.parseToJsonElement(compilation.json).jsonObject["endpoints"]!!.jsonArray
            .single().jsonObject
        val monitor = endpoint["monitor"]!!.jsonArray.single().jsonObject
        assertEquals(listOf("null", "string"), monitor["types"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(true, monitor["optional"]!!.jsonPrimitive.boolean)
        val values = endpoint["monitorValues"]!!.jsonObject
        assertEquals(
            listOf("banned", "published"),
            values["publications[].status"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `empty monitor is present in json`() {
        val compilation = ReferenceCompiler.compile(reference(endpoint()))
        assertTrue(compilation.json.contains("\"monitor\":[]"), compilation.json)
        assertTrue(compilation.json.contains("\"monitorValues\":{}"), compilation.json)
    }

    // -------------------------------------------------------------------- hash

    @Test
    fun `hash does not depend on comments and formatting`() {
        val first = reference(endpoint())
        val second = "# комментарий\n\n" + first.replace("format = 1", "format    =    1")
        assertEquals(
            ReferenceCompiler.compile(first).hash,
            ReferenceCompiler.compile(second).hash,
        )
    }

    @Test
    fun `hash changes when reference changes`() {
        val first = ReferenceCompiler.compile(reference(endpoint(used = mapOf("id" to "string"))))
        val second = ReferenceCompiler.compile(reference(endpoint(used = mapOf("id" to "number"))))
        assertFalse(first.hash == second.hash)
    }

    // ------------------------------------------------------------------ source

    @Test
    fun `kotlin source contains json and hash`() {
        val compilation = ReferenceCompiler.compile(reference(endpoint()))
        val source = compilation.toKotlinSource()
        assertTrue(source.contains("package devtools"))
        assertTrue(source.contains("ZEN_API_REFERENCE_HASH"))
        assertTrue(source.contains(compilation.hash))
        assertNotNull(compilation.json)
    }

    @Test
    fun `emitted json is valid and self consistent`() {
        val compilation = ReferenceCompiler.compile(
            reference(
                endpoint(
                    used = mapOf("publications" to "array", "publisher.regTime" to "number?"),
                    ignored = mapOf("publications[].**" to "не нужно"),
                )
            )
        )

        val root = Json.parseToJsonElement(compilation.json).jsonObject
        assertEquals(1, root["format"]!!.jsonPrimitive.int)
        assertEquals(compilation.hash, root["hash"]!!.jsonPrimitive.content)

        val endpoint = root["endpoints"]!!.jsonArray.single().jsonObject
        assertEquals("sample", endpoint["name"]!!.jsonPrimitive.content)
        assertEquals(
            "GET editor-api/v2/sample/{id}",
            endpoint["match"]!!.jsonObject["canonical"]!!.jsonPrimitive.content,
        )

        val publishers = endpoint["used"]!!.jsonArray
            .map { it.jsonObject }
            .first { it["path"]!!.jsonArray.first().jsonObject["n"]!!.jsonPrimitive.content == "publisher" }
        assertTrue(publishers["optional"]!!.jsonPrimitive.boolean)

        val ignoredPath = endpoint["ignored"]!!.jsonArray.single().jsonObject["path"]!!.jsonArray
        assertEquals("deep", ignoredPath.last().jsonObject["k"]!!.jsonPrimitive.content)
    }

    @Test
    fun `dollar sign in reasons is escaped for kotlin raw string`() {
        val compilation = ReferenceCompiler.compile(
            reference(endpoint(ignored = mapOf("extra.**" to "цена \$100")))
        )
        val source = compilation.toKotlinSource()
        assertTrue(source.contains("цена \${'$'}100"), source)
    }

    @Test
    fun `kotlin source contains raw reference text`() {
        val input = reference(endpoint())
        val compilation = ReferenceCompiler.compile(input)
        val source = compilation.toKotlinSource()
        val body = source
            .substringAfter("internal const val ZEN_API_REFERENCE_TOML: String = \"\"\"")
            .substringBefore("\"\"\"")
        assertEquals(input, body)
    }

    @Test
    fun `dollar sign in raw reference is escaped`() {
        val input = reference(endpoint(ignored = mapOf("extra.**" to "цена \$100")))
        val compilation = ReferenceCompiler.compile(input)
        val source = compilation.toKotlinSource()
        assertTrue(source.contains("цена \${'$'}100"), source)
    }

    @Test
    fun `triple quote in reference fails source generation`() {
        val body = "[endpoints.sample]\n" +
            "match = \"GET editor-api/v2/sample/{id}\"\n" +
            "[endpoints.sample.ignored]\n" +
            "\"extra.**\" = 'три \"\"\" кавычки'\n"
        val compilation = ReferenceCompiler.compile(reference(body))
        assertThrows(IllegalArgumentException::class.java) { compilation.toKotlinSource() }
    }
}
