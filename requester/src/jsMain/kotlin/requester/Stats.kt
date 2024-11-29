package requester

import kotlinx.serialization.Serializable

@Serializable
data class Stats(val publicationId: String, val views: Int, val viewsTillEnd: Int)
