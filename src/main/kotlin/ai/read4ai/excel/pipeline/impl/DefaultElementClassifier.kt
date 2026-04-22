package ai.read4ai.excel.pipeline.impl

import ai.read4ai.excel.grid.SpatialSegmenter
import ai.read4ai.excel.model.Cell
import ai.read4ai.excel.model.Element
import ai.read4ai.excel.model.Row
import ai.read4ai.excel.pipeline.ElementClassifier
import ai.read4ai.excel.pipeline.HeaderInfo
import ai.read4ai.excel.pipeline.Segment
import ai.read4ai.excel.grid.TitleDetector

/**
 * Default [ElementClassifier] that wraps the existing classification logic.
 *
 * Classifies segments as Heading, Text, or Table based on:
 * - Title detection (bullet prefixes, year patterns)
 * - Isolated single-cell content detection
 * - Fallback to table for multi-cell content
 *
 * This reproduces the existing ExcelParser behavior.
 */
class DefaultElementClassifier : ElementClassifier {

    override fun classify(segment: Segment, headerInfo: HeaderInfo): Element {
        val rows = segment.grid.cells
        if (rows.isEmpty()) {
            return Element.Text(text = "", startRow = segment.startRow, startCol = segment.startCol)
        }

        // Check for title
        val detection = TitleDetector.detectTitle(rows)
        if (detection != null && detection.remainingRows.isEmpty()) {
            return Element.Heading(text = detection.titleText, startRow = segment.startRow, startCol = segment.startCol)
        }

        val contentRows = detection?.remainingRows ?: rows
        val trimmed = SpatialSegmenter.trimBlankEdgeRows(contentRows)

        if (trimmed.isEmpty()) {
            return if (detection != null) {
                Element.Heading(text = detection.titleText, startRow = segment.startRow, startCol = segment.startCol)
            } else {
                Element.Text(text = "", startRow = segment.startRow, startCol = segment.startCol)
            }
        }

        val trimOffset = contentRows.indexOf(trimmed.firstOrNull() ?: contentRows.first()).coerceAtLeast(0)
        val titleOffset = if (detection != null) detection.remainingRows.let {
            rows.indexOf(it.firstOrNull() ?: rows.first()).coerceAtLeast(0)
        } else 0
        val absoluteRowBase = segment.startRow + titleOffset + trimOffset

        // Check for isolated single cell
        if (SpatialSegmenter.isIsolatedSingleCellContent(trimmed)) {
            val text = SpatialSegmenter.extractSingleCellText(trimmed) ?: ""
            return Element.Text(
                text = mergeDetectedTitle(detection?.titleText, text),
                startRow = if (detection != null) segment.startRow else absoluteRowBase,
                startCol = segment.startCol,
            )
        }

        // Single-column text block (notices, disclaimers): join lines as Text
        if (SpatialSegmenter.isSingleColumnTextBlock(trimmed)) {
            val text = SpatialSegmenter.extractSingleColumnText(trimmed)
            return Element.Text(
                text = mergeDetectedTitle(detection?.titleText, text),
                startRow = if (detection != null) segment.startRow else absoluteRowBase,
                startCol = segment.startCol,
            )
        }

        // Build a table — rowIndex is relative (0-based within the table),
        // startRow stores the absolute offset in the sheet grid.
        val headerStartInTrimmed = (headerInfo.headerStartRow - titleOffset - trimOffset)
            .coerceAtLeast(0)
            .coerceAtMost(trimmed.size)
        val tableSourceRows = if (headerStartInTrimmed > 0) {
            trimmed.drop(headerStartInTrimmed)
        } else {
            trimmed
        }
        val tableStartRow = absoluteRowBase + headerStartInTrimmed

        val tableRows = tableSourceRows.mapIndexed { rowIdx, row ->
            Row(
                rowIndex = rowIdx,
                cells = row.map { cellValue -> Cell(value = cellValue) },
            )
        }

        return Element.Table(
            rows = tableRows,
            headerRowCount = headerInfo.headerRowCount,
            startRow = tableStartRow,
            startCol = segment.startCol,
            columnPaths = headerInfo.columnPaths,
            rowPaths = headerInfo.rowPaths,
        )
    }

    private fun mergeDetectedTitle(title: String?, body: String): String {
        val parts = buildList {
            title?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            body.trim().takeIf { it.isNotEmpty() }?.let(::add)
        }
        return parts.joinToString("\n")
    }
}
