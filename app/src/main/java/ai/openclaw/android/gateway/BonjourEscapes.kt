package ai.openclaw.android.gateway

/**
 * Decodes DNS-SD / Bonjour escaped service names.
 * DNS-SD names may contain escaped characters like \032 for space.
 */
object BonjourEscapes {
    fun decode(name: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < name.length) {
            if (name[i] == '\\' && i + 3 < name.length) {
                val code = name.substring(i + 1, i + 4).toIntOrNull()
                if (code != null && code in 0..255) {
                    sb.append(code.toChar())
                    i += 4
                    continue
                }
            }
            sb.append(name[i])
            i++
        }
        return sb.toString()
    }
}
