package ai.read4ai.excel.pipeline.impl

import ai.read4ai.excel.grid.GridExtractionResult
import ai.read4ai.excel.image.DrawingCoverageCollector
import ai.read4ai.excel.pipeline.Grid
import ai.read4ai.excel.pipeline.GridExtractor
import ai.read4ai.excel.pipeline.MergeRegion
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.FormulaEvaluator
import org.apache.poi.ss.usermodel.Sheet
import ai.read4ai.excel.grid.GridExtractor as PoiGridExtractor

/**
 * Default [GridExtractor] that wraps the existing [PoiGridExtractor.toMergedGrid] logic.
 *
 * Requires a [FormulaEvaluator] and [DataFormatter] to be set before use
 * (typically done by the pipeline runner in ExcelParser).
 *
 * This reproduces the existing ExcelParser behavior.
 */
class DefaultGridExtractor : GridExtractor {

    /** Set by the pipeline runner before extraction. */
    var evaluator: FormulaEvaluator? = null

    /** Set by the pipeline runner before extraction. */
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
     * This is used internally by the pipeline runner to preserve merge region mappings
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
         * Convert a [GridExtractionResult] to the pipeline [Grid] model.
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
