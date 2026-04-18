package ai.read4ai.excel.cell

internal object TextSanitizer {

    private val hiddenCharsRegex =
        Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F\u200B\u200C\u200D\u2060\uFEFF\u00AD]")
    private val reservedPlaceholderRegex = Regex("(?i)reserved-\\d+x[0-9a-f]+")

    fun sanitizeOutput(text: String): String {
        var result = text
        result = hiddenCharsRegex.replace(result, "")
        result = reservedPlaceholderRegex.replace(result, "")
        return result.trim()
    }
}
