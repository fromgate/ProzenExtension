package common.pageanalyzer

data class Thematic(
    val link: String,
    val title: String,
    val tabId: String,
    val type: String,
    val subscribersCount: Int?,
)

fun List<Thematic>.sortByTitle(targetTitle: String?): List<Thematic> {
    if (this.isEmpty()) return this
    val (matchingTitle, others) = this.partition { it.title == (targetTitle ?: this.first().title) }

    val firstIsSingleWord = !matchingTitle.first().title.trim().contains(" ")

    val singleWordTitles = others.filter { !it.title.trim().contains(" ") }
    val multiWordTitles = others.filter { it.title.trim().contains(" ") }

    val first = if (firstIsSingleWord) multiWordTitles else singleWordTitles
    val second = if (firstIsSingleWord) singleWordTitles else multiWordTitles

    val alternatedList = mutableListOf<Thematic>()
    val maxLength = maxOf(first.size, second.size)

    for (i in 0 until maxLength) {
        if (i < first.size) alternatedList.add(first[i])
        if (i < second.size) alternatedList.add(second[i])
    }
    return matchingTitle + alternatedList
}

