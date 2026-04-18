package ai.read4ai.excel

import ai.read4ai.excel.model.ExcelDocument
import ai.read4ai.excel.output.JsonLayout
import ai.read4ai.excel.output.JsonWriter
import ai.read4ai.excel.output.MarkdownWriter

/**
 * Convenience facade for converting an [ExcelDocument] to AI-friendly text.
 *
 * Delegates to [JsonWriter] and [MarkdownWriter] which implement the
 * [ai.read4ai.excel.output.DocumentWriter] interface. For full control, use
 * the writers directly.
 */
object Formatter {

    /** Compact JSON with 1-based row indices and minimal merge fields. */
    @JvmStatic
    @JvmOverloads
    @OptIn(ExperimentalRead4ai::class)
    fun toJson(document: ExcelDocument, layout: JsonLayout = JsonLayout.COMPACT): String =
        JsonWriter(layout).write(document)

    /** Markdown pipe tables with row indices and merge annotations. */
    @JvmStatic
    fun toMarkdown(document: ExcelDocument): String =
        MarkdownWriter().write(document)
}
