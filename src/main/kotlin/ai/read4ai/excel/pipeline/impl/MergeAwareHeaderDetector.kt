package ai.read4ai.excel.pipeline.impl

import ai.read4ai.excel.pipeline.HeaderDetector
import ai.read4ai.excel.pipeline.HeaderInfo
import ai.read4ai.excel.pipeline.MergeRegion
import ai.read4ai.excel.pipeline.Segment
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Header detector that uses merge regions to detect multi-row headers.
 *
 * Logic derived from the storm-apis `toMarkdownTableWithHeaders()`:
 * - Find the first non-empty row as the header start
 * - If any cell in that row is vertically merged, all merged rows are header rows
 * - Fill empty header cells with "header_N" placeholders
 *
 * This produces more accurate header detection for tables with multi-level
 * headers (common in financial reports and government data).
 */
class MergeAwareHeaderDetector : HeaderDetector {

    private val log = KotlinLogging.logger {}

    override fun detectHeaders(segment: Segment): HeaderInfo {
        val cells = segment.grid.cells
        if (cells.isEmpty()) {
            return HeaderInfo(headerRowCount = 0, headerRows = emptyList())
        }

        // Find first non-empty row
        val firstNonEmptyIdx = cells.indexOfFirst { row -> row.any { it.isNotBlank() } }
        if (firstNonEmptyIdx < 0) {
            return HeaderInfo(headerRowCount = 0, headerRows = emptyList())
        }

        // Find all rows that participate in vertical merges intersecting the first non-empty row
        val headerRowIndices = mutableSetOf(firstNonEmptyIdx)
        val mergeRegions = segment.grid.mergeRegions

        for (region in mergeRegions) {
            if (isVerticalMerge(region) && region.firstRow <= firstNonEmptyIdx && region.lastRow >= firstNonEmptyIdx) {
                for (r in region.firstRow..region.lastRow) {
                    if (r >= 0 && r < cells.size) {
                        headerRowIndices.add(r)
                    }
                }
            }
        }

        val sortedIndices = headerRowIndices.sorted()
        val colCount = cells.maxOfOrNull { it.size } ?: 0

        // Collect header rows and fill empty cells with placeholders
        val headerRows = fillEmptyHeaderCells(
            sortedIndices.map { idx -> cells[idx] },
            colCount,
        )

        log.debug {
            "Detected ${headerRows.size} header row(s) starting at row $firstNonEmptyIdx"
        }

        return HeaderInfo(
            headerRowCount = headerRows.size,
            headerRows = headerRows,
        )
    }

    private fun isVerticalMerge(region: MergeRegion): Boolean =
        region.firstRow != region.lastRow

    /**
     * Fill empty header cells with "header_N" placeholders.
     * Once a column has been filled in any header row, subsequent empty cells
     * in the same column are left as-is (they represent merge continuations).
     */
    private fun fillEmptyHeaderCells(
        headerRows: List<List<String>>,
        colCount: Int,
    ): List<List<String>> {
        if (headerRows.isEmpty()) return emptyList()

        var placeholderCounter = 1
        val columnFilled = mutableSetOf<Int>()
        val result = mutableListOf<List<String>>()

        for (row in headerRows) {
            val filledRow = mutableListOf<String>()
            for (colIdx in 0 until colCount) {
                val cellValue = row.getOrElse(colIdx) { "" }
                when {
                    cellValue.isNotBlank() -> {
                        columnFilled.add(colIdx)
                        filledRow.add(cellValue)
                    }
                    colIdx in columnFilled -> {
                        // Already filled in a previous header row -- leave as-is
                        filledRow.add(cellValue)
                    }
                    else -> {
                        columnFilled.add(colIdx)
                        filledRow.add("header_${placeholderCounter++}")
                    }
                }
            }
            result.add(filledRow)
        }

        return result
    }
}
