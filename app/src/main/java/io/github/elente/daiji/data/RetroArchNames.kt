package io.github.elente.daiji.data

object RetroArchNames {
    /** RetroArch replaces these characters with `_` when it saves a thumbnail. */
    private val reservedChars = Regex("""[&*/:`"<>?\\|]""")

    fun thumbnail(label: String): String = label.replace(reservedChars, "_")

    /** "Chrono Trigger (USA) [!]" becomes "Chrono Trigger"; null when there is nothing to strip. */
    fun shortName(label: String): String? {
        val short = label.substringBefore(" (").substringBefore(" [").trim()
        return short.takeIf { it.isNotEmpty() && it != label }
    }
}
