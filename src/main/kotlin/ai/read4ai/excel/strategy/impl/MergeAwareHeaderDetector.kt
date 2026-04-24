package ai.read4ai.excel.strategy.impl

import ai.read4ai.excel.strategy.HeaderDetector
import ai.read4ai.excel.strategy.HeaderInfo
import ai.read4ai.excel.strategy.MergeRegion
import ai.read4ai.excel.strategy.Segment
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

        // Find header start row, skipping full-width intro/banner rows when a richer header follows.
        val firstNonEmptyIdx = findHeaderStartRow(cells, segment.grid.mergeRegions)
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

        val columnPaths = if (headerRows.size > 1) {
            HierarchyAwareHeaderDetector().buildColumnPaths(
                cells = cells,
                mergeRegions = mergeRegions,
                headerStartRow = firstNonEmptyIdx,
                headerRowCount = headerRows.size,
                colCount = colCount,
            )
        } else {
            emptyMap()
        }

        return HeaderInfo(
            headerStartRow = firstNonEmptyIdx,
            headerRowCount = headerRows.size,
            headerRows = headerRows,
            columnPaths = columnPaths,
        )
    }

    internal fun findHeaderStartRow(
        cells: List<List<String>>,
        mergeRegions: List<MergeRegion>,
    ): Int {
        var currentIdx = cells.indexOfFirst { row -> row.any(::isMeaningfulCellValue) }
        if (currentIdx < 0) return -1

        val colCount = cells.maxOfOrNull { it.size } ?: 0
        while (currentIdx >= 0 && isLikelyIntroBannerRow(cells, mergeRegions, currentIdx, colCount)) {
            val nextIdx = nextNonEmptyRowIndex(cells, currentIdx + 1)
            if (nextIdx < 0 || !isRicherHeaderCandidate(cells[nextIdx], cells[currentIdx])) {
                break
            }
            currentIdx = nextIdx
        }

        return currentIdx
    }

    private fun nextNonEmptyRowIndex(cells: List<List<String>>, startIdx: Int): Int {
        for (idx in startIdx until cells.size) {
            if (cells[idx].any(::isMeaningfulCellValue)) return idx
        }
        return -1
    }

    private fun isLikelyIntroBannerRow(
        cells: List<List<String>>,
        mergeRegions: List<MergeRegion>,
        rowIdx: Int,
        colCount: Int,
    ): Boolean {
        val row = cells[rowIdx]
        val meaningfulValues = row.filter(::isMeaningfulCellValue)
        if (meaningfulValues.distinct().size != 1 || colCount < 4) return false

        val fullWidthSpan = mergeRegions.any { region ->
            region.firstRow == rowIdx &&
                region.lastRow == rowIdx &&
                region.firstCol == 0 &&
                (region.lastCol - region.firstCol + 1) >= maxOf(4, colCount / 2)
        }
        if (fullWidthSpan) return true

        val text = meaningfulValues.firstOrNull().orEmpty()
        return text.length >= 40 || '\n' in text
    }

    private fun isRicherHeaderCandidate(
        candidateRow: List<String>,
        previousRow: List<String>,
    ): Boolean {
        val candidateNonBlank = candidateRow.count(::isMeaningfulCellValue)
        val previousNonBlank = previousRow.count(::isMeaningfulCellValue)
        return candidateNonBlank >= maxOf(3, previousNonBlank + 2)
    }

    private fun isMeaningfulCellValue(value: String): Boolean =
        value.isNotBlank() && value !in MERGE_CONTINUATION_MARKERS

    private fun isVerticalMerge(region: MergeRegion): Boolean =
        region.firstRow != region.lastRow

    companion object {
        private val MERGE_CONTINUATION_MARKERS = setOf("<", "^", "^<")
    }

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
