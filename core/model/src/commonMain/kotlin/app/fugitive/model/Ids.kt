package app.fugitive.model

/** Stable identifier of a player (the backend's auth uid). */
typealias PlayerId = String

/** Six-character lobby join code, e.g. `K7MX2Q`. */
typealias LobbyCode = String

object LobbyCodes {
    /** No 0/O, 1/I/L — easy to read aloud and type. */
    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    const val LENGTH = 6

    /** Upper-cases user input and drops anything that cannot be part of a code. */
    fun normalize(raw: String): String =
        raw.uppercase().filter { it in ALPHABET }.take(LENGTH)

    /** Deep link that opens the app straight into joining [code]. */
    fun link(code: String): String = "fugitive://join?joinCode=$code"

    /** Extracts a code from a scanned/pasted link or plain text, or null. */
    fun fromLink(raw: String): String? {
        val candidate = when {
            "joinCode=" in raw -> raw.substringAfter("joinCode=").substringBefore('&')
            "/" in raw -> raw.trimEnd('/').substringAfterLast('/')
            else -> raw
        }
        return normalize(candidate).takeIf { isValid(it) }
    }

    fun isValid(code: String): Boolean = code.length == LENGTH && code.all { it in ALPHABET }

    fun random(random: kotlin.random.Random = kotlin.random.Random.Default): String =
        (1..LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
}
