package ai.read4ai.excel.lang

import ai.read4ai.excel.model.Element
import ai.read4ai.excel.model.ExcelDocument

/**
 * Detects document language based on Unicode script ratio.
 *
 * Counts non-empty text cells containing each script.
 * If >= 1% of cells contain Hangul → KO.
 * If >= 1% contain Hiragana/Katakana → JA.
 * Otherwise → EN.
 */
object LanguageDetector {

    private const val THRESHOLD = 0.01 // 1%

    fun detect(document: ExcelDocument): String {
        var totalCells = 0
        var hangulCells = 0
        var kanaCells = 0

        for (sheet in document.sheets) {
            for (element in sheet.elements) {
                val texts = when (element) {
                    is Element.Table -> element.rows.flatMap { r -> r.cells.map { it.value } }
                    is Element.Heading -> listOf(element.text)
                    is Element.Text -> listOf(element.text)
                    is Element.Note -> listOf(element.text)
                    is Element.Image -> listOfNotNull(element.description)
                }
                for (text in texts) {
                    if (text.isBlank()) continue
                    totalCells++
                    var hasHangul = false
                    var hasKana = false
                    text.codePoints().forEach { cp ->
                        when (Character.UnicodeScript.of(cp)) {
                            Character.UnicodeScript.HANGUL -> hasHangul = true
                            Character.UnicodeScript.HIRAGANA,
                            Character.UnicodeScript.KATAKANA -> hasKana = true
                            else -> {}
                        }
                    }
                    if (hasHangul) hangulCells++
                    if (hasKana) kanaCells++
                }
            }
        }

        if (totalCells == 0) return "EN"

        val hangulRatio = hangulCells.toDouble() / totalCells
        val kanaRatio = kanaCells.toDouble() / totalCells

        return when {
            hangulRatio >= THRESHOLD && hangulRatio >= kanaRatio -> "KO"
            kanaRatio >= THRESHOLD -> "JA"
            else -> "EN"
        }
    }
}
