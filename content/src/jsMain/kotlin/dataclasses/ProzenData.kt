package dataclasses

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ProzenData(
    val type: String,
    val text: String,
    val jsonData: JsonObject?
)