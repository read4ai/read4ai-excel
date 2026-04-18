package ai.read4ai.excel

import ai.read4ai.excel.model.ExcelDocument
import ai.read4ai.excel.output.JsonFormatter
import ai.read4ai.excel.output.JsonLayout
import ai.read4ai.excel.output.MarkdownFormatter

/**
 * Convenience facade for converting an [ExcelDocument] to AI-friendly text.
 *
 * Delegates to [JsonFormatter] and [MarkdownFormatter] which implement the
 * [ai.read4ai.excel.output.DocumentFormatter] interface. For full control,
 * use the formatters directly.
 */
object Formatter {

    /** Compact JSON with 1-based row indices and minimal merge fields. */
    @JvmStatic
    @JvmOverloads
    @OptIn(ExperimentalRead4ai::class)
    fun toJson(document: ExcelDocument, layout: JsonLayout = JsonLayout.COMPACT): String =
        JsonFormatter(layout).format(document)

    /** Markdown pipe tables with row indices and merge annotations. */
    @JvmStatic
    fun toMarkdown(document: ExcelDocument): String =
        MarkdownFormatter().format(document)
}
