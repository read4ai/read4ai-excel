package ai.read4ai.excel.pipeline

/**
 * Header information detected for a segment.
 *
 * @property headerRowCount number of rows that form the header
 * @property headerRows the textual content of each header row
 * @property columnPaths column index to hierarchy path for top headers
 * @property rowPaths row index to hierarchy path for left-side merged headers
 */
data class HeaderInfo(
    val headerRowCount: Int,
    val headerRows: List<List<String>>,
    val columnPaths: Map<Int, List<String>> = emptyMap(),
    val rowPaths: Map<Int, List<String>> = emptyMap(),
)
