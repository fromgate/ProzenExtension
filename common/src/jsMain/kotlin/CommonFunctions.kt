import kotlinx.browser.document


fun getZenObject(): Pair<String,String>? {
    val content = document.head
        ?.querySelector("meta[property=zen_object_id][content]")
        ?.getAttribute("content")
    return content?.split(":", limit = 2)?.let { if (it.size==2) it[0] to it[1] else null }
}