import common.Card
import common.isInPeriod
import kotlinx.datetime.Instant
import requester.Requester
import kotlin.math.roundToInt

class Counter(private val requester: Requester) {

    var cards: List<Card>? = null

    fun isDataLoaded(): Boolean {
        return cards != null
    }

    suspend fun loadData() {
        if (cards == null) {
            cards = requester.loadAllPublications(true)
        }
    }

    fun calculateStats(range: Pair<Instant, Instant>?): Map<String, Stat> {
        val calcCards = if (range == null) { cards } else {
            cards?.filter { it.isInPeriod(range) }
        }
        if (calcCards.isNullOrEmpty()) return emptyMap()
        fun calculateAggregates(values: List<Int>): Triple<Int, Int, Int> {
            val sum = values.sum()
            val median = values.median()
            val avg = if (values.isNotEmpty()) values.average().roundToInt() else 0
            return Triple(sum, median, avg)
        }

        val statsByType = calcCards.groupBy { it.type }.mapValues { (_, cardsOfType) ->
            val impressionsAgg = calculateAggregates(cardsOfType.mapNotNull { it.feedShows })
            val clicksAgg = calculateAggregates(cardsOfType.mapNotNull { it.clicks })
            val typeSpecificViewsAgg = calculateAggregates(cardsOfType.mapNotNull { it.subscribersViews })
            val deepViewsAgg = calculateAggregates(cardsOfType.mapNotNull { it.viewsTillEnd })
            val addTimes = cardsOfType.mapNotNull { it.addTime }

            Stat(
                type = cardsOfType.first().type,
                count = cardsOfType.size,
                impressions = impressionsAgg.first,
                impressionsMed = impressionsAgg.second,
                impressionsAvg = impressionsAgg.third,
                clicks = clicksAgg.first,
                clicksMed = clicksAgg.second,
                clicksAvg = clicksAgg.third,
                typeSpecificViews = typeSpecificViewsAgg.first,
                typeSpecificViewsMed = typeSpecificViewsAgg.second,
                typeSpecificViewsAvg = typeSpecificViewsAgg.third,
                deepViews = deepViewsAgg.first,
                deepViewsMed = deepViewsAgg.second,
                deepViewsAvg = deepViewsAgg.third,
                minAddTime = addTimes.minOrNull() ?: 0L
            )
        }


        val allImpressionsAgg = calculateAggregates(calcCards.mapNotNull { it.feedShows })
        val allClicksAgg = calculateAggregates(calcCards.mapNotNull { it.clicks })
        val allTypeSpecificViewsAgg = calculateAggregates(calcCards.mapNotNull { it.subscribersViews })
        val allDeepViewsAgg = calculateAggregates(calcCards.mapNotNull { it.viewsTillEnd })
        val allAddTimes = calcCards.mapNotNull { it.addTime }

        val totalStat = Stat(
            type = "total",
            count = calcCards.size,
            impressions = allImpressionsAgg.first,
            impressionsMed = allImpressionsAgg.second,
            impressionsAvg = allImpressionsAgg.third,
            clicks = allClicksAgg.first,
            clicksMed = allClicksAgg.second,
            clicksAvg = allClicksAgg.third,
            typeSpecificViews = allTypeSpecificViewsAgg.first,
            typeSpecificViewsMed = allTypeSpecificViewsAgg.second,
            typeSpecificViewsAvg = allTypeSpecificViewsAgg.third,
            deepViews = allDeepViewsAgg.first,
            deepViewsMed = allDeepViewsAgg.second,
            deepViewsAvg = allDeepViewsAgg.third,
            minAddTime = allAddTimes.minOrNull() ?: 0L
        )

        return statsByType + ("total" to totalStat)
    }

    // Расширение для медианы
    fun List<Int>.median(): Int {
        if (isEmpty()) return 0
        val sorted = sorted()
        val middle = size / 2
        return if (size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2
        } else {
            sorted[middle]
        }
    }


}