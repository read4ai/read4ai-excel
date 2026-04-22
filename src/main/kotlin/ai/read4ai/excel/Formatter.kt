package ai.read4ai.excel

import ai.read4ai.excel.model.ExcelDocument
import ai.read4ai.excel.output.Assist
import ai.read4ai.excel.output.JsonFormatter
import ai.read4ai.excel.output.Layout
import ai.read4ai.excel.output.MarkdownFormatter

/**
 * Convenience facade for converting an [ExcelDocument] to AI-friendly text.
 *
 * Three independent axes:
 * - **Format**: JSON ([toJson]) or Markdown ([toMarkdown])
 * - **Layout**: [Layout.COMPACT] (default) or [Layout.ROW_OBJECT] (JSON only, experimental)
 * - **Assist**: [Assist.NONE] (default) or [Assist.ON] (embeds a system-prompt-like
 *   `prompt` block at the document root and inside every sheet so an LLM can
 *   interpret the payload without external instructions)
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
    fun toJson(
        document: ExcelDocument,
        layout: Layout = Layout.COMPACT,
        assist: Assist = Assist.NONE,
    ): String = JsonFormatter(layout, assist).format(document)

    /** Markdown pipe tables. Only [Layout.COMPACT] is supported. */
    @JvmStatic
    @JvmOverloads
    @OptIn(ExperimentalRead4ai::class)
    fun toMarkdown(
        document: ExcelDocument,
        layout: Layout = Layout.COMPACT,
        assist: Assist = Assist.NONE,
    ): String = MarkdownFormatter(layout, assist).format(document)
}
