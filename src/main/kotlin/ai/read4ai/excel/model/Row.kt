package ai.read4ai.excel.model

/**
 * A single row within a [Element.Table].
 *
 * @property rowIndex zero-based row index within the table
 * @property cells ordered list of cells in this row
 */
data class Row(
    val rowIndex: Int,
    val cells: List<Cell>,
)
