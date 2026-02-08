package ai.openclaw.android

object SessionKey {
    const val DEFAULT = "main"

    fun normalize(key: String?): String {
        if (key.isNullOrBlank()) return DEFAULT
        val trimmed = key.trim().lowercase()
        return if (trimmed == "main" || trimmed == "default") DEFAULT else trimmed
    }

    fun isMainSession(key: String): Boolean {
        return normalize(key) == DEFAULT
    }
}
