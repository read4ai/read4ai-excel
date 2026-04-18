package ai.read4ai.excel

import ai.read4ai.excel.model.*
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path

/**
 * Parses CSV files into [ExcelDocument], enabling unified downstream processing.
 *
 * Produces one sheet (named from the file, or "Sheet1"), one [Element.Table], no merge regions.
 */
object CsvParser {

    private val log = KotlinLogging.logger {}

    @JvmStatic
    @JvmOverloads
    fun parse(
        data: ByteArray,
        fileName: String? = null,
        config: CsvConfig = CsvConfig(),
    ): ExcelDocument {
        log.debug { "Starting parse, fileName=$fileName, size=${data.size}" }

        val delimiter = config.delimiter ?: detectDelimiter(data, config.encoding)

        val reader = BufferedReader(InputStreamReader(data.inputStream(), config.encoding))
        val rows = parseCsvRows(reader, delimiter)

        val sheetName = fileName
            ?.substringBeforeLast(".")
            ?: "Sheet1"

        val headerRowCount = if (config.hasHeader && rows.isNotEmpty()) 1 else 0

        val modelRows = rows.mapIndexed { index, cells ->
            Row(
                rowIndex = index,
                cells = cells.map { Cell(value = it) },
            )
        }

        val table = Element.Table(
            rows = modelRows,
            headerRowCount = headerRowCount,
        )

        val sheet = Sheet(
            sheetIndex = 0,
            sheetName = sheetName,
            elements = listOf(table),
        )

        return ExcelDocument(
            fileName = fileName,
            numberOfSheets = 1,
            sheets = listOf(sheet),
        )
    }

    @JvmStatic
    @JvmOverloads
    fun parse(
        path: Path,
        config: CsvConfig = CsvConfig(),
    ): ExcelDocument {
        val bytes = Files.readAllBytes(path)
        val fileName = path.fileName?.toString()
        return parse(bytes, fileName, config)
    }

    /** RFC 4180 compliant CSV row parser (handles quoted fields, escaped quotes, trailing newlines). */
    internal fun parseCsvRows(reader: BufferedReader, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentField = StringBuilder()
        val currentRow = mutableListOf<String>()
        var inQuotes = false
        var prevChar: Char? = null

        reader.use { br ->
            var ch: Int
            while (br.read().also { ch = it } != -1) {
                val c = ch.toChar()

                when {
                    // Handle quoted fields
                    c == '"' && inQuotes -> {
                        // Peek at next char to check for escaped quote
                        br.mark(1)
                        val next = br.read()
                        if (next == '"'.code) {
                            // Escaped quote
                            currentField.append('"')
                        } else {
                            // End of quoted field
                            inQuotes = false
                            if (next != -1) {
                                br.reset()
                            }
                        }
                    }

                    c == '"' && !inQuotes && currentField.isEmpty() -> {
                        // Start of quoted field
                        inQuotes = true
                    }

                    c == delimiter && !inQuotes -> {
                        currentRow.add(currentField.toString())
                        currentField.clear()
                    }

                    c == '\n' && !inQuotes -> {
                        // Handle \r\n: skip trailing \r that would have been added
                        val fieldValue = if (prevChar == '\r' && currentField.isNotEmpty() &&
                            currentField.last() == '\r'
                        ) {
                            currentField.substring(0, currentField.length - 1)
                        } else {
                            currentField.toString()
                        }
                        currentRow.add(fieldValue)
                        if (currentRow.any { it.isNotEmpty() }) {
                            rows.add(currentRow.toList())
                        }
                        currentRow.clear()
                        currentField.clear()
                    }

                    c == '\r' && !inQuotes -> {
                        // Skip \r, handle in \n case
                    }

                    else -> {
                        currentField.append(c)
                    }
                }
                prevChar = c
            }
        }

        // Handle last row (no trailing newline)
        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString())
            if (currentRow.any { it.isNotEmpty() }) {
                rows.add(currentRow.toList())
            }
        }

        return rows
    }

    private val CANDIDATE_DELIMITERS = charArrayOf(',', '\t', ';', '|')
    private const val SNIFF_LINES = 10

    /**
     * Auto-detect delimiter by checking which candidate produces the most consistent column count
     * across the first few lines. Falls back to comma if undetermined.
     */
    internal fun detectDelimiter(data: ByteArray, encoding: java.nio.charset.Charset): Char {
        val preview = String(data, encoding).lineSequence()
            .filter { it.isNotBlank() }
            .take(SNIFF_LINES)
            .toList()

        if (preview.isEmpty()) return ','

        var bestDelimiter = ','
        var bestScore = -1

        for (delim in CANDIDATE_DELIMITERS) {
            val counts = preview.map { line -> line.count { it == delim } }
            if (counts.all { it == 0 }) continue

            val firstCount = counts[0]
            // Score = column count if consistent across all lines, 0 otherwise
            val consistent = counts.all { it == firstCount }
            if (consistent && firstCount > bestScore) {
                bestScore = firstCount
                bestDelimiter = delim
            }
        }

        return bestDelimiter
    }
}
