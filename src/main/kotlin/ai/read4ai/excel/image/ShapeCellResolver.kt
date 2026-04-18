package ai.read4ai.excel.image

import org.apache.poi.hssf.usermodel.HSSFClientAnchor
import org.apache.poi.ss.usermodel.ClientAnchor
import org.apache.poi.ss.usermodel.Picture
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.util.CellAddress
import org.apache.poi.xssf.usermodel.XSSFClientAnchor
import org.apache.poi.xssf.usermodel.XSSFDrawing
import org.apache.poi.xssf.usermodel.XSSFPicture

internal object ShapeCellResolver {

    fun imageCenterCell(
        sheet: Sheet,
        picture: Picture,
        colCumSheet: LongArray,
        rowCumSheet: LongArray,
    ): CellAddress? {
        val fallbackProvider = {
            (sheet.drawingPatriarch as? XSSFDrawing)?.let { fallbackCellFromXml(it, sheet, picture) }
        }
        val anchor = picture.clientAnchor ?: return fallbackProvider()
        return centerCellFromAnchor(anchor, colCumSheet, rowCumSheet) ?: fallbackProvider()
    }

    fun shapeClientAnchor(shape: Any): ClientAnchor? {
        return when (shape) {
            is Picture -> shape.clientAnchor
            else -> null
        }
    }

    fun shapeCenterCell(
        shape: Any,
        colCumSheet: LongArray,
        rowCumSheet: LongArray,
    ): CellAddress? {
        val anchor = shapeClientAnchor(shape) ?: return null
        return centerCellFromAnchor(anchor, colCumSheet, rowCumSheet)
    }

    fun centerCellFromAnchor(
        anchor: ClientAnchor,
        colCumSheet: LongArray,
        rowCumSheet: LongArray,
    ): CellAddress? {
        return when (anchor) {
            is XSSFClientAnchor -> {
                val c1 = anchor.col1
                val r1 = anchor.row1
                val c2 = anchor.col2.toInt()
                val r2 = anchor.row2.toInt()
                if (c1 < 0 || r1 < 0 || c2 < 0 || r2 < 0) return null

                val leftX = colCumSheet.getOrNull(c1.toInt()) ?: return null
                val rightX = colCumSheet.getOrNull(c2 + 1) ?: return null
                val topY = rowCumSheet.getOrNull(r1.toInt()) ?: return null
                val bottomY = rowCumSheet.getOrNull(r2 + 1) ?: return null

                val centerX = (leftX + rightX) / 2
                val centerY = (topY + bottomY) / 2

                var col = EmuCalculator.upperBound(colCumSheet, centerX) - 1
                var row = EmuCalculator.upperBound(rowCumSheet, centerY) - 1

                val colMin = minOf(c1.toInt(), c2)
                val colMax = maxOf(c1.toInt(), c2)
                val rowMin = minOf(r1.toInt(), r2)
                val rowMax = maxOf(r1.toInt(), r2)

                if (col !in colMin..colMax) col = (colMin + colMax) / 2
                if (row !in rowMin..rowMax) row = (rowMin + rowMax) / 2

                CellAddress(row.coerceAtLeast(0), col.coerceAtLeast(0))
            }

            is HSSFClientAnchor -> {
                val c1 = anchor.col1
                val r1 = anchor.row1
                val c2 = anchor.col2
                val r2 = anchor.row2
                if (c1 < 0 || r1 < 0 || c2 < 0 || r2 < 0) return null
                val col = (c1 + c2) / 2
                val row = (r1 + r2) / 2
                CellAddress(row, col)
            }

            else -> null
        }
    }

    fun fallbackCellFromXml(drawing: XSSFDrawing, sheet: Sheet, picture: Picture): CellAddress? {
        val xpic = picture as? XSSFPicture ?: return null
        val myPic = runCatching { xpic.ctPicture }.getOrNull() ?: return null
        val myNv = runCatching { myPic.nvPicPr.cNvPr }.getOrNull()
        val myId = runCatching { myNv?.id }.getOrNull()

        try {
            val twoCellList = drawing.ctDrawing.twoCellAnchorList ?: emptyList()
            for (tca in twoCellList) {
                val pic = tca.pic ?: continue
                val id = runCatching { pic.nvPicPr.cNvPr.id }.getOrNull()
                if (id != null && id == myId) {
                    val from = tca.from
                    val to = tca.to
                    if (from != null && to != null) {
                        val fromCol = getLongViaGetter(from, "getCol")?.toInt() ?: continue
                        val fromRow = getLongViaGetter(from, "getRow")?.toInt() ?: continue
                        val toCol = getLongViaGetter(to, "getCol")?.toInt() ?: continue
                        val toRow = getLongViaGetter(to, "getRow")?.toInt() ?: continue

                        val centerCol = (fromCol + toCol) / 2
                        val centerRow = (fromRow + toRow) / 2
                        return CellAddress(centerRow, centerCol)
                    }
                }
            }

            val oneCellList = drawing.ctDrawing.oneCellAnchorList ?: emptyList()
            for (oca in oneCellList) {
                val pic = oca.pic ?: continue
                val id = runCatching { pic.nvPicPr.cNvPr.id }.getOrNull()
                if (id != null && id == myId) {
                    val from = oca.from
                    val ext = oca.ext
                    if (from != null && ext != null) {
                        val col = getLongViaGetter(from, "getCol")?.toInt() ?: continue
                        val row = getLongViaGetter(from, "getRow")?.toInt() ?: continue
                        return CellAddress(row, col)
                    }
                }
            }

            val absoluteList = drawing.ctDrawing.absoluteAnchorList ?: emptyList()
            for (aa in absoluteList) {
                val pic = aa.pic ?: continue
                val id = runCatching { pic.nvPicPr.cNvPr.id }.getOrNull()
                if (id != null && id == myId) {
                    val pos = aa.pos
                    val ext = aa.ext
                    val x = getLongViaGetter(pos, "getX")
                    val y = getLongViaGetter(pos, "getY")
                    val cx = getLongViaGetter(ext, "getCx")
                    val cy = getLongViaGetter(ext, "getCy")
                    if (x != null && y != null && cx != null && cy != null) {
                        val centerX = x + cx / 2
                        val centerY = y + cy / 2
                        val col = EmuCalculator.emuToColumn(sheet, centerX)
                        val row = EmuCalculator.emuToRow(sheet, centerY)
                        return CellAddress(row, col)
                    }
                }
            }
        } catch (_: Throwable) {
            return null
        }
        return null
    }

    fun getLongViaGetter(target: Any?, method: String): Long? {
        if (target == null) return null
        return try {
            val m = target.javaClass.getMethod(method)
            when (val result = m.invoke(target)) {
                is Long -> result
                is Int -> result.toLong()
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }
}
