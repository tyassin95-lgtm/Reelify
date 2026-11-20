package app.reelify.integration.openai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Tiny builder for OpenAI strict JSON schemas (all properties required, no additional properties). */
object S {
    fun str(description: String? = null): JsonObject = buildJsonObject {
        put("type", "string"); description?.let { put("description", it) }
    }
    fun int(description: String? = null): JsonObject = buildJsonObject {
        put("type", "integer"); description?.let { put("description", it) }
    }
    fun bool(description: String? = null): JsonObject = buildJsonObject {
        put("type", "boolean"); description?.let { put("description", it) }
    }
    fun enum(values: List<String>, description: String? = null): JsonObject = buildJsonObject {
        put("type", "string"); put("enum", JsonArray(values.map { JsonPrimitive(it) })); description?.let { put("description", it) }
    }
    fun arr(items: JsonElement, description: String? = null): JsonObject = buildJsonObject {
        put("type", "array"); put("items", items); description?.let { put("description", it) }
    }
    fun obj(vararg props: Pair<String, JsonElement>): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", JsonObject(props.toMap()))
        put("required", JsonArray(props.map { JsonPrimitive(it.first) }))
        put("additionalProperties", false)
    }
}
