package app.fugitive.backend.firebase

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Bridges kotlinx.serialization models and the plain Map/List/primitive trees
 * Firebase stores. Because it is generic, adding a field to any model needs no
 * change here — the field simply appears in the database.
 */
object FireJson {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
        isLenient = true
    }

    fun <T> encode(serializer: KSerializer<T>, value: T): Any? = toPlain(json.encodeToJsonElement(serializer, value))

    fun <T> decode(serializer: KSerializer<T>, raw: Any?): T? =
        if (raw == null) null else runCatching { json.decodeFromJsonElement(serializer, fromPlain(raw)) }.getOrNull()

    fun toPlain(e: JsonElement): Any? = when (e) {
        is JsonNull -> null
        is JsonPrimitive -> when {
            e.isString -> e.content
            e.booleanOrNull != null -> e.booleanOrNull
            e.longOrNull != null -> e.longOrNull
            else -> e.doubleOrNull
        }
        is JsonObject -> e.mapValues { toPlain(it.value) }.filterValues { it != null }.takeIf { it.isNotEmpty() }
        is JsonArray -> e.map { toPlain(it) }.takeIf { it.isNotEmpty() }
    }

    fun fromPlain(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is Map<*, *> -> JsonObject(v.entries.associate { (k, value) -> k.toString() to fromPlain(value) })
        is List<*> -> JsonArray(v.filterNotNull().map { fromPlain(it) })
        is Boolean -> JsonPrimitive(v)
        is Number -> JsonPrimitive(v)
        is String -> JsonPrimitive(v)
        else -> JsonPrimitive(v.toString())
    }
}
