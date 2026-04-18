package ai.read4ai.excel.model

/** The root output model representing a parsed Excel workbook. */
data class ExcelDocument(
    val fileName: String?,
    val numberOfSheets: Int,
    val sheets: List<Sheet>,
    val language: String = "EN",
)
