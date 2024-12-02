package pageanalyzer

import kotlinx.serialization.json.JsonObject
import org.w3c.dom.HTMLMetaElement

data class PageContext(
    val url: String,
    val type: String,
    var title: String = "Публикация",
    val publisherId: String,
    val publicationId: String,
    var views: Int? = null,
    var viewsTillEnd: Int? = null,
    var comments: Int? = null,
    var likes: Int? = null,
    var timeToRead: Int? = null,
    var thematics: List<Thematic> = emptyList(),
    var embeddedJson: JsonObject? = null,
    val metaTags: MutableList<HTMLMetaElement> = mutableListOf(),
    val checkResults: MutableMap<TypeCheck, Any> = mutableMapOf(),

    var isOk: Boolean = true,
    var isParseError: Boolean = false,
)

fun PageContext.isVideo() = type == "short_video" || type == "gif"
fun PageContext.isText() = type == "article" || type == "brief"

fun PageContext.containsAnyChecks(typeChecks: List<TypeCheck>): Boolean {
    return checkResults.keys.any { it in typeChecks }
}

fun PageContext.isOk() = this.isOk && !this.isParseError