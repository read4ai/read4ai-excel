package ai.read4ai.excel.strategy.impl

import ai.read4ai.excel.strategy.HeaderDetector
import ai.read4ai.excel.strategy.HeaderInfo
import ai.read4ai.excel.strategy.Segment

/**
 * Default [HeaderDetector] that treats the first non-empty row as the single header row.
 *
 * This reproduces the existing ExcelParser behavior (headerRowCount=1).
 */
class SingleRowHeaderDetector : HeaderDetector {

    override fun detectHeaders(segment: Segment): HeaderInfo {
        val cells = segment.grid.cells
        if (cells.isEmpty()) {
            return HeaderInfo(headerRowCount = 0, headerRows = emptyList())
        }

        val firstNonEmptyIdx = cells.indexOfFirst { row -> row.any { it.isNotBlank() } }
        return if (firstNonEmptyIdx >= 0) {
            HeaderInfo(
                headerStartRow = firstNonEmptyIdx,
                headerRowCount = 1,
                headerRows = listOf(cells[firstNonEmptyIdx]),
            )
        } else {
            HeaderInfo(headerRowCount = 0, headerRows = emptyList())
        }
    }
}
