package ai.read4ai.excel.model

/**
 * A single sheet within an [ExcelDocument].
 *
 * Contains the sheet metadata and a list of semantically classified [Element] objects
 * extracted from the sheet's content.
 *
 * @property sheetIndex zero-based index of this sheet in the workbook
 * @property sheetName the sheet tab name as displayed in Excel
 * @property elements ordered list of classified elements (tables, headings, text, images, notes)
 * @property mergeRegions top-level summary of merged cell regions in this sheet
 */
data class Sheet(
    val sheetIndex: Int,
    val sheetName: String,
    val elements: List<Element>,
    val mergeRegions: List<MergeRegionInfo> = emptyList(),
)

