package ai.read4ai.excel.strategy

/**
 * A 2D grid extracted from a sheet, with merge region metadata.
 *
 * @property cells row-major 2D array of cell string values
 * @property mergeRegions list of merged cell regions in grid coordinates
 * @property rowCount number of rows in the grid
 * @property colCount number of columns in the grid
 */
data class Grid(
    val cells: List<List<String>>,
    val mergeRegions: List<MergeRegion>,
    val rowCount: Int,
    val colCount: Int,
)
