package ai.read4ai.excel.strategy.impl

import ai.read4ai.excel.grid.SpatialSegmenter
import ai.read4ai.excel.strategy.Grid
import ai.read4ai.excel.strategy.MergeRegion
import ai.read4ai.excel.strategy.Segment
import ai.read4ai.excel.strategy.Segmenter
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Three-level segmenter derived from the storm-apis analysis.
 *
 * Performs a hierarchical split:
 * - **Level 1**: [SpatialSegmenter.splitIntoRowBands] -- splits by empty rows (gap >= 2)
 * - **Level 2**: [SpatialSegmenter.splitByEmptyColumns] -- splits by empty columns (gap >= 1)
 * - **Level 3**: [SpatialSegmenter.splitStripeWithIndices] -- sub-segments within each stripe
 *
 * This is the full segmentation approach from the original system, providing better
 * handling of sheets with multiple side-by-side tables and mixed content.
 *
 * @param minEmptyRowsForBandSplit minimum consecutive empty rows to split row bands (default: 2)
 * @param minEmptyRowsForStripeSplit minimum consecutive empty rows to split within a stripe (default: 2)
 */
class ThreeLevelSegmenter(
    private val minEmptyRowsForBandSplit: Int = DEFAULT_MIN_EMPTY_ROWS,
    private val minEmptyRowsForStripeSplit: Int = DEFAULT_MIN_EMPTY_ROWS,
) : Segmenter {

    private val log = KotlinLogging.logger {}

    override fun segment(grid: Grid): List<Segment> {
        if (grid.cells.isEmpty()) return emptyList()

        val segments = mutableListOf<Segment>()

        // Level 1: split into row bands
        val rowBands = SpatialSegmenter.splitIntoRowBands(grid.cells, minEmptyRowsForBandSplit)
        log.debug { "${rowBands.size} row band(s) after Level 1 split" }

        // Track the global row offset as we walk through bands.
        // We need to find the actual offset of each band in the original grid.
        var bandSearchStart = 0
        var previousBandEndRow = -1

        rowBands.forEach { band ->
            // Find where this band starts in the original grid
            val bandStartRow = findBandStartRow(grid.cells, band.rows, bandSearchStart)

            // Calculate the inter-band gap (empty rows between this band and the previous one)
            val interBandGap = if (previousBandEndRow >= 0) {
                (bandStartRow - previousBandEndRow - 1).coerceAtLeast(0)
            } else {
                bandStartRow // gap from top of grid
            }

            // Level 2: split each band by empty columns
            val columnStripes = SpatialSegmenter.splitByEmptyColumns(band.rows)
            log.debug { "Band at row $bandStartRow: ${columnStripes.size} column stripe(s)" }

            if (columnStripes.isEmpty()) {
                previousBandEndRow = bandStartRow + band.rows.size - 1
                bandSearchStart = previousBandEndRow + 1
                return@forEach
            }

            // Track column offset for each stripe
            val stripeColOffsets = findStripeColumnOffsets(band.rows, columnStripes)
            var isFirstSegmentInBand = true

            columnStripes.forEachIndexed { stripeIdx, stripeGrid ->
                val colOffset = stripeColOffsets.getOrElse(stripeIdx) { 0 }

                // Level 3: split each stripe into sub-segments
                val stripeSegments = SpatialSegmenter.splitStripeWithIndices(
                    stripeGrid,
                    minEmptyRowsForStripeSplit,
                )

                stripeSegments.forEach { stripeSegment ->
                    val segStartRow = bandStartRow + stripeSegment.startRowIndex
                    val segMergeRegions = filterMergeRegions(
                        grid.mergeRegions,
                        segStartRow,
                        stripeSegment.rows.size,
                        colOffset,
                        stripeSegment.rows.maxOfOrNull { it.size } ?: 0,
                    )

                    // For the first segment of a new band, use the inter-band gap
                    // For subsequent segments, use the intra-stripe gap
                    val effectiveGap = if (isFirstSegmentInBand && stripeSegment.gapRowsFromPrevious == 0) {
                        interBandGap
                    } else {
                        stripeSegment.gapRowsFromPrevious
                    }
                    isFirstSegmentInBand = false

                    segments.add(
                        Segment(
                            grid = Grid(
                                cells = stripeSegment.rows,
                                mergeRegions = segMergeRegions,
                                rowCount = stripeSegment.rows.size,
                                colCount = stripeSegment.rows.maxOfOrNull { it.size } ?: 0,
                            ),
                            startRow = segStartRow,
                            startCol = colOffset,
                            gapFromPrevious = effectiveGap,
                        )
                    )
                }
            }

            // Advance search start past this band
            previousBandEndRow = bandStartRow + band.rows.size - 1
            bandSearchStart = previousBandEndRow + 1
        }

        // Fallback
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

        log.debug { "Produced ${segments.size} segment(s) total" }
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
            if (gridCells[i] == firstBandRow) return i
        }
        return searchStart
    }

    /**
     * Determine the column offset for each stripe within a band.
     */
    private fun findStripeColumnOffsets(
        bandRows: List<List<String>>,
        stripes: List<List<List<String>>>,
    ): List<Int> {
        if (bandRows.isEmpty() || stripes.isEmpty()) return emptyList()

        val maxCols = bandRows.maxOfOrNull { it.size } ?: 0
        val offsets = mutableListOf<Int>()

        // Find non-empty column ranges to determine stripe boundaries
        val nonEmptyCols = mutableListOf<Int>()
        for (c in 0 until maxCols) {
            if (bandRows.any { row -> c < row.size && row[c].isNotBlank() }) {
                nonEmptyCols.add(c)
            }
        }

        if (nonEmptyCols.isEmpty()) return stripes.map { 0 }

        // Group contiguous non-empty columns
        val groups = mutableListOf<Int>() // start column of each group
        if (nonEmptyCols.isNotEmpty()) {
            groups.add(nonEmptyCols[0])
            for (i in 1 until nonEmptyCols.size) {
                if (nonEmptyCols[i] - nonEmptyCols[i - 1] > 1) {
                    groups.add(nonEmptyCols[i])
                }
            }
        }

        // Map each stripe to its column group start
        for (i in stripes.indices) {
            offsets.add(groups.getOrElse(i) { 0 })
        }

        return offsets
    }

    private fun filterMergeRegions(
        allRegions: List<MergeRegion>,
        startRow: Int,
        rowCount: Int,
        startCol: Int,
        colCount: Int,
    ): List<MergeRegion> {
        val endRow = startRow + rowCount - 1
        val endCol = startCol + colCount - 1
        return allRegions.filter { region ->
            region.firstRow <= endRow && region.lastRow >= startRow &&
                region.firstCol <= endCol && region.lastCol >= startCol
        }.map { region ->
            MergeRegion(
                firstRow = (region.firstRow - startRow).coerceAtLeast(0),
                lastRow = (region.lastRow - startRow).coerceAtMost(rowCount - 1),
                firstCol = (region.firstCol - startCol).coerceAtLeast(0),
                lastCol = (region.lastCol - startCol).coerceAtMost(colCount - 1),
            )
        }
    }

    companion object {
        private const val DEFAULT_MIN_EMPTY_ROWS = 2
    }
}
