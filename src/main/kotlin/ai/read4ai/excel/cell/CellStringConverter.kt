package ai.read4ai.excel.cell

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.poi.ss.usermodel.*
import java.text.SimpleDateFormat
import java.util.*

internal object CellStringConverter {

    private val log = KotlinLogging.logger {}
    private val generalTokens = listOf("General", "G/\uD45C\uC900", "\uD45C\uC900", "G/\u6A19\u6E96", "\u6A19\u6E96", "G/\u6807\u51C6", "\u6807\u51C6")

    fun convertCellToString(
        cell: Cell?,
        evaluator: FormulaEvaluator,
        dataFormatter: DataFormatter,
    ): String {
        if (cell == null) return ""

        // FORMULA type: skip DataFormatter and go directly to cache logic
        // DataFormatter internally uses evaluator for recursive evaluation which
        // can get stuck on unsupported functions (SORT, etc.)
        if (cell.cellType == CellType.FORMULA) {
            // Step 1: Try using cache
            try {
                when (cell.cachedFormulaResultType) {
                    CellType.STRING -> return TextSanitizer.sanitizeOutput(cell.stringCellValue)
                    CellType.NUMERIC -> return TextSanitizer.sanitizeOutput(getNumericCellAsString(cell))
                    CellType.BOOLEAN -> return TextSanitizer.sanitizeOutput(cell.booleanCellValue.toString())
                    CellType.ERROR -> return TextSanitizer.sanitizeOutput(
                        FormulaError.forInt(cell.errorCellValue).string ?: "#ERROR?"
                    )
                    CellType.BLANK -> return ""
                    else -> {
                        log.warn { "Cell ${cell.address} unexpected cache type: ${cell.cachedFormulaResultType}, attempting recalculation" }
                    }
                }
            } catch (e: Exception) {
                log.warn { "Cell ${cell.address} cache read failed, attempting formula recalculation: ${e.message}" }
            }

            // Step 2: Recalculate with FormulaEvaluator
            try {
                val cellValue = evaluator.evaluate(cell)
                return when (cellValue.cellType) {
                    CellType.STRING -> TextSanitizer.sanitizeOutput(cellValue.stringValue)
                    CellType.NUMERIC -> TextSanitizer.sanitizeOutput(
                        if (DateUtil.isCellDateFormatted(cell)) {
                            try {
                                DateUtil.getJavaDate(cellValue.numberValue).toString()
                            } catch (_: Exception) {
                                cellValue.numberValue.toString()
                            }
                        } else {
                            val numericValue = cellValue.numberValue
                            if (numericValue == numericValue.toLong().toDouble()) {
                                numericValue.toLong().toString()
                            } else {
                                numericValue.toString()
                            }
                        }
                    )
                    CellType.BOOLEAN -> TextSanitizer.sanitizeOutput(cellValue.booleanValue.toString())
                    CellType.ERROR -> TextSanitizer.sanitizeOutput(
                        FormulaError.forInt(cellValue.errorValue).string ?: "#ERROR?"
                    )
                    CellType.BLANK -> ""
                    else -> TextSanitizer.sanitizeOutput("Formula: ${cell.cellFormula}")
                }
            } catch (e: Exception) {
                log.warn { "Cell ${cell.address} formula recalculation failed: ${e.message}" }
            }

            // Step 3: Final fallback - formula string
            return TextSanitizer.sanitizeOutput("Formula: ${cell.cellFormula}")
        }

        // Non-FORMULA types use existing logic
        try {
            val formattedValue = dataFormatter.formatCellValue(cell, evaluator)
            val sanitizedFormatted = TextSanitizer.sanitizeOutput(formattedValue)

            val isNumericLike = (cell.cellType == CellType.NUMERIC ||
                    (cell.cellType == CellType.FORMULA && cell.cachedFormulaResultType == CellType.NUMERIC))

            val fmtString: String? = if (isNumericLike) DateFormatter.resolveDataFormatString(cell) else null
            val isDBNum = fmtString?.contains("[DBNum", ignoreCase = true) == true
            val isDateByPOI = if (isNumericLike) DateUtil.isCellDateFormatted(cell) else false
            val isDateByFormat = if (isNumericLike) {
                val style = cell.cellStyle
                if (style != null && fmtString != null)
                    try {
                        DateUtil.isADateFormat(style.dataFormat.toInt(), fmtString)
                    } catch (_: Exception) {
                        false
                    }
                else false
            } else false

            val shouldProcessAsDate = isNumericLike && !isDBNum && (
                    isDateByPOI || isDateByFormat ||
                            DateFormatter.isUnprocessedDatePattern(formattedValue) ||
                            (fmtString?.let { DateFormatter.isDateFormatPattern(it) } ?: false)
                    )

            if (shouldProcessAsDate) {
                log.debug {
                    "Needs date processing - POI recognized: $isDateByPOI, pattern detected: ${
                        DateFormatter.isUnprocessedDatePattern(formattedValue)
                    }"
                }

                val processed = DateFormatter.processDateValue(
                    numericValue = cell.numericCellValue,
                    formattedValue = fmtString ?: formattedValue,
                    cellStyle = cell.cellStyle,
                    force = true
                )
                val safeProcessed = TextSanitizer.sanitizeOutput(processed)
                if (safeProcessed.isNotBlank()) return safeProcessed

                val manual = try {
                    val pattern = DateFormatter.determineJavaDatePattern(fmtString ?: "", cell.cellStyle)
                    val date = DateUtil.getJavaDate(cell.numericCellValue)
                    SimpleDateFormat(pattern, Locale.KOREAN).format(date)
                } catch (_: Exception) {
                    try {
                        val date = DateUtil.getJavaDate(cell.numericCellValue)
                        SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN).format(date)
                    } catch (_: Exception) {
                        ""
                    }
                }
                val safeManual = TextSanitizer.sanitizeOutput(manual)
                if (safeManual.isNotBlank()) return safeManual

                val raw = maybeFormatRaw(cell, dataFormatter)
                if (!raw.isNullOrBlank()) return TextSanitizer.sanitizeOutput(raw)
            }

            if (formattedValue.isNotBlank()) {
                if (isNumericLike) {
                    val rawFormatted = maybeFormatRaw(cell, dataFormatter)
                    val dbnumFixed = maybeApplyDBNumFallback(cell, rawFormatted ?: formattedValue)
                    if (dbnumFixed != null) return TextSanitizer.sanitizeOutput(dbnumFixed)

                    if (rawFormatted != null && isBetterThan(formattedValue, rawFormatted)) {
                        return TextSanitizer.sanitizeOutput(rawFormatted)
                    }

                    val generalFixed = maybeApplyGeneralTokenFallback(cell, rawFormatted ?: formattedValue)
                    if (generalFixed != null) return TextSanitizer.sanitizeOutput(generalFixed)
                }

                return sanitizedFormatted
            }
        } catch (e: Exception) {
            log.debug(
                e,
            ) { "Couldn't get formatted value for cell at row ${cell.rowIndex}, column ${cell.columnIndex}" }
        }

        return when (cell.cellType) {
            CellType.STRING -> TextSanitizer.sanitizeOutput(cell.stringCellValue)
            CellType.NUMERIC -> TextSanitizer.sanitizeOutput(getNumericCellAsString(cell))
            CellType.BOOLEAN -> TextSanitizer.sanitizeOutput(cell.booleanCellValue.toString())
            CellType.FORMULA -> {
                log.warn { "Cell ${cell.address} FORMULA type reached here - should not happen!" }
                ""
            }
            CellType.BLANK -> ""
            CellType.ERROR -> TextSanitizer.sanitizeOutput(
                FormulaError.forInt(cell.errorCellValue).string ?: "#ERROR?"
            )
            else -> ""
        }
    }

    private fun getNumericCellAsString(cell: Cell): String {
        return if (DateUtil.isCellDateFormatted(cell)) {
            try {
                cell.dateCellValue.toString()
            } catch (_: Exception) {
                cell.numericCellValue.toString()
            }
        } else {
            val numericValue = cell.numericCellValue
            if (numericValue == numericValue.toLong().toDouble()) {
                numericValue.toLong().toString()
            } else {
                numericValue.toString()
            }
        }
    }

    private fun maybeApplyGeneralTokenFallback(cell: Cell, formattedValue: String): String? {
        val numericValue: Double = when (cell.cellType) {
            CellType.NUMERIC -> cell.numericCellValue
            CellType.FORMULA -> if (cell.cachedFormulaResultType == CellType.NUMERIC) cell.numericCellValue else return null
            else -> return null
        }

        val fmt = DateFormatter.resolveDataFormatString(cell)
        val hasGeneralToken = generalTokens.any { token ->
            formattedValue.contains(token, ignoreCase = true) ||
                    (fmt?.contains(token, ignoreCase = true) == true)
        }
        if (!hasGeneralToken) return null

        val generalStr = if (numericValue == numericValue.toLong().toDouble()) {
            numericValue.toLong().toString()
        } else {
            numericValue.toString()
        }

        var result = formattedValue.ifBlank { fmt ?: formattedValue }
        generalTokens.forEach { token ->
            result = result.replace(token, generalStr, ignoreCase = true)
        }
        return result
    }

    private fun maybeApplyDBNumFallback(cell: Cell, formattedValue: String): String? {
        val fmt = DateFormatter.resolveDataFormatString(cell) ?: return null
        val containsDbNum =
            fmt.contains("[DBNum", ignoreCase = true) || formattedValue.contains("[DBNum", ignoreCase = true)
        if (!containsDbNum) return null

        val value = when (cell.cellType) {
            CellType.NUMERIC -> cell.numericCellValue
            CellType.FORMULA -> if (cell.cachedFormulaResultType == CellType.NUMERIC) cell.numericCellValue else return null
            else -> return null
        }

        val isNegative = value < 0
        val absVal = kotlin.math.abs(value)
        val intPart = absVal.toLong()
        val hasFrac = absVal != intPart.toDouble()

        val integerStr = CjkNumberConverter.toCJKNumber(intPart)
        val result = if (!hasFrac) {
            integerStr
        } else {
            val fracDigits = absVal.toString().substringAfter('.', "")
            val fracCjk = buildString(fracDigits.length + 1) {
                append('\u70B9')
                fracDigits.forEach { ch -> append(CjkNumberConverter.digitToCJK(ch)) }
            }
            integerStr + fracCjk
        }

        return if (isNegative) "\u8CA0$result" else result
    }

    private fun maybeFormatRaw(cell: Cell, dataFormatter: DataFormatter): String? {
        val style = cell.cellStyle ?: return null
        val value = when (cell.cellType) {
            CellType.NUMERIC -> cell.numericCellValue
            CellType.FORMULA -> if (cell.cachedFormulaResultType == CellType.NUMERIC) cell.numericCellValue else return null
            else -> return null
        }
        val fmt = DateFormatter.resolveDataFormatString(cell) ?: return null
        return try {
            dataFormatter.formatRawCellContents(value, style.dataFormat.toInt(), fmt)
        } catch (_: Exception) {
            null
        }
    }

    private fun looksUnformatted(value: String): Boolean {
        if (value.isBlank()) return true
        val hasDateTokens = DateFormatter.isUnprocessedDatePattern(value)
        val hasGeneral = generalTokens.any { token -> value.contains(token, ignoreCase = true) }
        return hasDateTokens || hasGeneral
    }

    private fun isBetterThan(oldValue: String, newValue: String): Boolean {
        if (newValue.isBlank()) return false
        val oldSuspicious = looksUnformatted(oldValue)
        val newSuspicious = looksUnformatted(newValue)
        return oldSuspicious && !newSuspicious
    }
}
