package app.fugitive.model

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** Small helpers for the open `Map<String, JsonPrimitive>` extension bags. */
fun Map<String, JsonPrimitive>.long(key: String): Long? = this[key]?.longOrNull ?: this[key]?.doubleOrNull?.toLong()
fun Map<String, JsonPrimitive>.double(key: String): Double? = this[key]?.doubleOrNull
fun Map<String, JsonPrimitive>.bool(key: String): Boolean? = this[key]?.booleanOrNull
fun Map<String, JsonPrimitive>.string(key: String): String? = this[key]?.takeIf { it.isString }?.contentOrNull ?: this[key]?.contentOrNull

fun jsonOf(value: Any?): JsonPrimitive = when (value) {
    null -> kotlinx.serialization.json.JsonNull
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is String -> JsonPrimitive(value)
    is Enum<*> -> JsonPrimitive(value.name)
    else -> JsonPrimitive(value.toString())
}
