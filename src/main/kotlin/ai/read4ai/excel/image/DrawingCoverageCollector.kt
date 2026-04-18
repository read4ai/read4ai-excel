package ai.read4ai.excel.image

import org.apache.poi.hssf.usermodel.HSSFPatriarch
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFDrawing
import org.openxmlformats.schemas.drawingml.x2006.main.CTPositiveSize2D
import org.openxmlformats.schemas.drawingml.x2006.spreadsheetDrawing.CTMarker

internal object DrawingCoverageCollector {

    data class DrawingCoverage(
        val rows: Set<Int>,
        val columns: Set<Int>,
    )

    fun collectDrawingCoverage(sheet: Sheet): DrawingCoverage {
        val drawing = sheet.drawingPatriarch ?: return DrawingCoverage(emptySet(), emptySet())

        val rowSet = mutableSetOf<Int>()
        val colSet = mutableSetOf<Int>()

        when (drawing) {
            is XSSFDrawing -> collectCoverageFromXml(sheet, drawing, rowSet, colSet)
            is HSSFPatriarch -> {
                for (pic in drawing.children) {
                    val anchor = runCatching { pic.anchor as? org.apache.poi.ss.usermodel.ClientAnchor }.getOrNull()
                    if (anchor != null) {
                        val c1 = anchor.col1
                        val r1 = anchor.row1
                        val c2 = anchor.col2
                        val r2 = anchor.row2
                        val minC = minOf(c1, c2)
                        val maxC = maxOf(c1, c2)
                        val minR = minOf(r1, r2)
                        val maxR = maxOf(r1, r2)
                        for (c in minC..maxC) if (c >= 0) colSet.add(c)
                        for (r in minR..maxR) if (r >= 0) rowSet.add(r)
                    }
                }
            }
        }

        return DrawingCoverage(rowSet.toSet(), colSet.toSet())
    }

    private fun collectCoverageFromXml(
        sheet: Sheet,
        drawing: XSSFDrawing,
        rowSet: MutableSet<Int>,
        colSet: MutableSet<Int>,
    ) {
        val ct = drawing.ctDrawing

        for (tca in ct.twoCellAnchorList) {
            val from = tca.from
            val to = tca.to
            if (from != null && to != null) {
                for (c in from.col..to.col) if (c >= 0) colSet.add(c)
                for (r in from.row..to.row) if (r >= 0) rowSet.add(r)
            }
        }

        for (oca in ct.oneCellAnchorList) {
            val from = oca.from
            val ext = oca.ext
            if (from != null && ext != null) {
                recordOneCellExtension(
                    sheet = sheet,
                    baseCol = from.col,
                    baseRow = from.row,
                    ext = ext,
                    rowSet = rowSet,
                    colSet = colSet,
                    fromMarker = from
                )
            }
        }

        for (aa in ct.absoluteAnchorList) {
            val pos = aa.pos
            val ext = aa.ext
            if (pos != null && ext != null) {
                val x = ShapeCellResolver.getLongViaGetter(pos, "getX") ?: continue
                val y = ShapeCellResolver.getLongViaGetter(pos, "getY") ?: continue
                val cx = ShapeCellResolver.getLongViaGetter(ext, "getCx") ?: continue
                val cy = ShapeCellResolver.getLongViaGetter(ext, "getCy") ?: continue
                recordEmuRectangle(sheet, x, y, cx, cy, rowSet, colSet)
            }
        }
    }

    private fun recordOneCellExtension(
        sheet: Sheet,
        baseCol: Int,
        baseRow: Int,
        ext: CTPositiveSize2D,
        rowSet: MutableSet<Int>,
        colSet: MutableSet<Int>,
        fromMarker: CTMarker? = null,
    ) {
        val baseX = EmuCalculator.columnStartEmu(
            sheet,
            baseCol
        ) + (ShapeCellResolver.getLongViaGetter(fromMarker, "getColOff") ?: 0L)
        val baseY = EmuCalculator.rowStartEmu(sheet, baseRow) + (ShapeCellResolver.getLongViaGetter(
            fromMarker,
            "getRowOff"
        ) ?: 0L)
        val cx = ShapeCellResolver.getLongViaGetter(ext, "getCx") ?: 0L
        val cy = ShapeCellResolver.getLongViaGetter(ext, "getCy") ?: 0L
        recordEmuRectangle(sheet, baseX, baseY, cx, cy, rowSet, colSet)
    }

    private fun recordEmuRectangle(
        sheet: Sheet,
        x: Long,
        y: Long,
        cx: Long,
        cy: Long,
        rowSet: MutableSet<Int>,
        colSet: MutableSet<Int>,
    ) {
        val startCol = EmuCalculator.emuToColumn(sheet, x)
        val endCol = EmuCalculator.emuToColumn(sheet, x + cx)
        val startRow = EmuCalculator.emuToRow(sheet, y)
        val endRow = EmuCalculator.emuToRow(sheet, y + cy)

        for (c in minOf(startCol, endCol)..maxOf(startCol, endCol)) if (c >= 0) colSet.add(c)
        for (r in minOf(startRow, endRow)..maxOf(startRow, endRow)) if (r >= 0) rowSet.add(r)
    }
}
