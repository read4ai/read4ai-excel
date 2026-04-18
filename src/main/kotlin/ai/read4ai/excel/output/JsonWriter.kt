package ai.read4ai.excel.output

import ai.read4ai.excel.ExperimentalRead4ai
import ai.read4ai.excel.model.Element
import ai.read4ai.excel.model.ExcelDocument
import ai.read4ai.excel.model.MergeRegionInfo
import ai.read4ai.excel.model.Sheet
import tools.jackson.module.kotlin.jacksonObjectMapper

/**
 * Writes an [ExcelDocument] to JSON.
 *
 * The [layout] parameter selects the table representation:
 * - [JsonLayout.COMPACT] — 2D string arrays with a sparse merge list (default)
 * - [JsonLayout.ROW_OBJECT] — row objects `{"row": N, "cells": [...]}` with inline merge info
 *
 * Example:
 * ```kotlin
 * val doc = ExcelParser.parse(bytes)
 * val json = JsonWriter().write(doc)                          // COMPACT
 * val rowObj = JsonWriter(JsonLayout.ROW_OBJECT).write(doc)   // ROW_OBJECT
 * ```
 *
 * @see DocumentWriter
 */
class JsonWriter @JvmOverloads constructor(
    @OptIn(ExperimentalRead4ai::class)
    private val layout: JsonLayout = JsonLayout.COMPACT,
) : DocumentWriter {

    @OptIn(ExperimentalRead4ai::class)
    override fun write(document: ExcelDocument): String = when (layout) {
        JsonLayout.COMPACT -> writeCompact(document)
        JsonLayout.ROW_OBJECT -> writeRowObject(document)
    }

    // ------------------------------------------------------------------
    // Compact layout
    // ------------------------------------------------------------------

    private fun writeCompact(document: ExcelDocument): String {
        val root = mutableMapOf<String, Any>(
            "language" to document.language,
            "sheets" to document.sheets.map { compactSheet(it) },
        )
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root)
    }

    private fun compactSheet(sheet: Sheet): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "sheetName" to sheet.sheetName,
            "elements" to sheet.elements.map { compactElement(it) },
        )
        if (sheet.mergeRegions.isNotEmpty()) {
            map["mergeRegions"] = sheet.mergeRegions.map { formatMergeRange(it) }
        }
        return map
    }

    private fun compactElement(element: Element): Map<String, Any> {
        return when (element) {
            is Element.Table -> {
                val map = mutableMapOf<String, Any>("type" to "table")
                if (element.startRow != 0) map["startRow"] = element.startRow + 1
                if (element.startCol != 0) map["startCol"] = element.startCol + 1
                if (element.headerRowCount > 0) map["headerRowCount"] = element.headerRowCount
                if (element.columnPaths.isNotEmpty()) {
                    map["columnPaths"] = element.columnPaths.mapKeys { (k, _) -> k.toString() }
                }
                if (element.rowPaths.isNotEmpty()) {
                    map["rowPaths"] = element.rowPaths.mapKeys { (k, _) -> k.toString() }
                }
                val padCols = element.startCol
                map["rows"] = element.rows.map { row ->
                    val cells = row.cells.map { sanitizeMergePlaceholder(it.value) }
                    if (padCols > 0) List(padCols) { "" } + cells else cells
                }
                val merges = buildMergeList(element)
                if (merges.isNotEmpty()) map["merges"] = merges
                map
            }
            is Element.Heading -> mapOf("type" to "heading", "text" to element.text, "level" to element.level)
            is Element.Text -> mapOf("type" to "text", "text" to element.text)
            is Element.Note -> mapOf("type" to "note", "text" to element.text)
            is Element.Image -> mapOf("type" to "image", "description" to (element.description ?: ""))
        }
    }

    // ------------------------------------------------------------------
    // Row-object layout
    // ------------------------------------------------------------------

    private fun writeRowObject(document: ExcelDocument): String {
        val root = mutableMapOf<String, Any>(
            "language" to document.language,
            "sheets" to document.sheets.map { rowObjectSheet(it) },
        )
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root)
    }

    private fun rowObjectSheet(sheet: Sheet): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "sheetName" to sheet.sheetName,
            "elements" to sheet.elements.map { rowObjectElement(it) },
        )
        if (sheet.mergeRegions.isNotEmpty()) {
            map["mergeRegions"] = sheet.mergeRegions.map { formatMergeRange(it) }
        }
        return map
    }

    private fun rowObjectElement(element: Element): Map<String, Any> {
        return when (element) {
            is Element.Table -> {
                val map = mutableMapOf<String, Any>("type" to "table")
                if (element.startRow != 0) map["startRow"] = element.startRow + 1
                if (element.startCol != 0) map["startCol"] = element.startCol + 1
                if (element.headerRowCount > 0) map["headerRowCount"] = element.headerRowCount
                val roPadCols = element.startCol
                map["rows"] = element.rows.map { row ->
                    val cells = row.cells.map { cell ->
                        val v = sanitizeMergePlaceholder(cell.value)
                        if (cell.mergedRight == 0 && cell.mergedDown == 0) {
                            v as Any
                        } else {
                            val m = mutableMapOf<String, Any>("v" to v)
                            if (cell.mergedRight > 0) m["mr"] = cell.mergedRight
                            if (cell.mergedDown > 0) m["md"] = cell.mergedDown
                            m
                        }
                    }
                    val paddedCells: List<Any> = if (roPadCols > 0) List(roPadCols) { "" as Any } + cells else cells
                    mapOf("row" to (element.startRow + row.rowIndex + 1), "cells" to paddedCells)
                }
                map
            }
            is Element.Heading -> mapOf("type" to "heading", "text" to element.text, "level" to element.level)
            is Element.Text -> mapOf("type" to "text", "text" to element.text)
            is Element.Note -> mapOf("type" to "note", "text" to element.text)
            is Element.Image -> mapOf("type" to "image", "description" to (element.description ?: ""))
        }
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private fun buildMergeList(table: Element.Table): List<Map<String, Any>> {
        val merges = mutableListOf<Map<String, Any>>()
        for (row in table.rows) {
            for ((colIdx, cell) in row.cells.withIndex()) {
                if (cell.mergedRight > 0 || cell.mergedDown > 0) {
                    val absRow = table.startRow + row.rowIndex + 1
                    val colLetter = columnIndexToLetter(table.startCol + colIdx)
                    val entry = mutableMapOf<String, Any>("cell" to "$colLetter$absRow")
                    if (cell.mergedDown > 0) entry["rowSpan"] = cell.mergedDown + 1
                    if (cell.mergedRight > 0) entry["colSpan"] = cell.mergedRight + 1
                    merges.add(entry)
                }
            }
        }
        return merges
    }

    companion object {
        private val mapper = jacksonObjectMapper()

        private val MERGE_PLACEHOLDERS = setOf("<", "^", "<^", "^<")

        /**
         * Deserialize a JSON string to an [ExcelDocument].
         * Works with any JSON produced by [write], [toRawJson], or [toRawPrettyJson].
         */
        fun fromJson(json: String): ExcelDocument =
            mapper.readValue(json, ExcelDocument::class.java)

        /** Raw Jackson serialization (model as-is, no layout transformation). */
        fun toRawJson(document: ExcelDocument): String =
            mapper.writeValueAsString(document)

        /** Raw Jackson serialization with pretty-printing. */
        fun toRawPrettyJson(document: ExcelDocument): String =
            mapper.writerWithDefaultPrettyPrinter().writeValueAsString(document)

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

        private fun columnIndexToLetter(index: Int): String {
            val sb = StringBuilder()
            var c = index
            while (c >= 0) {
                sb.insert(0, ('A' + c % 26))
                c = c / 26 - 1
            }
            return sb.toString()
        }

        private fun sanitizeMergePlaceholder(value: String): String =
            if (value in MERGE_PLACEHOLDERS) "" else value
    }
}
