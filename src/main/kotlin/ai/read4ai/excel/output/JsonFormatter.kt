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
 * The [assist] parameter optionally embeds short output guidance
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
        map["elements"] = compactElements(sheet.elements)
        if (sheet.mergeRegions.isNotEmpty()) {
            map["mergeRegions"] = sheet.mergeRegions.map { formatMergeRange(it) }
        }
        return map
    }

    private fun compactElements(elements: List<Element>): List<Map<String, Any>> =
        elementsWithSections(elements) { element -> compactElement(element) }

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
                        .mapKeys { (k, _) -> (element.startCol + k + 1).toString() }
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
                val columns = buildColumnMetadata(element, headerArtifacts)
                if (columns.isNotEmpty()) {
                    map["columns"] = columns
                }
                val matrixRows = buildMatrixRows(element, headerArtifacts)
                if (matrixRows.isNotEmpty()) {
                    map["matrixRows"] = matrixRows
                }
                val matrixTransitions = buildMatrixTransitions(matrixRows)
                if (matrixTransitions.isNotEmpty()) {
                    map["matrixTransitions"] = matrixTransitions
                }
                val rowNumbers = buildRowNumbers(element)
                if (rowNumbers.isNotEmpty()) map["rowNumbers"] = rowNumbers
                val rowAnchors = buildRowAnchors(element)
                if (rowAnchors.isNotEmpty()) map["rowAnchors"] = rowAnchors
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
        map["elements"] = rowObjectElements(sheet.elements)
        if (sheet.mergeRegions.isNotEmpty()) {
            map["mergeRegions"] = sheet.mergeRegions.map { formatMergeRange(it) }
        }
        return map
    }

    private fun rowObjectElements(elements: List<Element>): List<Map<String, Any>> =
        elementsWithSections(elements) { element -> rowObjectElement(element) }

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
                val columns = buildColumnMetadata(element, headerArtifacts)
                if (columns.isNotEmpty()) {
                    map["columns"] = columns
                }
                val matrixRows = buildMatrixRows(element, headerArtifacts)
                if (matrixRows.isNotEmpty()) {
                    map["matrixRows"] = matrixRows
                }
                val matrixTransitions = buildMatrixTransitions(matrixRows)
                if (matrixTransitions.isNotEmpty()) {
                    map["matrixTransitions"] = matrixTransitions
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

    private fun elementsWithSections(
        elements: List<Element>,
        formatElement: (Element) -> Map<String, Any>,
    ): List<Map<String, Any>> {
        var currentSection: SectionRef? = null
        return elements.map { element ->
            val formatted = formatElement(element).toMutableMap()
            if (element is Element.Table) {
                tableSection(currentSection, element)?.let { section ->
                    formatted["section"] = section
                    val sectionHeaderCells = buildSectionHeaderCells(section, formatted["columns"])
                    if (sectionHeaderCells.isNotEmpty()) {
                        formatted["sectionHeaderCells"] = sectionHeaderCells
                    }
                }
            }
            sectionRef(element)?.let { currentSection = it }
            formatted
        }
    }

    private fun tableSection(section: SectionRef?, table: Element.Table): Map<String, Any>? {
        section ?: return null
        if (section.startRow >= table.startRow) return null
        if (table.startRow - section.startRow > SECTION_MAX_ROW_GAP) return null
        return linkedMapOf(
            "text" to section.text,
            "cell" to elementCell(section.startRow, section.startCol),
        )
    }

    private fun sectionRef(element: Element): SectionRef? =
        when (element) {
            is Element.Heading -> SectionRef(element.text, element.startRow, element.startCol)
            else -> null
        }

    @Suppress("UNCHECKED_CAST")
    private fun buildSectionHeaderCells(section: Map<String, Any>, columns: Any?): Map<String, String> {
        val sectionText = section["text"] as? String ?: return emptyMap()
        val columnEntries = columns as? List<Map<String, Any>> ?: return emptyMap()
        if (columnEntries.size > SECTION_HEADER_CELLS_MAX_COLUMNS) return emptyMap()
        return columnEntries.mapNotNull { column ->
            val header = column["header"] as? String ?: return@mapNotNull null
            val headerCell = column["headerCell"] as? String ?: return@mapNotNull null
            "$sectionText > $header" to headerCell
        }.toMap()
    }

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

        val bounds = linkedMapOf<String, Any>(
            "endRow" to endRow,
            "endCol" to endCol,
            "rowCount" to rowCount,
            "colCount" to logicalColCount,
            "range" to "$startColLetter$startRow:$endColLetter$endRow",
        )
        if (table.startCol > 0) {
            bounds["leadingBlankColCount"] = table.startCol
            bounds["sheetColCount"] = table.startCol + logicalColCount
        }
        return bounds
    }

    private fun buildRowNumbers(table: Element.Table): List<Int> {
        if (!shouldEmitRowIdentity(table)) return emptyList()
        return table.rows.map { row -> table.startRow + row.rowIndex + 1 }
    }

    private fun buildRowAnchors(table: Element.Table): List<Map<String, Any>> {
        if (!shouldEmitRowIdentity(table)) return emptyList()

        val bodyRows = table.rows.drop(table.headerRowCount.coerceAtLeast(0))
        if (bodyRows.isEmpty()) return emptyList()

        return bodyRows.mapNotNull { row ->
            row.cells.withIndex().firstNotNullOfOrNull { (colIdx, cell) ->
                val label = sanitizeMergePlaceholder(cell.value).trim()
                if (label.isBlank()) return@firstNotNullOfOrNull null

                linkedMapOf(
                    "row" to (table.startRow + row.rowIndex + 1),
                    "cell" to elementCell(table.startRow + row.rowIndex, table.startCol + colIdx),
                    "label" to label,
                )
            }
        }.take(ROW_ANCHOR_LIMIT)
    }

    private fun shouldEmitRowIdentity(table: Element.Table): Boolean =
        table.rows.size in 2..ROW_IDENTITY_MAX_ROWS && (
            tableColumnCount(table) <= ROW_IDENTITY_SINGLE_COL_MAX ||
                (tableColumnCount(table) <= ROW_IDENTITY_MERGED_MAX_COLS && hasVerticalMerges(table))
            )

    private fun tableColumnCount(table: Element.Table): Int =
        table.rows.maxOfOrNull { row -> row.cells.sumOf { cell -> 1 + cell.mergedRight } } ?: 0

    private fun hasVerticalMerges(table: Element.Table): Boolean =
        table.rows.any { row -> row.cells.any { cell -> cell.mergedDown > 0 } }

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

    private fun buildColumnMetadata(
        table: Element.Table,
        headerArtifacts: HeaderArtifacts,
    ): List<Map<String, Any>> {
        if (!shouldEmitColumnMetadata(table, headerArtifacts)) return emptyList()

        return headerArtifacts.resolvedHeaders.mapNotNull { (key, header) ->
            val index = key.toIntOrNull() ?: return@mapNotNull null
            val entry = linkedMapOf<String, Any>(
                "index" to index,
                "letter" to columnIndexToLetter(index - 1),
                "header" to header,
            )
            headerArtifacts.headerCells[key]?.let { entry["headerCell"] = it }
            entry
        }
    }

    private fun shouldEmitColumnMetadata(
        table: Element.Table,
        headerArtifacts: HeaderArtifacts,
    ): Boolean {
        if (headerArtifacts.resolvedHeaders.isEmpty()) return false
        return table.startCol > 0 || table.headerRowCount > 1 || tableColumnCount(table) > COLUMN_METADATA_WIDE_MIN_COLS
    }

    private fun buildMatrixRows(
        table: Element.Table,
        headerArtifacts: HeaderArtifacts,
    ): List<Map<String, Any>> {
        if (table.headerRowCount <= 0) return emptyList()
        if (headerArtifacts.resolvedHeaders.isEmpty()) return emptyList()

        val bodyRows = table.rows.drop(table.headerRowCount)
        if (bodyRows.size < MATRIX_MIN_BODY_ROWS) return emptyList()

        val maxCols = table.rows.maxOfOrNull { it.cells.size } ?: return emptyList()
        val matrixCols = (0 until maxCols).filter { col ->
            val values = bodyRows.mapNotNull { row ->
                row.cells.getOrNull(col)?.value
                    ?.let(::sanitizeMergePlaceholder)
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
            }
            values.size >= MATRIX_MIN_COLUMN_VALUES && values.all(::isMatrixValue)
        }
        if (matrixCols.size < MATRIX_MIN_VALUE_COLUMNS) return emptyList()

        val firstMatrixCol = matrixCols.first()
        return bodyRows.mapNotNull { row ->
            val keyParts = row.cells.take(firstMatrixCol)
                .map { sanitizeMergePlaceholder(it.value).trim() }
                .filter(String::isNotBlank)
                .distinct()
            if (keyParts.isEmpty()) return@mapNotNull null

            val values = linkedMapOf<String, String>()
            for (col in matrixCols) {
                val value = row.cells.getOrNull(col)
                    ?.value
                    ?.let(::sanitizeMergePlaceholder)
                    ?.trim()
                    ?.takeIf(::isMatrixValue)
                    ?: continue
                val absCol = table.startCol + col + 1
                val header = headerArtifacts.resolvedHeaders[absCol.toString()] ?: continue
                values[header] = value
            }
            if (values.isEmpty()) return@mapNotNull null

            val groups = values.entries
                .groupBy({ it.value }, { it.key })
                .toSortedMap()
            linkedMapOf<String, Any>(
                "row" to (table.startRow + row.rowIndex + 1),
                "key" to keyParts.joinToString(" / "),
                "values" to values,
            ).also { entry ->
                if (groups.isNotEmpty()) entry["groups"] = groups
            }
        }.take(MATRIX_ROW_LIMIT)
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildMatrixTransitions(matrixRows: List<Map<String, Any>>): List<Map<String, Any>> {
        if (matrixRows.size < 2) return emptyList()

        return matrixRows.zipWithNext().mapNotNull { (from, to) ->
            val fromValues = from["values"] as? Map<String, String> ?: return@mapNotNull null
            val toValues = to["values"] as? Map<String, String> ?: return@mapNotNull null

            val keys = fromValues.keys.filter { it in toValues }
            val changes = linkedMapOf<String, List<String>>()
            for (fromValue in MATRIX_VALUES) {
                for (toValue in MATRIX_VALUES) {
                    if (fromValue == toValue) continue
                    val changed = keys.filter { key ->
                        fromValues[key]?.uppercase() == fromValue && toValues[key]?.uppercase() == toValue
                    }
                    if (changed.isNotEmpty()) {
                        changes["$fromValue->$toValue"] = changed
                    }
                }
            }
            if (changes.isEmpty()) return@mapNotNull null

            linkedMapOf(
                "fromRow" to (from["row"] as Any),
                "fromKey" to (from["key"] as Any),
                "toRow" to (to["row"] as Any),
                "toKey" to (to["key"] as Any),
                "changes" to changes,
            )
        }.take(MATRIX_TRANSITION_LIMIT)
    }

    private fun isMatrixValue(value: String): Boolean =
        value.uppercase() in MATRIX_VALUES

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

    private data class SectionRef(
        val text: String,
        val startRow: Int,
        val startCol: Int,
    )

    companion object {
        private val mapper = jacksonObjectMapper()

        private val MERGE_PLACEHOLDERS = setOf("<", "^", "<^", "^<")
        private const val SECTION_MAX_ROW_GAP = 5
        private const val SECTION_HEADER_CELLS_MAX_COLUMNS = 80
        private const val ROW_IDENTITY_MAX_ROWS = 200
        private const val ROW_IDENTITY_SINGLE_COL_MAX = 1
        private const val ROW_IDENTITY_MERGED_MAX_COLS = 4
        private const val ROW_ANCHOR_LIMIT = 80
        private const val COLUMN_METADATA_WIDE_MIN_COLS = 12
        private const val MATRIX_MIN_BODY_ROWS = 2
        private const val MATRIX_MIN_COLUMN_VALUES = 2
        private const val MATRIX_MIN_VALUE_COLUMNS = 3
        private const val MATRIX_ROW_LIMIT = 80
        private const val MATRIX_TRANSITION_LIMIT = 80
        private val MATRIX_VALUES = setOf("O", "X", "Y", "N", "YES", "NO", "TRUE", "FALSE", "○", "×", "✓")

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
