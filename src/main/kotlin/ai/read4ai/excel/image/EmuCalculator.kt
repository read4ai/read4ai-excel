package ai.read4ai.excel.image

import org.apache.poi.hssf.usermodel.HSSFSheet
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.util.SheetUtil
import org.apache.poi.util.Units
import org.apache.poi.xssf.usermodel.XSSFSheet
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

internal object EmuCalculator {

    fun emuToColumn(sheet: Sheet, xEmu: Long): Int {
        var acc = 0L
        var c = 0
        val maxCols = 16384
        while (c < maxCols) {
            val wPx = columnWidthInPixels(sheet, c)
            val wEmu = Units.pixelToEMU(wPx.roundToInt().coerceAtLeast(1)).toLong()
            if (xEmu < acc + wEmu) return c
            acc += wEmu
            c++
        }
        return maxCols - 1
    }

    fun emuToRow(sheet: Sheet, yEmu: Long): Int {
        var acc = 0L
        var r = 0
        val maxRows = 1_048_576
        while (r < maxRows) {
            val hPt = sheet.getRow(r)?.heightInPoints ?: sheet.defaultRowHeightInPoints
            val hEmu = Units.toEMU(hPt.toDouble()).toLong()
            if (yEmu < acc + hEmu) return r
            acc += hEmu
            r++
        }
        return maxRows - 1
    }

    fun columnStartEmu(sheet: Sheet, column: Int): Long {
        if (column <= 0) return 0L
        var acc = 0L
        var c = 0
        while (c < column) {
            val wPx = columnWidthInPixels(sheet, c)
            val wEmu = Units.pixelToEMU(wPx.roundToInt().coerceAtLeast(1)).toLong()
            acc += wEmu
            c++
        }
        return acc
    }

    fun rowStartEmu(sheet: Sheet, row: Int): Long {
        if (row <= 0) return 0L
        var acc = 0L
        var r = 0
        while (r < row) {
            val hPt = sheet.getRow(r)?.heightInPoints ?: sheet.defaultRowHeightInPoints
            val hEmu = Units.toEMU(hPt.toDouble()).toLong()
            acc += hEmu
            r++
        }
        return acc
    }

    fun columnWidthInPixels(sheet: Sheet, column: Int): Double {
        val px = when (sheet) {
            is XSSFSheet -> runCatching { sheet.getColumnWidthInPixels(column).toDouble() }.getOrDefault(0.0)
            is HSSFSheet -> runCatching { sheet.getColumnWidthInPixels(column).toDouble() }.getOrDefault(0.0)
            else -> {
                val widthUnits =
                    runCatching { sheet.getColumnWidth(column) }.getOrDefault(sheet.defaultColumnWidth * 256)
                (widthUnits / 256.0) * SheetUtil.DEFAULT_CHAR_WIDTH
            }
        }
        return px.coerceAtLeast(1.0)
    }

    fun upperBound(arr: LongArray, target: Long): Int {
        var l = 0
        var r = arr.size
        while (l < r) {
            val m = (l + r) ushr 1
            if (arr[m] <= target) l = m + 1 else r = m
        }
        return l
    }

    fun nearestVisibleIndex(target: Int, visibleIndices: List<Int>): Int? {
        if (visibleIndices.isEmpty()) return null
        val idx = visibleIndices.binarySearch(target)
        if (idx >= 0) return idx
        val insertionPoint = -idx - 1
        val lower = insertionPoint - 1
        val upper = insertionPoint
        if (lower < 0 && upper >= visibleIndices.size) return null
        return when {
            lower < 0 -> upper
            upper >= visibleIndices.size -> lower
            else -> {
                val lowerDiff = (target - visibleIndices[lower]).absoluteValue
                val upperDiff = (visibleIndices[upper] - target).absoluteValue
                if (lowerDiff <= upperDiff) lower else upper
            }
        }
    }
}
