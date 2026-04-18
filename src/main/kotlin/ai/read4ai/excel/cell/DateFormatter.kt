package ai.read4ai.excel.cell

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.DateUtil
import java.text.SimpleDateFormat
import java.util.*

internal object DateFormatter {

    private val log = KotlinLogging.logger {}

    private val datePatternRegex = Regex(
        pattern = "(yyyy|yy|mm|m|dd|d|hh|h|ss|s)",
        option = RegexOption.IGNORE_CASE
    )

    private val koreanDateKeywords = listOf("\uB144", "\uC6D4", "\uC77C", "\uC2DC", "\uBD84", "\uCD08")

    fun isUnprocessedDatePattern(value: String): Boolean {
        val lowerValue = value.lowercase()

        val containsDateKeywords = listOf("yyyy", "yy", "mm", "dd", "hh", "ss")
            .any { lowerValue.contains(it) }

        val containsKoreanDatePattern = koreanDateKeywords
            .any { value.contains(it) } && containsDateKeywords

        return containsDateKeywords || containsKoreanDatePattern
    }

    fun determineJavaDatePattern(
        formattedValue: String,
        cellStyle: CellStyle?
    ): String {
        val actualFormat = cellStyle?.dataFormatString ?: formattedValue

        val cleaned = actualFormat
            .replace(Regex("\\[\\$[^\\]]*\\]"), "")
            .replace(Regex("\\[(?i:color)\\s*\\d+\\]"), "")
            .replace(Regex("\\[(?i:black|blue|cyan|green|magenta|red|white|yellow)\\]"), "")
            .replace(Regex("\\[(?i:DBNum)\\d*\\]"), "")
            .trim()

        return cleaned
            .replace("mm", "MM")
            .replace("m", "M")
            .replace("yyyy", "yyyy")
            .replace("yy", "yy")
            .replace("dd", "dd")
            .replace("d", "d")
            .replace("hh", "HH")
            .replace("h", "H")
            .replace("AM/PM", "a", ignoreCase = true)
            .replace("\"", "")
            .trim()
    }

    fun processDateValue(
        numericValue: Double,
        formattedValue: String,
        cellStyle: CellStyle?,
        force: Boolean = false
    ): String {
        if (!force && !isUnprocessedDatePattern(formattedValue)) {
            return formattedValue
        }

        return try {
            val date = DateUtil.getJavaDate(numericValue)
            val javaPattern = determineJavaDatePattern(formattedValue, cellStyle)
            val dateFormat = SimpleDateFormat(javaPattern, Locale.KOREAN)
            val result = dateFormat.format(date)

            log.debug { "Date conversion succeeded - original: $numericValue, pattern: $javaPattern, result: $result" }

            result

        } catch (e: Exception) {
            log.warn { "Date conversion failed - value: $numericValue, pattern: $formattedValue, error: ${e.message}" }
            try {
                val date = DateUtil.getJavaDate(numericValue)
                SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN).format(date)
            } catch (_: Exception) {
                formattedValue
            }
        }
    }

    fun resolveDataFormatString(cell: Cell): String? {
        val style = cell.cellStyle ?: return null
        val direct = style.dataFormatString
        if (!direct.isNullOrBlank()) return direct
        return try {
            val wb = cell.sheet.workbook
            val df = wb.creationHelper.createDataFormat()
            df.getFormat(style.dataFormat)
        } catch (_: Exception) {
            null
        }
    }

    fun isDateFormatPattern(formatString: String): Boolean {
        if (datePatternRegex.containsMatchIn(formatString)) {
            return true
        }

        if (koreanDateKeywords.any { formatString.contains(it) }) {
            return datePatternRegex.containsMatchIn(formatString)
        }

        return false
    }
}
