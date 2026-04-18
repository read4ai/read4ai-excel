package ai.read4ai.excel

import java.nio.charset.Charset

/**
 * Configuration for CSV parsing.
 *
 * @property delimiter field separator character. null = auto-detect from content
 * @property encoding character encoding of the CSV data (default: UTF-8)
 * @property hasHeader whether the first row is a header row (default: true)
 */
data class CsvConfig(
    val delimiter: Char? = null,
    val encoding: Charset = Charsets.UTF_8,
    val hasHeader: Boolean = true,
)
