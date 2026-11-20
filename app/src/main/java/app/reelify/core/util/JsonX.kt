package app.reelify.core.util

import kotlinx.serialization.json.Json

/** Shared, lenient-on-read JSON configuration used for persistence and network DTOs. */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = false
}

inline fun <reified T> Json.decodeOrNull(text: String?): T? =
    if (text.isNullOrBlank()) null else runCatching { decodeFromString<T>(text) }.getOrNull()
