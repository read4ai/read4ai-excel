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
            return Element.Text(text = "")
        }

        // Check for title
        val detection = TitleDetector.detectTitle(rows)
        if (detection != null && detection.remainingRows.isEmpty()) {
            return Element.Heading(text = detection.titleText)
        }

        val contentRows = detection?.remainingRows ?: rows
        val trimmed = SpatialSegmenter.trimBlankEdgeRows(contentRows)

        if (trimmed.isEmpty()) {
            return if (detection != null) {
                Element.Heading(text = detection.titleText)
            } else {
                Element.Text(text = "")
            }
        }

        // Check for isolated single cell
        if (SpatialSegmenter.isIsolatedSingleCellContent(trimmed)) {
            val text = SpatialSegmenter.extractSingleCellText(trimmed) ?: ""
            return Element.Text(text = text.trim())
        }

        // Build a table — rowIndex is relative (0-based within the table),
        // startRow stores the absolute offset in the sheet grid.
        val trimOffset = contentRows.indexOf(trimmed.firstOrNull() ?: contentRows.first()).coerceAtLeast(0)
        val titleOffset = if (detection != null) detection.remainingRows.let {
            rows.indexOf(it.firstOrNull() ?: rows.first()).coerceAtLeast(0)
        } else 0
        val absoluteRowBase = segment.startRow + titleOffset + trimOffset

        val tableRows = trimmed.mapIndexed { rowIdx, row ->
            Row(
                rowIndex = rowIdx,
                cells = row.map { cellValue -> Cell(value = cellValue) },
            )
        }

        return Element.Table(
            rows = tableRows,
            headerRowCount = headerInfo.headerRowCount,
            startRow = absoluteRowBase,
            startCol = segment.startCol,
            columnPaths = headerInfo.columnPaths,
            rowPaths = headerInfo.rowPaths,
        )
    }
}
