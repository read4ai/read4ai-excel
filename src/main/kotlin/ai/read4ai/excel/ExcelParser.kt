package ai.read4ai.excel

import ai.read4ai.excel.grid.GridExtractionResult
import ai.read4ai.excel.grid.ImageInjector
import ai.read4ai.excel.image.DrawingCoverageCollector
import ai.read4ai.excel.model.ExcelDocument
import ai.read4ai.excel.model.MergeRegionInfo
import ai.read4ai.excel.model.Sheet
import ai.read4ai.excel.strategy.*
import ai.read4ai.excel.strategy.impl.DefaultGridExtractor
import ai.read4ai.excel.grid.ElementClassifier
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.poi.EncryptedDocumentException
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Workbook
import java.nio.file.Files
import java.nio.file.Path
import ai.read4ai.excel.grid.GridExtractor as PoiGridExtractor

/**
 * Main entry point for parsing Excel (.xlsx, .xls) and CSV (.csv, .tsv) files.
 *
 * CSV files are automatically detected by extension and delegated to [CsvParser].
 */
object ExcelParser {

    private val log = KotlinLogging.logger {}

    /**
     * @throws ExcelParseException.EncryptedFile if the file is password-protected
     * @throws ExcelParseException.InvalidFormat if the file cannot be opened
     */
    @JvmStatic
    @JvmOverloads
    fun parse(
        bytes: ByteArray,
        fileName: String? = null,
        config: ExcelConfig = ExcelConfig(),
        strategy: StrategyConfig = StrategyConfig(),
    ): ExcelDocument {
        if (isCsvFile(fileName)) {
            val doc = CsvParser.parse(bytes, fileName)
            return doc.copy(language = ai.read4ai.excel.lang.LanguageDetector.detect(doc))
        }

        log.debug { "Starting parse, fileName=$fileName, size=${bytes.size}" }

        val imageInjector = ImageInjector(config)

        val workbook: Workbook = strategy.workbookReader.read(bytes, config)

        return workbook.use { wb ->
            val evaluator = try {
                wb.creationHelper.createFormulaEvaluator().also {
                    it.evaluateAll()
                }
            } catch (e: Exception) {
                log.warn { "Formula evaluation failed, using non-caching evaluator: ${e.message}" }
                wb.creationHelper.createFormulaEvaluator()
            }

            val dataFormatter = DataFormatter()
            val numberOfSheets = wb.numberOfSheets

            // Configure the grid extractor if it's the default implementation
            val gridExtractor = strategy.gridExtractor
            if (gridExtractor is DefaultGridExtractor) {
                gridExtractor.evaluator = evaluator
                gridExtractor.dataFormatter = dataFormatter
            }

            val sheets = (0 until numberOfSheets).map { sheetIndex ->
                val poiSheet = wb.getSheetAt(sheetIndex)
                processSheet(poiSheet, sheetIndex, gridExtractor, imageInjector, config, strategy)
            }

            val doc = ExcelDocument(
                fileName = fileName,
                numberOfSheets = numberOfSheets,
                sheets = sheets,
            )
            doc.copy(language = ai.read4ai.excel.lang.LanguageDetector.detect(doc))
        }
    }

    /** @see parse(ByteArray, String?, ExcelConfig, StrategyConfig) */
    @JvmStatic
    @JvmOverloads
    fun parse(
        path: Path,
        config: ExcelConfig = ExcelConfig(),
        strategy: StrategyConfig = StrategyConfig(),
    ): ExcelDocument {
        val fileName = path.fileName?.toString()
        if (isCsvFile(fileName)) {
            val doc = CsvParser.parse(path)
            return doc.copy(language = ai.read4ai.excel.lang.LanguageDetector.detect(doc))
        }
        val bytes = Files.readAllBytes(path)
        return parse(bytes, fileName, config, strategy)
    }

    private fun processSheet(
        poiSheet: org.apache.poi.ss.usermodel.Sheet,
        sheetIndex: Int,
        gridExtractor: ai.read4ai.excel.strategy.GridExtractor,
        imageInjector: ImageInjector,
        config: ExcelConfig,
        strategy: StrategyConfig,
    ): Sheet {
        log.debug { "Processing sheet $sheetIndex: '${poiSheet.sheetName}'" }

        // Step 2: Extract grid
        val grid: Grid
        val gridContext: GridExtractionResult?

        if (gridExtractor is DefaultGridExtractor) {
            // Use the context-returning variant for backward compatibility with image injection
            val (g, ctx) = gridExtractor.extractWithContext(poiSheet)
            grid = g
            gridContext = ctx
        } else {
            grid = gridExtractor.extract(poiSheet)
            gridContext = null
        }

        // Step 2b: Inject image contents (uses grid context if available)
        val enrichedCells = if (gridContext != null && poiSheet.drawingPatriarch != null) {
            imageInjector.injectImageContents(poiSheet, gridContext)
        } else {
            grid.cells
        }

        val enrichedGrid = Grid(
            cells = enrichedCells,
            mergeRegions = grid.mergeRegions,
            rowCount = enrichedCells.size,
            colCount = enrichedCells.maxOfOrNull { it.size } ?: 0,
        )

        // Use the strategy-stage processing path
        val elements = processWithStrategyStages(enrichedGrid, gridContext, strategy)

        // Annotate merge spans on Table cells from grid merge regions
        val annotatedElements = annotateMergeSpans(elements, enrichedGrid.mergeRegions)

        // Build top-level merge region summary from grid merge data
        val mergeRegions = enrichedGrid.mergeRegions.map { mr ->
            val colLetter = columnIndexToLetter(mr.firstCol)
            val cellRef = "$colLetter${mr.firstRow + 1}"
            MergeRegionInfo(
                cell = cellRef,
                rowSpan = mr.lastRow - mr.firstRow + 1,
                colSpan = mr.lastCol - mr.firstCol + 1,
            )
        }

        return Sheet(
            sheetIndex = sheetIndex,
            sheetName = poiSheet.sheetName,
            elements = annotatedElements,
            mergeRegions = mergeRegions,
        )
    }

    /**
     * Post-processing: annotate [ai.read4ai.excel.model.Cell.mergedRight] and
     * [ai.read4ai.excel.model.Cell.mergedDown] on [Element.Table] cells from the grid merge regions.
     * Works regardless of which processing path produced the elements.
     *
     * Cells already carrying non-zero merge info are left untouched.
     */
    private fun annotateMergeSpans(
        elements: List<ai.read4ai.excel.model.Element>,
        gridMerges: List<ai.read4ai.excel.strategy.MergeRegion>,
    ): List<ai.read4ai.excel.model.Element> {
        if (gridMerges.isEmpty()) return elements

        return elements.map { element ->
            if (element !is ai.read4ai.excel.model.Element.Table) return@map element

            val maxRow = element.rows.size
            val maxCol = element.rows.firstOrNull()?.cells?.size ?: 0
            if (maxRow == 0 || maxCol == 0) return@map element

            // Build lookup: (localRow, localCol) -> (colSpan, rowSpan) where spans are 0-based extras.
            // Only fully-contained merges (both anchor and end within table bounds) are applied —
            // partial merges spanning outside the table boundary are skipped to avoid fabricating
            // merges that don't exist in the physical sheet.
            val mergeMap = mutableMapOf<Pair<Int, Int>, Pair<Int, Int>>()
            for (mr in gridMerges) {
                val localRow = mr.firstRow - element.startRow
                val localCol = mr.firstCol - element.startCol
                val rowSpan = mr.lastRow - mr.firstRow
                val colSpan = mr.lastCol - mr.firstCol
                val lastLocalRow = localRow + rowSpan
                val lastLocalCol = localCol + colSpan
                if ((rowSpan > 0 || colSpan > 0) &&
                    localRow in 0 until maxRow &&
                    localCol in 0 until maxCol &&
                    lastLocalRow < maxRow &&
                    lastLocalCol < maxCol
                ) {
                    mergeMap[localRow to localCol] = colSpan to rowSpan
                }
            }

            if (mergeMap.isEmpty()) return@map element

            val newRows = element.rows.mapIndexed { rowIdx, row ->
                val newCells = row.cells.mapIndexed { colIdx, cell ->
                    val merge = mergeMap[rowIdx to colIdx]
                    if (merge != null && cell.mergedRight == 0 && cell.mergedDown == 0) {
                        cell.copy(mergedRight = merge.first, mergedDown = merge.second)
                    } else {
                        cell
                    }
                }
                row.copy(cells = newCells)
            }
            element.copy(rows = newRows)
        }
    }

    /**
     * Process a grid through the strategy stages: segment -> detect headers ->
     * classify -> order blocks -> extract elements.
     */
    private fun processWithStrategyStages(
        grid: Grid,
        gridContext: GridExtractionResult?,
        strategy: StrategyConfig,
    ): List<ai.read4ai.excel.model.Element> {
        // Check if using all default implementations -- use legacy path for exact backward compat
        if (usesDefaultStrategy(strategy) && gridContext != null) {
            return ElementClassifier.classify(grid.cells, gridContext)
        }

        // Strategy-stage path: segment -> header detect -> classify -> order
        val segments = strategy.segmenter.segment(grid)

        val blocks = segments.map { segment ->
            val headerInfo = strategy.headerDetector.detectHeaders(segment)
            val element = strategy.elementClassifier.classify(segment, headerInfo)
            Block(
                segment = segment,
                headerInfo = headerInfo,
                element = element,
                isDeferred = segment.gapFromPrevious >= 2,
            )
        }

        val ordered = strategy.blockOrderer.order(blocks)
        val elements = ordered.map { it.element }

        // Fallback: if no elements, create a single table from the entire grid
        if (elements.isEmpty() && grid.cells.isNotEmpty()) {
            val tableRows = grid.cells.mapIndexed { rowIdx, row ->
                ai.read4ai.excel.model.Row(
                    rowIndex = rowIdx,
                    cells = row.map { cellValue ->
                        ai.read4ai.excel.model.Cell(value = cellValue)
                    },
                )
            }
            return listOf(ai.read4ai.excel.model.Element.Table(rows = tableRows))
        }

        return elements
    }

    /**
     * Convert a 0-based column index to an Excel-style column letter (A, B, ..., Z, AA, ...).
     */
    private fun columnIndexToLetter(index: Int): String {
        val sb = StringBuilder()
        var c = index
        while (c >= 0) {
            sb.insert(0, ('A' + c % 26))
            c = c / 26 - 1
        }
        return sb.toString()
    }

    /**
     * Check if the strategy setup uses all default implementations.
     * When it does, we can use the legacy ElementClassifier path for exact backward compatibility.
     */
    private fun usesDefaultStrategy(strategy: StrategyConfig): Boolean {
        return strategy.workbookReader is ai.read4ai.excel.strategy.impl.PoiWorkbookReader &&
            strategy.gridExtractor is DefaultGridExtractor &&
            strategy.segmenter is ai.read4ai.excel.strategy.impl.SimpleSegmenter &&
            strategy.headerDetector is ai.read4ai.excel.strategy.impl.SingleRowHeaderDetector &&
            strategy.blockOrderer is ai.read4ai.excel.strategy.impl.SequentialBlockOrderer &&
            strategy.elementClassifier is ai.read4ai.excel.strategy.impl.DefaultElementClassifier
    }

    private val CSV_EXTENSIONS = setOf("csv", "tsv")

    private fun isCsvFile(fileName: String?): Boolean {
        if (fileName == null) return false
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in CSV_EXTENSIONS
    }
}
