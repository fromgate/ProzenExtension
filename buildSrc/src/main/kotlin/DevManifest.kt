import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * Готовит dev-манифест Chrome из release-манифеста.
 *
 * Меняются только два места: имя (`Prozen dev`, чтобы dev-расширение нельзя было
 * спутать с обычным) и разрешение `unlimitedStorage` (в Chrome квота
 * `storage.local` без него — 10 МБ, а расхождения могут быть объёмными).
 * `options_page` и остальные поля остаются как в release.
 *
 * Исходный манифест в репозитории не меняется: правится только копия в каталоге
 * сборки.
 */
fun writeDevManifest(source: File, target: File) {
    val root = Json.parseToJsonElement(source.readText()).jsonObject

    val updated = buildJsonObject {
        root.forEach { (key, value) ->
            when (key) {
                "name" -> put("name", JsonPrimitive(DEV_MANIFEST_NAME))
                "permissions" -> put("permissions", devPermissions(value))
                else -> put(key, value)
            }
        }
    }

    target.writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), updated))
}

private const val DEV_MANIFEST_NAME = "Prozen dev"

private fun devPermissions(value: kotlinx.serialization.json.JsonElement): JsonArray {
    val permissions = value.jsonArray
        .map { it.jsonPrimitive.content }
        .toMutableList()

    if ("unlimitedStorage" !in permissions) {
        permissions.add(0, "unlimitedStorage")
    }

    return JsonArray(permissions.map { JsonPrimitive(it) })
}
