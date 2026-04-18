package ai.read4ai.excel.grid

internal object SpatialSegmenter {

    private const val MIN_DEFERRED_GAP_ROWS = 2

    data class RowBand(
        val rows: List<List<String>>,
    )

    data class StripeSegment(
        val rows: List<List<String>>,
        val gapRowsFromPrevious: Int,
        val startRowIndex: Int,
    )

    /**
     * Split a grid into row bands separated by consecutive empty rows.
     */
    fun splitIntoRowBands(
        grid: List<List<String>>,
        minEmptyRowsBetweenBands: Int = MIN_DEFERRED_GAP_ROWS,
    ): List<RowBand> {
        if (grid.isEmpty()) return emptyList()

        val bands = mutableListOf<RowBand>()
        var currentRows = mutableListOf<List<String>>()
        var consecutiveEmpty = 0

        fun flushBand() {
            if (currentRows.isNotEmpty()) {
                bands.add(RowBand(currentRows.toList()))
                currentRows = mutableListOf()
            }
        }

        grid.forEach { row ->
            if (isRowEmpty(row)) {
                consecutiveEmpty++
                if (consecutiveEmpty >= minEmptyRowsBetweenBands) {
                    flushBand()
                    consecutiveEmpty = 0
                } else {
                    if (currentRows.isNotEmpty()) {
                        currentRows.add(row)
                    }
                }
            } else {
                currentRows.add(row)
                consecutiveEmpty = 0
            }
        }

        flushBand()
        return bands
    }

    /**
     * Split columns by empty-column gaps (at least 1 empty column separates sub-tables).
     */
    fun splitByEmptyColumns(
        grid: List<List<String>>,
        minCols: Int = 1,
    ): List<List<List<String>>> {
        if (grid.isEmpty()) return emptyList()
        val maxCols = grid.maxOfOrNull { it.size } ?: 0
        if (maxCols == 0) return emptyList()

        val nonEmptyCols = mutableListOf<Int>()
        for (c in 0 until maxCols) {
            var hasValue = false
            for (row in grid) {
                val v = if (c < row.size) row[c] else ""
                if (v.isNotBlank()) {
                    hasValue = true; break
                }
            }
            if (hasValue) nonEmptyCols.add(c)
        }
        if (nonEmptyCols.isEmpty()) return emptyList()

        val segments = mutableListOf<IntRange>()
        var start = nonEmptyCols.first()
        var prev = start
        for (i in 1 until nonEmptyCols.size) {
            val col = nonEmptyCols[i]
            val gap = col - prev - 1
            if (gap >= 1) {
                segments.add(start..prev)
                start = col
            }
            prev = col
        }
        segments.add(start..prev)

        val subTables = mutableListOf<List<List<String>>>()
        for (seg in segments) {
            val width = seg.last - seg.first + 1
            if (width < minCols) continue
            val sub = grid.map { row ->
                (seg.first..seg.last).map { c -> if (c < row.size) row[c] else "" }
            }
            val hasAnyValue = sub.any { r -> r.any { it.isNotBlank() } }
            if (hasAnyValue) subTables.add(sub)
        }

        return subTables
    }

    /**
     * Split a stripe (column-separated sub-table) into segments by empty rows.
     */
    fun splitStripeWithIndices(
        stripe: List<List<String>>,
        minEmptyRowsBetweenSegments: Int = MIN_DEFERRED_GAP_ROWS,
    ): List<StripeSegment> {
        if (stripe.isEmpty()) return emptyList()

        val segments = mutableListOf<StripeSegment>()
        var currentRows = mutableListOf<List<String>>()
        var currentStartIndex = 0
        var gapBeforeCurrent = 0
        var pendingGap = 0

        fun flushSegment() {
            if (currentRows.isEmpty()) return
            segments.add(
                StripeSegment(
                    rows = currentRows.toList(),
                    gapRowsFromPrevious = gapBeforeCurrent,
                    startRowIndex = currentStartIndex,
                )
            )
            currentRows = mutableListOf()
        }

        stripe.forEachIndexed { index, row ->
            if (isRowEmpty(row)) {
                pendingGap++
                if (currentRows.isNotEmpty()) {
                    if (pendingGap >= minEmptyRowsBetweenSegments) {
                        flushSegment()
                    } else {
                        currentRows.add(row)
                    }
                }
                return@forEachIndexed
            }

            if (currentRows.isEmpty()) {
                currentStartIndex = index
                gapBeforeCurrent = pendingGap
            }

            currentRows.add(row)
            pendingGap = 0
        }

        flushSegment()
        return segments
    }

    /**
     * Extract isolated cells (single non-blank cell in a row, surrounded by blank rows).
     * Returns a pair of (isolated cell texts, remaining rows after removal).
     */
    fun extractIsolatedCells(
        rows: List<List<String>>,
    ): Pair<List<String>, List<List<String>>> {
        if (rows.isEmpty()) return emptyList<String>() to emptyList()

        val isolatedTexts = mutableListOf<String>()
        val removeFlags = BooleanArray(rows.size)

        fun isBlankRow(row: List<String>): Boolean = row.all { it.isBlank() }

        rows.forEachIndexed { index, row ->
            val nonBlankColumns = row.withIndex().filter { it.value.isNotBlank() }.map { it.index }
            if (nonBlankColumns.size != 1) return@forEachIndexed

            val col = nonBlankColumns.single()
            val leftBlank = (col == 0) || row[col - 1].isBlank()
            val rightBlank = (col == row.lastIndex) || row[col + 1].isBlank()
            val upBlank = index == 0 || isBlankRow(rows[index - 1])
            val downBlank = index == rows.lastIndex || isBlankRow(rows[index + 1])

            if (leftBlank && rightBlank && upBlank && downBlank) {
                val text = row[col].trim()
                if (text.isNotEmpty()) {
                    isolatedTexts.add(text)
                }
                removeFlags[index] = true
                val prevIdx = index - 1
                val nextIdx = index + 1
                if (prevIdx >= 0 && isBlankRow(rows[prevIdx])) removeFlags[prevIdx] = true
                if (nextIdx < rows.size && isBlankRow(rows[nextIdx])) removeFlags[nextIdx] = true
            }
        }

        val remainingRows = rows.filterIndexed { idx, _ -> !removeFlags[idx] }
        return isolatedTexts to remainingRows
    }

    fun trimBlankEdgeRows(rows: List<List<String>>): List<List<String>> {
        if (rows.isEmpty()) return rows
        var start = 0
        var end = rows.size
        while (start < end && rows[start].all { it.isBlank() }) start++
        while (end > start && rows[end - 1].all { it.isBlank() }) end--
        if (start == 0 && end == rows.size) return rows
        return rows.subList(start, end).map { it.toList() }
    }

    fun isRowEmpty(row: List<String>): Boolean = row.all { it.isBlank() }

    fun isIsolatedSingleCellContent(rows: List<List<String>>): Boolean {
        if (rows.isEmpty()) return false
        val nonEmptyCellCount = rows.sumOf { row -> row.count { it.isNotBlank() } }
        return nonEmptyCellCount == 1
    }

    fun extractSingleCellText(rows: List<List<String>>): String? {
        if (rows.isEmpty()) return null
        rows.forEach { row ->
            row.forEach { cell ->
                if (cell.isNotBlank()) return cell
            }
        }
        return null
    }
}
