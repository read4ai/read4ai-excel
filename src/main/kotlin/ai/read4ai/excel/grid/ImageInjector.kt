package ai.read4ai.excel.grid

import ai.read4ai.excel.ExcelConfig
import ai.read4ai.excel.image.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.poi.hssf.usermodel.HSSFPatriarch
import org.apache.poi.ss.usermodel.ClientAnchor
import org.apache.poi.ss.usermodel.Picture
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.util.CellAddress
import org.apache.poi.util.Units
import org.apache.poi.xssf.usermodel.XSSFDrawing
import org.apache.poi.xssf.usermodel.XSSFPicture
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import javax.imageio.ImageIO
import kotlin.math.roundToInt

internal class ImageInjector(
    private val config: ExcelConfig = ExcelConfig(),
) {
    private val log = KotlinLogging.logger {}
    private val imageSemaphore = Semaphore(config.maxConcurrentImageRequests)

    private data class PreparedPicture(
        val index: Int,
        val sheetAddress: CellAddress,
        val gridRowIndex: Int,
        val gridColumnIndex: Int,
        val base64: String,
        val mimeType: String,
    )

    private data class PictureResult(
        val index: Int,
        val sheetAddress: CellAddress,
        val gridRowIndex: Int,
        val gridColumnIndex: Int,
        val content: String,
    )

    /**
     * Inject image contents into the grid. For each image in the sheet,
     * find its center cell and inject its description or base64 content.
     */
    fun injectImageContents(
        sheet: Sheet,
        gridContext: GridExtractionResult,
    ): List<List<String>> {
        val grid = gridContext.grid
        if (config.imageOutput == ExcelConfig.ImageOutput.SKIP) {
            return grid
        }

        val drawing = sheet.drawingPatriarch ?: run {
            log.debug { "sheet='${sheet.sheetName}' has no drawing patriarch" }
            return grid
        }

        val pictures: List<Picture> = when (drawing) {
            is XSSFDrawing -> PictureCollector.collectPicturesXssf(drawing)
            is HSSFPatriarch -> PictureCollector.collectPicturesHssf(drawing)
            else -> emptyList()
        }

        val shapeTextEntries = when (drawing) {
            is XSSFDrawing -> PictureCollector.collectShapeTextEntries(drawing)
            is HSSFPatriarch -> PictureCollector.collectShapeTextEntries(drawing)
            else -> emptyList()
        }

        if (pictures.isEmpty() && shapeTextEntries.isEmpty()) {
            return grid
        }

        val mutableGrid = grid.map { it.toMutableList() }.toMutableList()

        val maxSheetRow = sheet.lastRowNum.coerceAtLeast(0)
        val rowCumSheet = LongArray(maxSheetRow + 2)
        for (r in 0..maxSheetRow) {
            val heightPoints = sheet.getRow(r)?.heightInPoints ?: sheet.defaultRowHeightInPoints
            val heightEmu = Units.toEMU(heightPoints.toDouble()).toLong()
            rowCumSheet[r + 1] = rowCumSheet[r] + heightEmu
        }

        var maxSheetCol = gridContext.visibleColumns.maxOrNull() ?: -1

        fun considerAnchor(anchor: ClientAnchor?) {
            if (anchor == null) return
            runCatching { anchor.col1 }.getOrNull()?.toInt()?.let { if (it > maxSheetCol) maxSheetCol = it }
            runCatching { anchor.col2 }.getOrNull()?.toInt()?.let { if (it > maxSheetCol) maxSheetCol = it }
        }

        pictures.forEach { pic -> considerAnchor(runCatching { pic.clientAnchor }.getOrNull()) }
        shapeTextEntries.forEach { entry -> considerAnchor(ShapeCellResolver.shapeClientAnchor(entry.shape)) }
        if (maxSheetCol < 0) maxSheetCol = 0
        val colCumSheet = LongArray(maxSheetCol + 2)
        for (c in 0..maxSheetCol) {
            val wPx = EmuCalculator.columnWidthInPixels(sheet, c)
            val widthEmu = Units.pixelToEMU(wPx.roundToInt().coerceAtLeast(1)).toLong()
            colCumSheet[c + 1] = colCumSheet[c] + widthEmu
        }

        fun withinGrid(row: Int, col: Int): Boolean =
            row in mutableGrid.indices && col in 0 until (mutableGrid.getOrNull(row)?.size ?: 0)

        // Inject shape text entries
        shapeTextEntries.forEachIndexed { idx, entry ->
            val rawAddress = ShapeCellResolver.shapeCenterCell(entry.shape, colCumSheet, rowCumSheet)
            val normalizedAddress = rawAddress?.let { GridExtractor.normalizeMergedCell(sheet, it) }
            if (normalizedAddress == null) return@forEachIndexed

            val gridRowIndex = gridContext.sheetRowToGridIndex[normalizedAddress.row]
                ?: EmuCalculator.nearestVisibleIndex(normalizedAddress.row, gridContext.visibleRowIndices)
            val gridColIndex = gridContext.sheetColToGridIndex[normalizedAddress.column]
                ?: EmuCalculator.nearestVisibleIndex(normalizedAddress.column, gridContext.visibleColumns)

            if (gridRowIndex == null || gridColIndex == null) return@forEachIndexed
            if (!withinGrid(gridRowIndex, gridColIndex)) return@forEachIndexed

            val existing = mutableGrid[gridRowIndex][gridColIndex]
            mutableGrid[gridRowIndex][gridColIndex] =
                if (existing.isBlank()) entry.text else "$existing\n${entry.text}"
        }

        // Process pictures
        val preparedPictures = mutableListOf<PreparedPicture>()

        for ((idx, pic) in pictures.withIndex()) {
            val sheetAddrRaw = ShapeCellResolver.imageCenterCell(sheet, pic, colCumSheet, rowCumSheet)
            val normalizedSheetAddr = sheetAddrRaw?.let { GridExtractor.normalizeMergedCell(sheet, it) }
            if (normalizedSheetAddr == null) continue

            val gridRowIndex = gridContext.sheetRowToGridIndex[normalizedSheetAddr.row]
                ?: EmuCalculator.nearestVisibleIndex(normalizedSheetAddr.row, gridContext.visibleRowIndices)
            val gridColIndex = gridContext.sheetColToGridIndex[normalizedSheetAddr.column]
                ?: EmuCalculator.nearestVisibleIndex(normalizedSheetAddr.column, gridContext.visibleColumns)

            if (gridRowIndex == null || gridColIndex == null) continue
            if (!withinGrid(gridRowIndex, gridColIndex)) continue

            val (ext, mime, raw) = ImageExtractor.extractPictureBytes(pic, sheet)
            if (raw == null) continue

            val buffered = ImageExtractor.pictureToBufferedImage(ext, mime, raw)
            val base64 = if (buffered == null) {
                null
            } else {
                try {
                    encodeBufferedImageToBase64(buffered, mime)
                } catch (_: Exception) {
                    null
                } finally {
                    runCatching { buffered.flush() }
                }
            }

            if (base64 == null) continue

            preparedPictures.add(
                PreparedPicture(
                    index = idx,
                    sheetAddress = normalizedSheetAddr,
                    gridRowIndex = gridRowIndex,
                    gridColumnIndex = gridColIndex,
                    base64 = base64,
                    mimeType = mime ?: "image/png",
                )
            )
        }

        if (preparedPictures.isEmpty()) {
            return mutableGrid
        }

        // Determine content for each picture based on image output mode
        val results: List<PictureResult> = when (config.imageOutput) {
            ExcelConfig.ImageOutput.BASE64 -> {
                preparedPictures.map { prepared ->
                    PictureResult(
                        index = prepared.index,
                        sheetAddress = prepared.sheetAddress,
                        gridRowIndex = prepared.gridRowIndex,
                        gridColumnIndex = prepared.gridColumnIndex,
                        content = "[image:${prepared.mimeType}:base64]",
                    )
                }
            }

            ExcelConfig.ImageOutput.HYBRID -> {
                val hybridConfig = config.hybridConfig
                if (hybridConfig == null) {
                    log.warn { "HYBRID mode but no hybridConfig provided, skipping images" }
                    return mutableGrid
                }

                val executor = ExecutorProvider.create()
                try {
                    preparedPictures.map { prepared ->
                        executor.submit<PictureResult> {
                            imageSemaphore.acquire()
                            val content = try {
                                hybridConfig.describeImage(prepared.base64, prepared.mimeType)
                            } finally {
                                imageSemaphore.release()
                            }
                            PictureResult(
                                index = prepared.index,
                                sheetAddress = prepared.sheetAddress,
                                gridRowIndex = prepared.gridRowIndex,
                                gridColumnIndex = prepared.gridColumnIndex,
                                content = content,
                            )
                        }
                    }.map { it.get() }
                } finally {
                    executor.shutdown()
                }
            }

            ExcelConfig.ImageOutput.SKIP -> return mutableGrid
        }

        for (result in results) {
            val r = result.gridRowIndex
            val c = result.gridColumnIndex
            if (!withinGrid(r, c)) continue
            val prev = mutableGrid[r][c]
            mutableGrid[r][c] = if (prev.isBlank()) result.content else "$prev\n${result.content}"
        }

        return mutableGrid
    }

    private fun encodeBufferedImageToBase64(image: BufferedImage, mimeType: String?): String {
        val format = when (mimeType?.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/gif" -> "gif"
            "image/bmp" -> "bmp"
            else -> "png"
        }

        return ByteArrayOutputStream().use { outputStream ->
            ImageIO.write(image, format, outputStream)
            outputStream.flush()
            Base64.getEncoder().encodeToString(outputStream.toByteArray())
        }
    }
}
