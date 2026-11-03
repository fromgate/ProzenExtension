package zenapi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyntaxTest {

    private fun assertSyntaxError(contains: String, block: () -> Unit) {
        val error = assertThrows(SyntaxException::class.java, block)
        assertTrue(
            error.message.orEmpty().contains(contains),
            "Ожидалась ошибка, содержащая \"$contains\", получено: \"${error.message}\"",
        )
    }

    // ------------------------------------------------------------------- paths

    @Test
    fun `wildcards and arrays are parsed`() {
        assertEquals(
            listOf("items", "*", "published"),
            PathSyntax.parse("items.*.published").segments.map {
                when (it) {
                    is PathSegment.Key -> it.name
                    PathSegment.AnyKey -> "*"
                    PathSegment.ArrayItem -> "[]"
                    PathSegment.Deep -> "**"
                }
            },
        )
        assertEquals("publications[].**", PathSyntax.parse("publications[].**").canonical)
    }

    @Test
    fun `unknown escape is rejected`() {
        assertSyntaxError("неизвестное экранирование") { PathSyntax.parse("""a\b""") }
    }

    @Test
    fun `trailing escape is rejected`() {
        assertSyntaxError("незавершённое экранирование") { PathSyntax.parse("""a\""") }
    }

    @Test
    fun `empty segment is rejected`() {
        assertSyntaxError("пустой сегмент") { PathSyntax.parse("a..b") }
    }

    @Test
    fun `wildcard inside name is rejected`() {
        assertSyntaxError("отдельным сегментом") { PathSyntax.parse("a*b") }
    }

    @Test
    fun `deep in the middle is rejected`() {
        assertSyntaxError("только последним") { PathSyntax.parse("a.**.b") }
    }

    @Test
    fun `standalone array item only at the beginning`() {
        assertSyntaxError("только в начале") { PathSyntax.parse("a.[].b") }
    }

    @Test
    fun `specificity counts literals and wildcards`() {
        val path = PathSyntax.parse("a.b.*")
        assertEquals(PathSpecificity(2, 1), path.specificity)
    }

    // ------------------------------------------------------------------- types

    @Test
    fun `union and optional are parsed`() {
        val type = TypeSyntax.parse("string|null?")
        assertEquals(setOf(JsonType.STRING, JsonType.NULL), type.types)
        assertTrue(type.optional)
    }

    @Test
    fun `any cannot be combined`() {
        assertSyntaxError("нельзя объединять") { TypeSyntax.parse("any|string") }
    }

    @Test
    fun `duplicate type is rejected`() {
        assertSyntaxError("повторяется") { TypeSyntax.parse("string|string") }
    }

    @Test
    fun `optional mark only at the end`() {
        assertSyntaxError("только в конце") { TypeSyntax.parse("string?|number") }
    }

    // ------------------------------------------------------------------- match

    @Test
    fun `method must be uppercase`() {
        assertSyntaxError("заглавными") { MatchSyntax.parse("get editor-api/v2/x") }
    }

    @Test
    fun `path variables are parsed`() {
        val match = MatchSyntax.parse("GET editor-api/v2/publisher/{id}/stats2?fields=views")
        assertEquals(
            listOf(
                MatchSegment.Literal("editor-api"),
                MatchSegment.Literal("v2"),
                MatchSegment.Literal("publisher"),
                MatchSegment.Variable("id"),
                MatchSegment.Literal("stats2"),
            ),
            match.segments,
        )
    }

    @Test
    fun `query parameter without value is rejected`() {
        assertSyntaxError("имя=значение") { MatchSyntax.parse("GET x?flag") }
    }

    @Test
    fun `empty query pair is rejected`() {
        assertSyntaxError("пустая пара") { MatchSyntax.parse("GET x?a=1&&b=2") }
    }

    @Test
    fun `variable parameter is recognized`() {
        val match = MatchSyntax.parse("GET x?from={date}&fields=views")
        assertEquals(
            listOf(MatchParamValue.Any("date")),
            match.param("from")?.values,
        )
    }

    @Test
    fun `duplicate literal values are collapsed`() {
        val match = MatchSyntax.parse("GET x?fields=views&fields=views")
        assertEquals(listOf("views"), match.param("fields")?.values?.map { (it as MatchParamValue.Literal).value })
    }

    @Test
    fun `canonical round trips`() {
        val canonical = MatchSyntax.parse("GET x?b=2&fields=vtr&a=1&fields=ctr").canonical
        assertEquals(canonical, MatchSyntax.parse(canonical).canonical)
    }
}
