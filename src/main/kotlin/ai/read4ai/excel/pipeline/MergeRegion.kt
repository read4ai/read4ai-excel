package ai.read4ai.excel.pipeline

/**
 * A rectangular region of merged cells, in grid coordinates.
 */
data class MergeRegion(
    val firstRow: Int,
    val lastRow: Int,
    val firstCol: Int,
    val lastCol: Int,
)
