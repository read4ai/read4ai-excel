package ai.read4ai.excel.grid

import ai.read4ai.excel.grid.GridExtractionResult
import ai.read4ai.excel.grid.MarkdownRenderer
import ai.read4ai.excel.grid.SpatialSegmenter
import ai.read4ai.excel.model.Cell
import ai.read4ai.excel.model.Element
import ai.read4ai.excel.model.Row
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Classifies grid segments into Element types (Table, Heading, Text, etc.)
 */
internal object ElementClassifier {

    private val log = KotlinLogging.logger {}
    private const val MIN_DEFERRED_GAP_ROWS = 2

    data class TitleInfo(
        val text: String,
        val gapBefore: Int,
    )

    /**
     * Convert a grid into a list of Elements by:
     * 1. Splitting into row bands
     * 2. Splitting each band by empty columns
     * 3. Splitting each stripe into segments
     * 4. Classifying each segment as title/text/table
     */
    fun classify(
        grid: List<List<String>>,
        gridContext: GridExtractionResult,
    ): List<Element> {
        val elements = mutableListOf<Element>()

        val rowBands = SpatialSegmenter.splitIntoRowBands(grid)
        log.debug { "${rowBands.size} row band(s) after global split" }

        // Track absolute row offsets for each band
        var bandSearchStart = 0

        rowBands.forEachIndexed { bandIndex, band ->
            val primaryElements = mutableListOf<Element>()
            val deferredElements = mutableListOf<Element>()

            // Find where this band starts in the original grid
            val bandStartRow = findBandStartRow(grid, band.rows, bandSearchStart)

            val columnStripes = SpatialSegmenter.splitByEmptyColumns(band.rows)
            if (columnStripes.isEmpty()) {
                bandSearchStart = bandStartRow + band.rows.size
                return@forEachIndexed
            }

            // Find column offsets for each stripe
            val stripeColOffsets = findStripeColumnOffsets(band.rows, columnStripes)

            columnStripes.forEachIndexed { stripeIdx, stripeGrid ->
                val colOffset = stripeColOffsets.getOrElse(stripeIdx) { 0 }
                val segments = SpatialSegmenter.splitStripeWithIndices(stripeGrid)
                var pendingTitle: TitleInfo? = null

                segments.forEach { segment ->
                    val detection = TitleDetector.detectTitle(segment.rows)
                    val contentRows: List<List<String>>
                    val contentGap: Int

                    if (detection != null) {
                        pendingTitle = TitleInfo(
                            text = detection.titleText,
                            gapBefore = segment.gapRowsFromPrevious,
                        )
                        contentRows = detection.remainingRows
                        contentGap = 0
                    } else {
                        contentRows = segment.rows
                        contentGap = segment.gapRowsFromPrevious
                    }

                    if (contentRows.isEmpty()) return@forEach

                    var currentTitle = pendingTitle

                    val (isolatedCells, remainingRowsRaw) = SpatialSegmenter.extractIsolatedCells(contentRows)

                    if (isolatedCells.isNotEmpty()) {
                        val shouldDeferSingle = currentTitle?.let {
                            it.gapBefore >= MIN_DEFERRED_GAP_ROWS || contentGap >= MIN_DEFERRED_GAP_ROWS
                        } ?: (contentGap >= MIN_DEFERRED_GAP_ROWS)

                        var titlePendingForCell = currentTitle
                        isolatedCells.filter { it.isNotBlank() }.forEach { cellText ->
                            titlePendingForCell?.let {
                                val target = if (shouldDeferSingle) deferredElements else primaryElements
                                target.add(Element.Heading(text = it.text))
                                titlePendingForCell = null
                                pendingTitle = null
                                currentTitle = null
                            }
                            val target = if (shouldDeferSingle) deferredElements else primaryElements
                            target.add(Element.Text(text = cellText.trim()))
                        }
                    }

                    val trimmedRows = SpatialSegmenter.trimBlankEdgeRows(remainingRowsRaw)
                    if (trimmedRows.isEmpty()) return@forEach

                    if (SpatialSegmenter.isIsolatedSingleCellContent(trimmedRows)) {
                        val cellText = SpatialSegmenter.extractSingleCellText(trimmedRows)
                        if (!cellText.isNullOrBlank()) {
                            val shouldDeferSingle = currentTitle?.let {
                                it.gapBefore >= MIN_DEFERRED_GAP_ROWS || contentGap >= MIN_DEFERRED_GAP_ROWS
                            } ?: (contentGap >= MIN_DEFERRED_GAP_ROWS)

                            currentTitle?.let {
                                val target = if (shouldDeferSingle) deferredElements else primaryElements
                                target.add(Element.Heading(text = it.text))
                                pendingTitle = null
                                currentTitle = null
                            }
                            val target = if (shouldDeferSingle) deferredElements else primaryElements
                            target.add(Element.Text(text = cellText.trim()))
                        }
                        return@forEach
                    }

                    // Table segment — rowIndex is relative (0-based within the table),
                    // startRow stores the absolute offset in the sheet grid.
                    val absoluteRowBase = bandStartRow + segment.startRowIndex
                    val tableRows = trimmedRows.mapIndexed { rowIdx, row ->
                        Row(
                            rowIndex = rowIdx,
                            cells = row.map { cellValue -> Cell(value = cellValue) },
                        )
                    }

                    val tableResult = MarkdownRenderer.toMarkdownTableWithHeaders(trimmedRows, gridContext.mergedRegions)
                    if (tableResult.markdownTable.isBlank()) return@forEach

                    val shouldDefer = currentTitle?.let {
                        it.gapBefore >= MIN_DEFERRED_GAP_ROWS || contentGap >= MIN_DEFERRED_GAP_ROWS
                    } ?: false

                    currentTitle?.let {
                        val target = if (shouldDefer) deferredElements else primaryElements
                        target.add(Element.Heading(text = it.text))
                        pendingTitle = null
                        currentTitle = null
                    }

                    val target = if (shouldDefer) deferredElements else primaryElements
                    target.add(
                        Element.Table(
                            rows = tableRows,
                            headerRowCount = tableResult.headerRows.size,
                            startRow = absoluteRowBase,
                            startCol = colOffset,
                        )
                    )
                }

                pendingTitle?.let { orphanTitle ->
                    if (orphanTitle.text.isNotBlank()) {
                        val target =
                            if (orphanTitle.gapBefore >= MIN_DEFERRED_GAP_ROWS) deferredElements else primaryElements
                        target.add(Element.Heading(text = orphanTitle.text))
                    }
                }
            }

            elements.addAll(primaryElements)
            elements.addAll(deferredElements)

            bandSearchStart = bandStartRow + band.rows.size
        }

        // If no elements found, create a single fallback table from the entire grid
        if (elements.isEmpty() && grid.isNotEmpty()) {
            val tableRows = grid.mapIndexed { rowIdx, row ->
                Row(
                    rowIndex = rowIdx,
                    cells = row.map { cellValue -> Cell(value = cellValue) },
                )
            }
            elements.add(Element.Table(rows = tableRows))
        }

        return elements
    }

    /**
     * Find where a band's rows start in the original grid.
     */
    private fun findBandStartRow(
        gridCells: List<List<String>>,
        bandRows: List<List<String>>,
        searchStart: Int,
    ): Int {
        if (bandRows.isEmpty()) return searchStart
        val firstBandRow = bandRows[0]
        for (i in searchStart until gridCells.size) {
            if (gridCells[i] == firstBandRow) return i
        }
        return searchStart
    }

    /**
     * Determine the column offset for each stripe within a band.
     */
    private fun findStripeColumnOffsets(
        bandRows: List<List<String>>,
        stripes: List<List<List<String>>>,
    ): List<Int> {
        if (bandRows.isEmpty() || stripes.isEmpty()) return emptyList()

        val maxCols = bandRows.maxOfOrNull { it.size } ?: 0
        val nonEmptyCols = mutableListOf<Int>()
        for (c in 0 until maxCols) {
            if (bandRows.any { row -> c < row.size && row[c].isNotBlank() }) {
                nonEmptyCols.add(c)
            }
        }

        if (nonEmptyCols.isEmpty()) return stripes.map { 0 }

        // Group contiguous non-empty columns
        val groups = mutableListOf<Int>()
        if (nonEmptyCols.isNotEmpty()) {
            groups.add(nonEmptyCols[0])
            for (i in 1 until nonEmptyCols.size) {
                if (nonEmptyCols[i] - nonEmptyCols[i - 1] > 1) {
                    groups.add(nonEmptyCols[i])
                }
            }
        }

        return stripes.indices.map { groups.getOrElse(it) { 0 } }
    }
}
