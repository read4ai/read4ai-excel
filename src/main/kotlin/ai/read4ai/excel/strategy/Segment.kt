package ai.read4ai.excel.strategy

/**
 * A rectangular sub-region of the grid identified by the segmenter.
 *
 * @property grid the cell data within this segment
 * @property startRow the row offset of this segment within the parent grid
 * @property startCol the column offset of this segment within the parent grid
 * @property gapFromPrevious number of empty rows separating this segment from the previous one
 */
data class Segment(
    val grid: Grid,
    val startRow: Int,
    val startCol: Int,
    val gapFromPrevious: Int,
)
