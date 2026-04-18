package ai.read4ai.excel.pipeline.impl

import ai.read4ai.excel.ExperimentalRead4ai
import ai.read4ai.excel.pipeline.HeaderDetector
import ai.read4ai.excel.pipeline.HeaderInfo
import ai.read4ai.excel.pipeline.MergeRegion
import ai.read4ai.excel.pipeline.Segment
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Header detector that builds hierarchy paths from merge regions.
 *
 * Extends [MergeAwareHeaderDetector]'s multi-row header detection with
 * structural hierarchy path extraction:
 *
 * - **Column paths** (top headers): For each column, walks down through
 *   header rows collecting the text at each level. Horizontally merged cells
 *   propagate their value to all spanned columns.
 *   Example: A1:C1="대분류", A2="소분류1" -> column 0 path = ["대분류", "소분류1"]
 *
 * - **Row paths** (left headers): For each data row, walks right through
 *   left-side merged columns collecting text. Vertically merged cells
 *   propagate their value to all spanned rows.
 *   Example: A1:A3="기계설비", B1="펌프" -> row 0 path = ["기계설비", "펌프"]
 *
 * The approach is purely structural -- no domain-specific keywords or heuristics.
 */
@ExperimentalRead4ai
class HierarchyAwareHeaderDetector : HeaderDetector {

    private val log = KotlinLogging.logger {}

    override fun detectHeaders(segment: Segment): HeaderInfo {
        val cells = segment.grid.cells
        if (cells.isEmpty()) {
            return HeaderInfo(headerRowCount = 0, headerRows = emptyList())
        }

        // Reuse MergeAwareHeaderDetector's logic for header row detection
        val baseInfo = MergeAwareHeaderDetector().detectHeaders(segment)
        if (baseInfo.headerRowCount == 0) {
            return baseInfo
        }

        val mergeRegions = segment.grid.mergeRegions
        val colCount = cells.maxOfOrNull { it.size } ?: 0

        // Build column paths from top header rows
        val columnPaths = buildColumnPaths(cells, mergeRegions, baseInfo.headerRowCount, colCount)

        // Build row paths from left-side merged headers in the data area
        val rowPaths = buildRowPaths(cells, mergeRegions, baseInfo.headerRowCount, colCount)

        log.debug {
            "${baseInfo.headerRowCount} header row(s), " +
                "${columnPaths.size} column path(s), ${rowPaths.size} row path(s)"
        }

        return baseInfo.copy(
            columnPaths = columnPaths,
            rowPaths = rowPaths,
        )
    }

    /**
     * Build hierarchy paths for each column by walking down through header rows.
     *
     * For each column, collect the effective text at each header row level.
     * Horizontally merged cells propagate their value to all spanned columns.
     */
    internal fun buildColumnPaths(
        cells: List<List<String>>,
        mergeRegions: List<MergeRegion>,
        headerRowCount: Int,
        colCount: Int,
    ): Map<Int, List<String>> {
        if (headerRowCount <= 1) return emptyMap()

        // Build a resolved grid for header rows where each cell knows its effective value
        // (accounting for horizontal merges that propagate values across columns)
        val resolvedHeader = resolveHeaderGrid(cells, mergeRegions, headerRowCount, colCount)

        val paths = mutableMapOf<Int, List<String>>()
        for (col in 0 until colCount) {
            val path = mutableListOf<String>()
            for (row in 0 until headerRowCount) {
                val value = resolvedHeader[row][col]
                if (value.isNotBlank()) {
                    // Only add if different from the previous level (avoid duplicating
                    // vertically merged cells that repeat the same value)
                    if (path.isEmpty() || path.last() != value) {
                        path.add(value)
                    }
                }
            }
            if (path.size >= 2) {
                paths[col] = path
            }
        }
        return paths
    }

    /**
     * Build hierarchy paths for data rows from left-side merged columns.
     *
     * Detects columns on the left that act as row headers (vertically merged cells
     * spanning multiple data rows). For each data row, collects text from these
     * left header columns to form a path.
     */
    internal fun buildRowPaths(
        cells: List<List<String>>,
        mergeRegions: List<MergeRegion>,
        headerRowCount: Int,
        colCount: Int,
    ): Map<Int, List<String>> {
        if (headerRowCount >= cells.size) return emptyMap()

        // Find left-side columns that have vertical merges in the data area
        val dataStartRow = headerRowCount
        val leftMergeColumns = findLeftMergeColumns(mergeRegions, dataStartRow, cells.size, colCount)
        if (leftMergeColumns.isEmpty()) return emptyMap()

        // Build a resolved grid for the data area's left columns
        val resolvedValues = resolveDataLeftColumns(cells, mergeRegions, dataStartRow, leftMergeColumns)

        val paths = mutableMapOf<Int, List<String>>()
        for (dataRowIdx in 0 until (cells.size - dataStartRow)) {
            val path = mutableListOf<String>()
            for (col in leftMergeColumns) {
                val value = resolvedValues[dataRowIdx]?.get(col) ?: ""
                if (value.isNotBlank()) {
                    if (path.isEmpty() || path.last() != value) {
                        path.add(value)
                    }
                }
            }
            if (path.size >= 2) {
                paths[dataRowIdx] = path
            }
        }
        return paths
    }

    /**
     * Resolve the header grid so each cell has its effective value,
     * accounting for horizontal and vertical merges.
     */
    private fun resolveHeaderGrid(
        cells: List<List<String>>,
        mergeRegions: List<MergeRegion>,
        headerRowCount: Int,
        colCount: Int,
    ): List<List<String>> {
        // Start with the raw values
        val grid = (0 until headerRowCount).map { row ->
            (0 until colCount).map { col ->
                cells.getOrNull(row)?.getOrNull(col) ?: ""
            }.toMutableList()
        }

        // Apply merge regions: propagate the top-left cell's value to all cells in the region
        for (region in mergeRegions) {
            val sourceValue = cells.getOrNull(region.firstRow)?.getOrNull(region.firstCol) ?: ""
            if (sourceValue.isBlank()) continue

            for (r in region.firstRow..minOf(region.lastRow, headerRowCount - 1)) {
                for (c in region.firstCol..minOf(region.lastCol, colCount - 1)) {
                    if (r < grid.size && c < grid[r].size) {
                        grid[r][c] = sourceValue
                    }
                }
            }
        }

        return grid
    }

    /**
     * Find left-side columns (starting from column 0) that contain vertical merges
     * in the data area. These are row header columns.
     */
    private fun findLeftMergeColumns(
        mergeRegions: List<MergeRegion>,
        dataStartRow: Int,
        totalRows: Int,
        colCount: Int,
    ): List<Int> {
        // Collect columns with vertical merges in the data area
        val mergedCols = mutableSetOf<Int>()
        for (region in mergeRegions) {
            if (region.firstRow < region.lastRow &&
                region.lastRow >= dataStartRow &&
                region.firstRow < totalRows
            ) {
                for (c in region.firstCol..region.lastCol) {
                    mergedCols.add(c)
                }
            }
        }

        // Only take contiguous columns starting from column 0
        val result = mutableListOf<Int>()
        for (col in 0 until colCount) {
            if (col in mergedCols) {
                result.add(col)
            } else {
                break
            }
        }
        return result
    }

    /**
     * Resolve left column values for each data row, propagating vertically merged values.
     */
    private fun resolveDataLeftColumns(
        cells: List<List<String>>,
        mergeRegions: List<MergeRegion>,
        dataStartRow: Int,
        leftColumns: List<Int>,
    ): Map<Int, Map<Int, String>> {
        val dataRowCount = cells.size - dataStartRow

        // Initialize with raw values
        val resolved = (0 until dataRowCount).associateWith { dataRowIdx ->
            val absRow = dataStartRow + dataRowIdx
            leftColumns.associateWith { col ->
                cells.getOrNull(absRow)?.getOrNull(col) ?: ""
            }.toMutableMap()
        }.toMutableMap()

        // Apply vertical merges in the data area
        for (region in mergeRegions) {
            if (region.firstRow >= region.lastRow) continue
            val sourceValue = cells.getOrNull(region.firstRow)?.getOrNull(region.firstCol) ?: ""
            if (sourceValue.isBlank()) continue

            for (col in region.firstCol..region.lastCol) {
                if (col !in leftColumns) continue
                for (absRow in region.firstRow..region.lastRow) {
                    val dataRowIdx = absRow - dataStartRow
                    if (dataRowIdx >= 0 && dataRowIdx < dataRowCount) {
                        resolved[dataRowIdx]?.set(col, sourceValue)
                    }
                }
            }
        }

        return resolved
    }
}
