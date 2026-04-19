package ai.read4ai.excel

import ai.read4ai.excel.model.ExcelDocument
import ai.read4ai.excel.output.JsonFormatter
import ai.read4ai.excel.output.Layout
import ai.read4ai.excel.output.MarkdownFormatter

/**
 * Convenience facade for converting an [ExcelDocument] to AI-friendly text.
 *
 * Two independent axes:
 * - **Format**: JSON ([toJson]) or Markdown ([toMarkdown])
 * - **Layout**: [Layout.COMPACT] (default) or [Layout.ROW_OBJECT] (JSON only, experimental)
 *
 * Delegates to [JsonFormatter] and [MarkdownFormatter] which implement the
 * [ai.read4ai.excel.output.DocumentFormatter] interface. For full control,
 * use the formatters directly.
 */
object Formatter {

    /** JSON output. Defaults to compact 2D arrays; pass [Layout.ROW_OBJECT] for row records. */
    @JvmStatic
    @JvmOverloads
    @OptIn(ExperimentalRead4ai::class)
    fun toJson(document: ExcelDocument, layout: Layout = Layout.COMPACT): String =
        JsonFormatter(layout).format(document)

    /** Markdown pipe tables. Only [Layout.COMPACT] is supported. */
    @JvmStatic
    @JvmOverloads
    fun toMarkdown(document: ExcelDocument, layout: Layout = Layout.COMPACT): String =
        MarkdownFormatter(layout).format(document)
}
