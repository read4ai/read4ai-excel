package ai.read4ai.excel.output

import ai.read4ai.excel.model.Element
import ai.read4ai.excel.model.ExcelDocument
import ai.read4ai.excel.model.MergeRegionInfo
import ai.read4ai.excel.model.Sheet

/**
 * Converts an [ExcelDocument] to Markdown pipe-table format.
 *
 * Features:
 * - Row indices (1-based absolute)
 * - Merge annotations `[merged RxC]`
 * - Column/row hierarchy paths
 * - Sheet-level merge region summary
 *
 * Example:
 * ```kotlin
 * val doc = ExcelParser.parse(bytes)
 * val md = MarkdownWriter().write(doc)
 * ```
 *
 * @see DocumentWriter
 */
class MarkdownWriter : DocumentWriter {

    override fun write(document: ExcelDocument): String = toMarkdown(document)

    /**
     * Convert an entire [ExcelDocument] to Markdown.
     *
     * @param includeFileName if true, prepend `# filename` heading
     */
    fun toMarkdown(document: ExcelDocument, includeFileName: Boolean = false): String {
        return buildString {
            if (includeFileName) {
                document.fileName?.let {
                    append("# ").append(it).append("\n\n")
                }
            }
            if (document.sheets.isNotEmpty()) {
                append("_Language: ").append(document.language).append("_\n\n")
            }

            document.sheets.forEachIndexed { index, sheet ->
                if (index > 0) append("\n\n")
                append(sheetToMarkdown(sheet))
            }
        }.trimEnd()
    }

    /** Convert a single [Sheet] to Markdown. */
    fun sheetToMarkdown(sheet: Sheet): String {
        return buildString {
            append("## ").append(sheet.sheetName).append("\n\n")
            if (sheet.mergeRegions.isNotEmpty()) {
                append("_Merged: ")
                append(sheet.mergeRegions.joinToString(", ") { formatMergeRange(it) })
                append("_\n")
                append("_[merged RxC] = spans R rows × C columns_\n\n")
            }
            sheet.elements.forEachIndexed { index, element ->
                if (index > 0) append("\n\n")
                append(elementToMarkdown(element))
            }
        }
    }

    /** Convert a single [Element] to Markdown. */
    fun elementToMarkdown(element: Element): String {
        return when (element) {
            is Element.Table -> tableToMarkdown(element)
            is Element.Heading -> headingToMarkdown(element)
            is Element.Text -> element.text
            is Element.Image -> imageToMarkdown(element)
            is Element.Note -> "> ${element.text}"
        }
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private fun formatMergeRange(info: MergeRegionInfo): String {
        val startCell = info.cell
        if (info.rowSpan <= 1 && info.colSpan <= 1) return startCell
        val match = Regex("([A-Z]+)(\\d+)").matchEntire(startCell) ?: return startCell
        val startCol = match.groupValues[1]
        val startRow = match.groupValues[2].toInt()
        val endRow = startRow + info.rowSpan - 1
        val endCol = offsetColumn(startCol, info.colSpan - 1)
        return "$startCell:$endCol$endRow"
    }

    private fun offsetColumn(col: String, offset: Int): String {
        var num = 0
        for (c in col) num = num * 26 + (c - 'A' + 1)
        num += offset
        val sb = StringBuilder()
        var n = num
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.insert(0, ('A' + rem))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun tableToMarkdown(table: Element.Table): String {
        if (table.rows.isEmpty()) return "*Empty table*"

        val colCount = table.rows.maxOf { it.cells.size }
        if (colCount == 0) return "*Empty table*"

        val hasColumnPaths = table.columnPaths.isNotEmpty()
        val hasRowPaths = table.rowPaths.isNotEmpty()

        return buildString {
            if (table.startRow != 0 || table.startCol != 0) {
                append("_Table starts at row ${table.startRow + 1}, col ${table.startCol + 1}_\n")
            }
            if (table.headerRowCount > 1) {
                append("_Headers: ${table.headerRowCount} rows_\n")
            }
            if (hasColumnPaths) {
                append("_Column hierarchy:_\n")
                for ((col, path) in table.columnPaths.toSortedMap()) {
                    append("- Col ${col + 1}: ${path.joinToString(" > ")}\n")
                }
            }
            if (table.startRow != 0 || table.startCol != 0 || table.headerRowCount > 1 || hasColumnPaths) {
                append("\n")
            }

            val effectiveHeaderRows = if (table.headerRowCount > 0) table.headerRowCount else 1

            table.rows.forEachIndexed { rowIndex, row ->
                val absRow = table.startRow + row.rowIndex + 1

                append("| $absRow | ")

                val dataRowIdx = rowIndex - effectiveHeaderRows
                val rowPath = if (hasRowPaths && dataRowIdx >= 0) {
                    table.rowPaths[dataRowIdx]
                } else {
                    null
                }
                if (hasRowPaths) {
                    if (rowPath != null) {
                        append("| _${rowPath.joinToString(" > ")}_ ")
                    } else {
                        append("| ")
                    }
                }

                val cells = (0 until colCount).map { colIdx ->
                    val cell = row.cells.getOrNull(colIdx)
                    val value = cell?.value ?: ""
                    val sanitized = sanitizeForMarkdownTable(value)
                    if (cell != null && (cell.mergedRight > 0 || cell.mergedDown > 0)) {
                        val rs = cell.mergedDown + 1
                        val cs = cell.mergedRight + 1
                        "$sanitized [merged ${rs}x${cs}]"
                    } else {
                        sanitized
                    }
                }
                append(cells.joinToString(" | ")).append(" |")

                if (rowIndex == effectiveHeaderRows - 1) {
                    append("\n")
                    val extraCols = 1 + (if (hasRowPaths) 1 else 0)
                    val totalCols = colCount + extraCols
                    append("| ").append((0 until totalCols).joinToString(" | ") { "---" }).append(" |")
                }

                if (rowIndex < table.rows.size - 1) {
                    append("\n")
                }
            }
        }
    }

    private fun headingToMarkdown(heading: Element.Heading): String {
        val prefix = "#".repeat(heading.level.coerceIn(1, 6))
        return "$prefix ${heading.text}"
    }

    private fun imageToMarkdown(image: Element.Image): String {
        return when {
            image.description != null -> image.description
            image.base64 != null -> "[Image: ${image.mimeType ?: "unknown"}]"
            else -> "[Image]"
        }
    }

    companion object {
        private val MERGE_PLACEHOLDERS = setOf("<", "^", "<^", "^<")

        private fun sanitizeForMarkdownTable(value: String): String {
            if (value in MERGE_PLACEHOLDERS) return ""
            return value
                .replace("|", "\\|")
                .replace("\r\n", "<br>")
                .replace("\n", "<br>")
                .replace("\r", "<br>")
        }
    }
}
