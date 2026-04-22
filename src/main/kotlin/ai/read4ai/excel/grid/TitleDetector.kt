package ai.read4ai.excel.grid

internal object TitleDetector {

    private val bulletPrefixes = listOf("\u25A0", "\u25FE", "\u25AA", "\u2022", "\u00B7", "-")

    data class TitleDetection(
        val titleText: String,
        val remainingRows: List<List<String>>,
    )

    fun detectTitle(rows: List<List<String>>): TitleDetection? {
        if (rows.isEmpty()) return null

        val rowLimit = minOf(rows.size, 2)
        for (r in 0 until rowLimit) {
            val row = rows[r]
            row.forEachIndexed { c, value ->
                val text = value.trim()
                if (text.isEmpty()) return@forEachIndexed

                if (isLikelyTitleText(text)) {
                    val sanitized = sanitizeRowsForTitle(rows, r, c)
                    return TitleDetection(normalizeTitleText(text), sanitized)
                }
            }
        }

        return null
    }

    fun isLikelyTitleText(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false

        if (bulletPrefixes.any { trimmed.startsWith(it) }) {
            return true
        }

        val condensed = trimmed.replace("\\s+".toRegex(), "")
        val yearPattern = Regex("^(20|19)\\d{2}(\uB144|\uB144[\uAC00-\uD7A3]*|\\s|$)")
        if (yearPattern.containsMatchIn(condensed)) return true

        if (condensed.startsWith("\uB144") || condensed.startsWith("~")) return true
        if (condensed.contains("\uD328\uB110") && condensed.length <= 15) return true

        return false
    }

    fun normalizeTitleText(text: String): String = text.trim()

    fun formatTitle(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        return if (bulletPrefixes.any { trimmed.startsWith(it) }) trimmed else "\u25FE $trimmed"
    }

    private fun sanitizeRowsForTitle(
        rows: List<List<String>>,
        titleRowIndex: Int,
        titleColIndex: Int,
    ): List<List<String>> {
        return rows.mapIndexed { r, row ->
            row.mapIndexed { c, value ->
                if (r == titleRowIndex && c == titleColIndex) "" else value
            }
        }.filterNot { sanitizedRow -> sanitizedRow.all { it.isBlank() } }
    }
}
