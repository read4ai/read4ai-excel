package ai.read4ai.excel.pipeline.impl

import ai.read4ai.excel.grid.SpatialSegmenter
import ai.read4ai.excel.pipeline.Grid
import ai.read4ai.excel.pipeline.MergeRegion
import ai.read4ai.excel.pipeline.Segment
import ai.read4ai.excel.pipeline.Segmenter

/**
 * Default [Segmenter] that wraps the existing [SpatialSegmenter] logic.
 *
 * Performs the same 3-step segmentation as the current ElementClassifier:
 * 1. Split into row bands (by empty row gaps >= 2)
 * 2. Split each band by empty columns
 * 3. Split each stripe into sub-segments
 *
 * This reproduces the existing ExcelParser behavior.
 */
class SimpleSegmenter : Segmenter {

    override fun segment(grid: Grid): List<Segment> {
        if (grid.cells.isEmpty()) return emptyList()

        val segments = mutableListOf<Segment>()

        val rowBands = SpatialSegmenter.splitIntoRowBands(grid.cells)

        // Find actual start row for each band by searching in the original grid
        var bandSearchStart = 0

        rowBands.forEach { band ->
            val bandStartRow = findBandStartRow(grid.cells, band.rows, bandSearchStart)

            val columnStripes = SpatialSegmenter.splitByEmptyColumns(band.rows)
            if (columnStripes.isEmpty()) {
                bandSearchStart = bandStartRow + band.rows.size
                return@forEach
            }

            columnStripes.forEach { stripeGrid ->
                val stripeSegments = SpatialSegmenter.splitStripeWithIndices(stripeGrid)

                stripeSegments.forEach { stripeSegment ->
                    val absoluteRow = bandStartRow + stripeSegment.startRowIndex
                    val mergeRegions = filterMergeRegions(
                        grid.mergeRegions,
                        absoluteRow,
                        stripeSegment.rows.size,
                    )
                    segments.add(
                        Segment(
                            grid = Grid(
                                cells = stripeSegment.rows,
                                mergeRegions = mergeRegions,
                                rowCount = stripeSegment.rows.size,
                                colCount = stripeSegment.rows.maxOfOrNull { it.size } ?: 0,
                            ),
                            startRow = absoluteRow,
                            startCol = 0,
                            gapFromPrevious = stripeSegment.gapRowsFromPrevious,
                        )
                    )
                }
            }

            bandSearchStart = bandStartRow + band.rows.size
        }

        // Fallback: if no segments found but grid has data, create a single segment
        if (segments.isEmpty() && grid.cells.isNotEmpty()) {
            segments.add(
                Segment(
                    grid = grid,
                    startRow = 0,
                    startCol = 0,
                    gapFromPrevious = 0,
                )
            )
        }

        return segments
    }

    /**
     * Find where a band's rows start in the original grid.
     */
    private fun findBandStartRow(
        gridCells: List<List<String>>,
        bandRows: List<List<String>>,
        searchStart: Int,
    ): Int {
        if (bandRows.isEmpty()) return searchStart
        val firstBandRow = bandRows[0]
        for (i in searchStart until gridCells.size) {
            if (gridCells[i] === firstBandRow || gridCells[i] == firstBandRow) return i
        }
        return searchStart
    }

    private fun filterMergeRegions(
        allRegions: List<MergeRegion>,
        startRow: Int,
        rowCount: Int,
    ): List<MergeRegion> {
        val endRow = startRow + rowCount - 1
        return allRegions.filter { region ->
            region.firstRow <= endRow && region.lastRow >= startRow
        }.map { region ->
            MergeRegion(
                firstRow = (region.firstRow - startRow).coerceAtLeast(0),
                lastRow = (region.lastRow - startRow).coerceAtMost(rowCount - 1),
                firstCol = region.firstCol,
                lastCol = region.lastCol,
            )
        }
    }
}
