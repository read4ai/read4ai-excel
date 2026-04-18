package ai.read4ai.excel.model

/**
 * A single cell within a [Row].
 *
 * @property value the cell's text content (formatted and sanitized)
 * @property mergedRight number of additional columns this cell spans to the right (0 = no merge)
 * @property mergedDown number of additional rows this cell spans downward (0 = no merge)
 */
data class Cell(
    val value: String,
    val mergedRight: Int = 0,
    val mergedDown: Int = 0,
)
