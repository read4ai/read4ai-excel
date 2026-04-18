package ai.read4ai.excel.output

import ai.read4ai.excel.model.ExcelDocument

/**
 * Converts an [ExcelDocument] to a text representation.
 *
 * Implementations are the output counterpart to the pipeline interfaces:
 * while `Segmenter`, `HeaderDetector`, etc. control how Excel is *parsed*,
 * `DocumentWriter` controls how the parsed result is *serialized*.
 *
 * Built-in implementations:
 * - [JsonWriter] — compact or row-object JSON
 * - [MarkdownWriter] — pipe-table Markdown
 *
 * Example custom writer:
 * ```kotlin
 * class CsvWriter : DocumentWriter {
 *     override fun write(document: ExcelDocument): String = buildString {
 *         document.sheets.flatMap { it.elements }
 *             .filterIsInstance<Element.Table>()
 *             .forEach { table -> /* ... */ }
 *     }
 * }
 * ```
 */
interface DocumentWriter {
    fun write(document: ExcelDocument): String
}
