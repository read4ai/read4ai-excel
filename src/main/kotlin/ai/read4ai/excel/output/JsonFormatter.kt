package ai.read4ai.excel.output

import ai.read4ai.excel.ExperimentalRead4ai
import ai.read4ai.excel.model.Element
import ai.read4ai.excel.model.ExcelDocument
import ai.read4ai.excel.model.MergeRegionInfo
import ai.read4ai.excel.model.Sheet
import tools.jackson.module.kotlin.jacksonObjectMapper

/**
 * Formats an [ExcelDocument] as JSON.
 *
 * The [layout] parameter selects the table representation:
 * - [Layout.COMPACT] — 2D string arrays with a sparse merge list (default)
 * - [Layout.ROW_OBJECT] — row objects `{"row": N, "cells": [...]}` with inline merge info
 *
 * The [assist] parameter optionally embeds a short system-prompt-like
 * `prompt` field at the document root and inside every sheet so the output
 * stays valid JSON while carrying its own usage instructions.
 *
 * Example:
 * ```kotlin
 * val doc = ExcelParser.parse(bytes)
 * val json = JsonFormatter().format(doc)                              // no prompt
 * val annotated = JsonFormatter(assist = Assist.ON).format(doc)       // "prompt" fields added
 * val rowObj = JsonFormatter(Layout.ROW_OBJECT).format(doc)           // ROW_OBJECT
 * ```
 *
 * @see DocumentFormatter
 */
class JsonFormatter @JvmOverloads constructor(
    @OptIn(ExperimentalRead4ai::class)
    private val layout: Layout = Layout.COMPACT,
    @OptIn(ExperimentalRead4ai::class)
    private val assist: Assist = Assist.NONE,
) : DocumentFormatter {

    @OptIn(ExperimentalRead4ai::class)
    override fun format(document: ExcelDocument): String = when (layout) {
        Layout.COMPACT -> formatCompact(document)
        Layout.ROW_OBJECT -> formatRowObject(document)
    }

    // ------------------------------------------------------------------
    // Compact layout
    // ------------------------------------------------------------------

    @OptIn(ExperimentalRead4ai::class)
    private fun formatCompact(document: ExcelDocument): String {
        val root = linkedMapOf<String, Any>()
        if (assist == Assist.ON) {
            root["prompt"] = PromptText.rootJsonCompact(document)
        }
        root["language"] = document.language
        root["sheets"] = document.sheets.map { compactSheet(it) }
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root)
    }

    @OptIn(ExperimentalRead4ai::class)
    private fun compactSheet(sheet: Sheet): Map<String, Any> {
        val map = linkedMapOf<String, Any>()
        map["sheetName"] = sheet.sheetName
        if (assist == Assist.ON) {
            map["prompt"] = PromptText.sheetJsonCompact(sheet)
        }
        map["elements"] = sheet.elements.map { compactElement(it) }
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
                tableBounds(element).forEach { (key, value) -> map[key] = value }
                if (element.headerRowCount > 0) map["headerRowCount"] = element.headerRowCount
                if (element.headerRowCount > 0) {
                    map["headerEndRow"] = element.startRow + element.headerRowCount
                    map["bodyStartRow"] = element.startRow + element.headerRowCount + 1
                }
                if (element.columnPaths.isNotEmpty()) {
                    map["columnPaths"] = element.columnPaths
                        .mapValues { (_, path) -> cleanHeaderPath(path) }
                        .filterValues { it.isNotEmpty() }
                        .mapKeys { (k, _) -> k.toString() }
                }
                if (element.rowPaths.isNotEmpty()) {
                    map["rowPaths"] = element.rowPaths.mapKeys { (k, _) -> k.toString() }
                }
                val headerArtifacts = headerArtifacts(element)
                if (headerArtifacts.resolvedHeaders.isNotEmpty()) {
                    map["resolvedHeaders"] = headerArtifacts.resolvedHeaders
                }
                if (headerArtifacts.headerCells.isNotEmpty()) {
                    map["headerCells"] = headerArtifacts.headerCells
                }
                val padCols = element.startCol
                map["rows"] = element.rows.map { row ->
                    val cells = row.cells.map { sanitizeMergePlaceholder(it.value) }
                    if (padCols > 0) List(padCols) { "" } + cells else cells
                }
                val merges = buildMergeList(element)
                if (merges.isNotEmpty()) map["merges"] = merges
                val mergedRanges = buildMergedRanges(element)
                if (mergedRanges.isNotEmpty()) map["mergedRanges"] = mergedRanges
                val mergedRangeDetails = buildMergedRangeDetails(element)
                if (mergedRangeDetails.isNotEmpty()) map["mergedRangeDetails"] = mergedRangeDetails
                map
            }
            is Element.Heading -> buildMap {
                put("type", "heading")
                put("text", element.text)
                put("level", element.level)
                if (element.startRow != 0) put("startRow", element.startRow + 1)
                if (element.startCol != 0) put("startCol", element.startCol + 1)
                put("cell", elementCell(element.startRow, element.startCol))
            }
            is Element.Text -> buildMap {
                put("type", "text")
                put("text", element.text)
                if (element.startRow != 0) put("startRow", element.startRow + 1)
                if (element.startCol != 0) put("startCol", element.startCol + 1)
                put("cell", elementCell(element.startRow, element.startCol))
            }
            is Element.Note -> buildMap {
                put("type", "note")
                put("text", element.text)
                if (element.startRow != 0) put("startRow", element.startRow + 1)
                if (element.startCol != 0) put("startCol", element.startCol + 1)
                put("cell", elementCell(element.startRow, element.startCol))
            }
            is Element.Image -> buildMap {
                put("type", "image")
                put("description", element.description ?: "")
                if (element.startRow != 0) put("startRow", element.startRow + 1)
                if (element.startCol != 0) put("startCol", element.startCol + 1)
                put("cell", elementCell(element.startRow, element.startCol))
            }
        }
    }

    // ------------------------------------------------------------------
    // Row-object layout
    // ------------------------------------------------------------------

    @OptIn(ExperimentalRead4ai::class)
    private fun formatRowObject(document: ExcelDocument): String {
        val root = linkedMapOf<String, Any>()
        if (assist == Assist.ON) {
            root["prompt"] = PromptText.rootJsonRowObject(document)
        }
        root["language"] = document.language
        root["sheets"] = document.sheets.map { rowObjectSheet(it) }
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root)
    }

    @OptIn(ExperimentalRead4ai::class)
    private fun rowObjectSheet(sheet: Sheet): Map<String, Any> {
        val map = linkedMapOf<String, Any>()
        map["sheetName"] = sheet.sheetName
        if (assist == Assist.ON) {
            map["prompt"] = PromptText.sheetJsonRowObject(sheet)
        }
        map["elements"] = sheet.elements.map { rowObjectElement(it) }
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
                tableBounds(element).forEach { (key, value) -> map[key] = value }
                if (element.headerRowCount > 0) map["headerRowCount"] = element.headerRowCount
                if (element.headerRowCount > 0) {
                    map["headerEndRow"] = element.startRow + element.headerRowCount
                    map["bodyStartRow"] = element.startRow + element.headerRowCount + 1
                }
                val headerArtifacts = headerArtifacts(element)
                if (headerArtifacts.resolvedHeaders.isNotEmpty()) {
                    map["resolvedHeaders"] = headerArtifacts.resolvedHeaders
                }
                if (headerArtifacts.headerCells.isNotEmpty()) {
                    map["headerCells"] = headerArtifacts.headerCells
                }
                val mergedRanges = buildMergedRanges(element)
                if (mergedRanges.isNotEmpty()) map["mergedRanges"] = mergedRanges
                val mergedRangeDetails = buildMergedRangeDetails(element)
                if (mergedRangeDetails.isNotEmpty()) map["mergedRangeDetails"] = mergedRangeDetails
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
            is Element.Heading -> buildMap {
                put("type", "heading")
                put("text", element.text)
                put("level", element.level)
                if (element.startRow != 0) put("startRow", element.startRow + 1)
                if (element.startCol != 0) put("startCol", element.startCol + 1)
                put("cell", elementCell(element.startRow, element.startCol))
            }
            is Element.Text -> buildMap {
                put("type", "text")
                put("text", element.text)
                if (element.startRow != 0) put("startRow", element.startRow + 1)
                if (element.startCol != 0) put("startCol", element.startCol + 1)
                put("cell", elementCell(element.startRow, element.startCol))
            }
            is Element.Note -> buildMap {
                put("type", "note")
                put("text", element.text)
                if (element.startRow != 0) put("startRow", element.startRow + 1)
                if (element.startCol != 0) put("startCol", element.startCol + 1)
                put("cell", elementCell(element.startRow, element.startCol))
            }
            is Element.Image -> buildMap {
                put("type", "image")
                put("description", element.description ?: "")
                if (element.startRow != 0) put("startRow", element.startRow + 1)
                if (element.startCol != 0) put("startCol", element.startCol + 1)
                put("cell", elementCell(element.startRow, element.startCol))
            }
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
                    val value = sanitizeMergePlaceholder(cell.value)
                    if (value.isNotBlank()) entry["value"] = value
                    merges.add(entry)
                }
            }
        }
        return merges
    }

    private fun buildMergedRanges(table: Element.Table): List<String> =
        buildMergeList(table).map { merge ->
            mergeRange(merge)
        }

    private fun buildMergedRangeDetails(table: Element.Table): List<Map<String, Any>> =
        buildMergeList(table).mapNotNull { merge ->
            val value = merge["value"] as? String ?: return@mapNotNull null
            val detail = linkedMapOf<String, Any>(
                "range" to mergeRange(merge),
                "cell" to (merge["cell"] as String),
                "value" to value,
            )
            detail
        }

    private fun tableBounds(table: Element.Table): Map<String, Any> {
        val rowCount = table.rows.size
        if (rowCount == 0) return emptyMap()

        val logicalColCount = table.rows.maxOfOrNull { row ->
            row.cells.sumOf { cell -> 1 + cell.mergedRight }
        } ?: 0
        if (logicalColCount == 0) return emptyMap()

        val startRow = table.startRow + 1
        val startCol = table.startCol + 1
        val endRow = startRow + rowCount - 1
        val endCol = startCol + logicalColCount - 1
        val endColLetter = columnIndexToLetter(endCol - 1)
        val startColLetter = columnIndexToLetter(startCol - 1)

        return linkedMapOf(
            "endRow" to endRow,
            "endCol" to endCol,
            "rowCount" to rowCount,
            "colCount" to logicalColCount,
            "range" to "$startColLetter$startRow:$endColLetter$endRow",
        )
    }

    private fun elementCell(startRow: Int, startCol: Int): String =
        "${columnIndexToLetter(startCol)}${startRow + 1}"

    private fun headerArtifacts(table: Element.Table): HeaderArtifacts {
        if (table.headerRowCount <= 0 || table.rows.isEmpty()) return HeaderArtifacts(emptyMap(), emptyMap())

        val headerRows = table.rows.take(table.headerRowCount)
        val colCount = headerRows.maxOfOrNull { row -> row.cells.size } ?: 0
        if (colCount == 0) return HeaderArtifacts(emptyMap(), emptyMap())

        val resolvedGrid = MutableList(table.headerRowCount) { rowIdx ->
            MutableList(colCount) { colIdx ->
                headerRows.getOrNull(rowIdx)?.cells?.getOrNull(colIdx)?.value ?: ""
            }
        }
        val anchorGrid = MutableList(table.headerRowCount) {
            MutableList<Pair<Int, Int>?>(colCount) { null }
        }

        for ((rowIdx, row) in headerRows.withIndex()) {
            for ((colIdx, cell) in row.cells.withIndex()) {
                val value = sanitizeMergePlaceholder(cell.value)
                if (value.isBlank()) continue
                val lastRow = minOf(table.headerRowCount - 1, rowIdx + cell.mergedDown)
                val lastCol = minOf(colCount - 1, colIdx + cell.mergedRight)
                for (r in rowIdx..lastRow) {
                    for (c in colIdx..lastCol) {
                        resolvedGrid[r][c] = value
                        anchorGrid[r][c] = rowIdx to colIdx
                    }
                }
            }
        }

        val informativeRows = (0 until table.headerRowCount).filter { row ->
            val nonBlankDistinct = resolvedGrid[row].map { it.trim() }.filter { it.isNotBlank() }.distinct()
            nonBlankDistinct.size >= 2
        }
        if (informativeRows.isEmpty()) return HeaderArtifacts(emptyMap(), emptyMap())

        val resolved = linkedMapOf<String, String>()
        val headerCells = linkedMapOf<String, String>()
        for (col in 0 until colCount) {
            val path = when {
                table.columnPaths.containsKey(col) -> cleanHeaderPath(table.columnPaths.getValue(col))
                else -> informativeRows.mapNotNull { row ->
                    sanitizeMergePlaceholder(resolvedGrid[row][col]).takeIf { it.isNotBlank() }
                }.distinct()
            }
            if (path.isEmpty()) continue

            val key = (table.startCol + col + 1).toString()
            resolved[key] = path.joinToString(" > ")
            informativeRows.asReversed().firstNotNullOfOrNull { row ->
                sanitizeMergePlaceholder(resolvedGrid[row][col]).takeIf { it.isNotBlank() }
                    ?.let { anchorGrid[row][col] }
            }?.let { (anchorRow, anchorCol) ->
                headerCells[key] = elementCell(table.startRow + anchorRow, table.startCol + anchorCol)
            }
        }
        return HeaderArtifacts(resolved, headerCells)
    }

    private fun cleanHeaderPath(path: List<String>): List<String> =
        path.map(::sanitizeMergePlaceholder)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()

    private fun mergeRange(merge: Map<String, Any>): String {
        val startCell = merge["cell"] as String
        val startColLetters = startCell.takeWhile { it.isLetter() }
        val startRowNumber = startCell.dropWhile { it.isLetter() }.toInt()
        val startColIndex = columnLetterToIndex(startColLetters)
        val rowSpan = (merge["rowSpan"] as? Int ?: 1)
        val colSpan = (merge["colSpan"] as? Int ?: 1)
        val endColLetters = columnIndexToLetter(startColIndex + colSpan - 1)
        val endRowNumber = startRowNumber + rowSpan - 1
        return "$startCell:$endColLetters$endRowNumber"
    }

    private data class HeaderArtifacts(
        val resolvedHeaders: Map<String, String>,
        val headerCells: Map<String, String>,
    )

    companion object {
        private val mapper = jacksonObjectMapper()

        private val MERGE_PLACEHOLDERS = setOf("<", "^", "<^", "^<")

        /**
         * Deserialize a JSON string to an [ExcelDocument].
         * Works with any JSON produced by [format], [toRawJson], or [toRawPrettyJson].
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

    private fun columnLetterToIndex(letter: String): Int {
        var result = 0
        for (ch in letter) {
            result = result * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return result - 1
    }

    private fun sanitizeMergePlaceholder(value: String): String =
        if (value in MERGE_PLACEHOLDERS) "" else value
}
