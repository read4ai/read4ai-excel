package ai.read4ai.excel.grid

import org.apache.poi.ss.util.CellRangeAddress

internal data class GridExtractionResult(
    val grid: List<List<String>>,
    val mergedRegions: List<CellRangeAddress>,
    val visibleRowIndices: List<Int>,
    val visibleColumns: List<Int>,
    val sheetRowToGridIndex: Map<Int, Int>,
    val sheetColToGridIndex: Map<Int, Int>,
)
