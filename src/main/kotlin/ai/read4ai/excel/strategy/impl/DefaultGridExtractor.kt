package ai.read4ai.excel.strategy.impl

import ai.read4ai.excel.grid.GridExtractionResult
import ai.read4ai.excel.image.DrawingCoverageCollector
import ai.read4ai.excel.strategy.Grid
import ai.read4ai.excel.strategy.GridExtractor
import ai.read4ai.excel.strategy.MergeRegion
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.FormulaEvaluator
import org.apache.poi.ss.usermodel.Sheet
import ai.read4ai.excel.grid.GridExtractor as PoiGridExtractor

/**
 * Default [GridExtractor] that wraps the existing [PoiGridExtractor.toMergedGrid] logic.
 *
 * Requires a [FormulaEvaluator] and [DataFormatter] to be set before use
 * (typically done by the parser runtime in ExcelParser).
 *
 * This reproduces the existing ExcelParser behavior.
 */
class DefaultGridExtractor : GridExtractor {

    /** Set by the parser runtime before extraction. */
    var evaluator: FormulaEvaluator? = null

    /** Set by the parser runtime before extraction. */
    var dataFormatter: DataFormatter? = null

    override fun extract(sheet: Sheet): Grid {
        val eval = evaluator ?: throw IllegalStateException(
            "DefaultGridExtractor.evaluator must be set before extraction"
        )
        val fmt = dataFormatter ?: throw IllegalStateException(
            "DefaultGridExtractor.dataFormatter must be set before extraction"
        )

        val drawingCoverage = DrawingCoverageCollector.collectDrawingCoverage(sheet)

        val result = PoiGridExtractor.toMergedGrid(
            sheet = sheet,
            evaluator = eval,
            dataFormatter = fmt,
            additionalRows = drawingCoverage.rows,
            additionalColumns = drawingCoverage.columns,
        )

        return toGrid(result)
    }

    /**
     * Extract the grid and also return the raw [GridExtractionResult] for downstream use.
     * This is used internally by the parser runtime to preserve merge region mappings
     * needed for image injection and markdown rendering.
     */
    internal fun extractWithContext(sheet: Sheet): Pair<Grid, GridExtractionResult> {
        val eval = evaluator ?: throw IllegalStateException(
            "DefaultGridExtractor.evaluator must be set before extraction"
        )
        val fmt = dataFormatter ?: throw IllegalStateException(
            "DefaultGridExtractor.dataFormatter must be set before extraction"
        )

        val drawingCoverage = DrawingCoverageCollector.collectDrawingCoverage(sheet)

        val result = PoiGridExtractor.toMergedGrid(
            sheet = sheet,
            evaluator = eval,
            dataFormatter = fmt,
            additionalRows = drawingCoverage.rows,
            additionalColumns = drawingCoverage.columns,
        )

        return toGrid(result) to result
    }

    companion object {
        /**
         * Convert a [GridExtractionResult] to the strategy-stage [Grid] model.
         */
        internal fun toGrid(result: GridExtractionResult): Grid {
            val mergeRegions = result.mergedRegions.map { region ->
                MergeRegion(
                    firstRow = region.firstRow,
                    lastRow = region.lastRow,
                    firstCol = region.firstColumn,
                    lastCol = region.lastColumn,
                )
            }
            return Grid(
                cells = result.grid,
                mergeRegions = mergeRegions,
                rowCount = result.grid.size,
                colCount = result.grid.maxOfOrNull { it.size } ?: 0,
            )
        }
    }
}
