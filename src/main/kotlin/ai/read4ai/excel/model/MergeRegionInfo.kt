package ai.read4ai.excel.model

/**
 * A summary of a merged cell region within a sheet.
 *
 * @property cell the top-left cell reference (e.g., "A1")
 * @property rowSpan number of rows spanned (1 = no vertical merge)
 * @property colSpan number of columns spanned (1 = no horizontal merge)
 */
data class MergeRegionInfo(
    val cell: String,
    val rowSpan: Int,
    val colSpan: Int,
)
