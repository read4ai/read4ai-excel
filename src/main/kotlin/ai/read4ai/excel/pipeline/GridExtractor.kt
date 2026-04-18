package ai.read4ai.excel.pipeline

import org.apache.poi.ss.usermodel.Sheet

/**
 * Extracts a [Grid] (2D cell array with merge info) from a POI [Sheet].
 *
 * Implementations must handle merged cells, hidden rows/columns, and formula evaluation.
 */
interface GridExtractor {
    fun extract(sheet: Sheet): Grid
}
