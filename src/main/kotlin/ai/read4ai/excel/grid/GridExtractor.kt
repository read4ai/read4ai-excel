package ai.read4ai.excel.grid

import ai.read4ai.excel.cell.CellStringConverter
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.FormulaEvaluator
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFRow

internal object GridExtractor {

    /**
     * Convert a sheet to a merged grid representation.
     * Merged region top-left cells keep original values; other cells
     * get merge direction markers ('^' for vertical, '<' for horizontal).
     */
    fun toMergedGrid(
        sheet: Sheet,
        evaluator: FormulaEvaluator,
        dataFormatter: DataFormatter,
        additionalRows: Set<Int> = emptySet(),
        additionalColumns: Set<Int> = emptySet(),
    ): GridExtractionResult {
        val emptyResult = GridExtractionResult(
            grid = emptyList(),
            mergedRegions = sheet.mergedRegions,
            visibleRowIndices = emptyList(),
            visibleColumns = emptyList(),
            sheetRowToGridIndex = emptyMap(),
            sheetColToGridIndex = emptyMap(),
        )

        val extraRows = additionalRows.filter { it >= 0 }.toSet()
        val extraCols = additionalColumns.filter { it >= 0 }.toSet()

        val sheetHasRows = sheet.physicalNumberOfRows > 0
        val sheetLastRow = if (sheetHasRows) sheet.lastRowNum else -1
        val rowUpperBound = maxOf(sheetLastRow, extraRows.maxOrNull() ?: -1)
        if (rowUpperBound < 0) return emptyResult

        val visibleRowSetMutable = mutableSetOf<Int>()
        for (r in 0..rowUpperBound) {
            val row = sheet.getRow(r)
            when {
                row != null -> if (!row.isHiddenRow()) visibleRowSetMutable.add(r)
                r <= sheetLastRow -> visibleRowSetMutable.add(r)
                extraRows.contains(r) -> visibleRowSetMutable.add(r)
            }
        }
        visibleRowSetMutable.addAll(extraRows.filter { it in 0..rowUpperBound })

        val visibleRowIndices = visibleRowSetMutable.toList().sorted()
        if (visibleRowIndices.isEmpty()) return emptyResult
        val visibleRowSet = visibleRowSetMutable

        val mergedCellValues = mutableMapOf<Pair<Int, Int>, String>()
        var maxContentCol = extraCols.maxOrNull() ?: -1

        sheet.mergedRegions.forEach { region ->
            val firstRow = sheet.getRow(region.firstRow)
            val firstCell = firstRow?.getCell(region.firstColumn)
            val cellValue = CellStringConverter.convertCellToString(firstCell, evaluator, dataFormatter)
            val isVerticalMerge = region.firstRow != region.lastRow
            val isHorizontalMerge = region.firstColumn != region.lastColumn

            (region.firstRow..region.lastRow).forEach { r ->
                if (r in visibleRowSet) {
                    (region.firstColumn..region.lastColumn).forEach { c ->
                        val marker = buildString {
                            if (isVerticalMerge && r != region.firstRow) append("^")
                            if (isHorizontalMerge && c != region.firstColumn) append("<")
                        }
                        val mergedValue = if (marker.isNotEmpty()) marker else cellValue
                        mergedCellValues[r to c] = mergedValue
                    }
                }
            }

            val hasVisibleRow = (region.firstRow..region.lastRow).any { it in visibleRowSet }
            if (cellValue.isNotBlank() && hasVisibleRow) {
                if (region.lastColumn > maxContentCol) maxContentCol = region.lastColumn
            }
        }

        val rowValues: Array<MutableMap<Int, String>> = Array(rowUpperBound + 1) { mutableMapOf() }
        for (r in visibleRowIndices) {
            val row = sheet.getRow(r) ?: continue
            val it = row.cellIterator()
            while (it.hasNext()) {
                val cell = it.next()
                val c = cell.columnIndex
                val s = CellStringConverter.convertCellToString(cell, evaluator, dataFormatter)
                if (s.isNotBlank()) {
                    rowValues[r][c] = s
                    if (c > maxContentCol) maxContentCol = c
                }
            }
        }

        if (maxContentCol < 0) {
            return emptyResult
        }

        val colLimit = maxContentCol + 1
        val visibleColumns = (0 until colLimit).filter { col ->
            !sheet.isColumnHidden(col) || extraCols.contains(col)
        }
        if (visibleColumns.isEmpty()) return emptyResult

        val grid = visibleRowIndices.map { rowIndex ->
            val rowMap = rowValues.getOrNull(rowIndex) ?: mutableMapOf()
            visibleColumns.map { colIndex ->
                mergedCellValues[rowIndex to colIndex]
                    ?: rowMap[colIndex]
                    ?: ""
            }
        }

        val sheetRowToGridIndex = HashMap<Int, Int>(visibleRowIndices.size).apply {
            visibleRowIndices.forEachIndexed { gridIdx, sheetRow -> this[sheetRow] = gridIdx }
        }
        val sheetColToGridIndex = HashMap<Int, Int>(visibleColumns.size).apply {
            visibleColumns.forEachIndexed { gridIdx, sheetCol -> this[sheetCol] = gridIdx }
        }
        val mappedMergedRegions = buildList {
            sheet.mergedRegions.forEach { region ->
                val visibleRowsInRegion = visibleRowIndices.filter { it in region.firstRow..region.lastRow }
                if (visibleRowsInRegion.isNotEmpty()) {
                    val firstVisibleSheetRow = visibleRowsInRegion.first()
                    val lastVisibleSheetRow = visibleRowsInRegion.last()
                    val mappedFirst = sheetRowToGridIndex[firstVisibleSheetRow] ?: return@forEach
                    val mappedLast = sheetRowToGridIndex[lastVisibleSheetRow] ?: return@forEach
                    add(CellRangeAddress(mappedFirst, mappedLast, region.firstColumn, region.lastColumn))
                }
            }
        }

        return GridExtractionResult(
            grid = grid,
            mergedRegions = mappedMergedRegions,
            visibleRowIndices = visibleRowIndices,
            visibleColumns = visibleColumns,
            sheetRowToGridIndex = sheetRowToGridIndex,
            sheetColToGridIndex = sheetColToGridIndex,
        )
    }

    fun normalizeMergedCell(sheet: Sheet, addr: org.apache.poi.ss.util.CellAddress): org.apache.poi.ss.util.CellAddress {
        val num = sheet.numMergedRegions
        for (i in 0 until num) {
            val region = sheet.getMergedRegion(i)
            if (region.isInRange(addr.row, addr.column)) {
                return org.apache.poi.ss.util.CellAddress(region.firstRow, region.firstColumn)
            }
        }
        return addr
    }

    fun findVerticallyMergedRows(
        startRowIndex: Int,
        mergedRegions: List<CellRangeAddress>,
        maxRows: Int,
    ): Set<Int> {
        val mergedRows = mutableSetOf<Int>()
        mergedRegions.forEach { region ->
            if (region.firstRow <= startRowIndex && region.lastRow >= startRowIndex) {
                if (region.firstRow != region.lastRow) {
                    (region.firstRow..region.lastRow).forEach { rowIndex ->
                        if (rowIndex < maxRows) {
                            mergedRows.add(rowIndex)
                        }
                    }
                }
            }
        }
        return mergedRows
    }

    /**
     * Determine if a row is hidden: height is zero (XSSF) or internal hidden flag is set.
     */
    private fun Row.isHiddenRow(): Boolean =
        this.zeroHeight || (this is XSSFRow && this.ctRow.hidden)
}
