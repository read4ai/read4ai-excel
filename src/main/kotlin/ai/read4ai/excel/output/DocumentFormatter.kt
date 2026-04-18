package ai.read4ai.excel.output

import ai.read4ai.excel.model.ExcelDocument

/**
 * Converts an [ExcelDocument] to a text representation.
 *
 * Implementations are the output counterpart to the pipeline interfaces:
 * while `Segmenter`, `HeaderDetector`, etc. control how Excel is *parsed*,
 * `DocumentFormatter` controls how the parsed result is *rendered*.
 *
 * Built-in implementations:
 * - [JsonFormatter] — compact or row-object JSON
 * - [MarkdownFormatter] — pipe-table Markdown
 *
 * Example custom formatter:
 * ```kotlin
 * class CsvFormatter : DocumentFormatter {
 *     override fun format(document: ExcelDocument): String = buildString {
 *         document.sheets.flatMap { it.elements }
 *             .filterIsInstance<Element.Table>()
 *             .forEach { table -> /* ... */ }
 *     }
 * }
 * ```
 */
interface DocumentFormatter {
    fun format(document: ExcelDocument): String
}
