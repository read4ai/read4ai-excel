package ai.read4ai.excel.pipeline.impl

import ai.read4ai.excel.pipeline.Grid
import ai.read4ai.excel.pipeline.MergeRegion
import ai.read4ai.excel.pipeline.Segment
import ai.read4ai.excel.pipeline.Segmenter
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Graph-based segmenter inspired by Unstructured's connected-component approach.
 *
 * Algorithm:
 * 1. Build an implicit adjacency graph of non-empty cells (4-directional: up/down/left/right)
 * 2. Find connected components via BFS flood-fill
 * 3. Merge components whose row ranges overlap (horizontal merge)
 * 4. Each merged component's bounding box = one segment
 *
 * No external dependencies needed -- simple BFS replaces NetworkX.
 *
 * Differences from ThreeLevelSegmenter:
 * - Uses cell connectivity instead of empty-row/column gaps
 * - Handles irregular (non-rectangular) cell layouts
 * - Naturally detects scattered data islands
 * - May merge too aggressively when a single bridge cell connects two tables
 *
 * @param mergeOverlappingRows whether to merge components with overlapping row ranges (default: true)
 * @param mergeGapThreshold max row gap to still merge (0 = only overlapping, 1 = adjacent, etc.)
 */
class GraphSegmenter(
    private val mergeOverlappingRows: Boolean = true,
    private val mergeGapThreshold: Int = 1,
) : Segmenter {

    private val log = KotlinLogging.logger {}

    override fun segment(grid: Grid): List<Segment> {
        if (grid.cells.isEmpty()) return emptyList()

        val rowCount = grid.cells.size
        val colCount = grid.cells.maxOfOrNull { it.size } ?: 0
        if (colCount == 0) return emptyList()

        // Step 1: Find all non-empty cells
        val nonEmpty = mutableSetOf<Long>()
        for (r in 0 until rowCount) {
            val row = grid.cells[r]
            for (c in row.indices) {
                if (row[c].isNotBlank()) {
                    nonEmpty.add(pack(r, c))
                }
            }
        }

        if (nonEmpty.isEmpty()) return emptyList()

        // Step 2: BFS to find connected components
        val visited = mutableSetOf<Long>()
        val components = mutableListOf<BoundingBox>()

        for (cell in nonEmpty) {
            if (cell in visited) continue
            val bbox = bfs(cell, nonEmpty, visited, rowCount, colCount)
            components.add(bbox)
        }

        log.debug { "Found ${components.size} connected component(s)" }

        // Step 3: Merge overlapping row ranges
        val merged = if (mergeOverlappingRows && components.size > 1) {
            mergeOverlapping(components)
        } else {
            components
        }

        log.debug { "After merge: ${merged.size} segment(s)" }

        // Step 4: Sort by reading order (top-to-bottom, left-to-right)
        val sorted = merged.sortedWith(compareBy({ it.minRow }, { it.minCol }))

        // Step 5: Build segments
        var previousEndRow = -1
        return sorted.map { bbox ->
            val segRows = (bbox.minRow..bbox.maxRow).map { r ->
                val row = grid.cells.getOrElse(r) { emptyList() }
                (bbox.minCol..bbox.maxCol).map { c ->
                    if (c < row.size) row[c] else ""
                }
            }

            val gap = if (previousEndRow >= 0) {
                (bbox.minRow - previousEndRow - 1).coerceAtLeast(0)
            } else {
                bbox.minRow
            }
            previousEndRow = bbox.maxRow

            val segMergeRegions = filterMergeRegions(
                grid.mergeRegions, bbox.minRow, bbox.maxRow, bbox.minCol, bbox.maxCol,
            )

            Segment(
                grid = Grid(
                    cells = segRows,
                    mergeRegions = segMergeRegions,
                    rowCount = segRows.size,
                    colCount = segRows.maxOfOrNull { it.size } ?: 0,
                ),
                startRow = bbox.minRow,
                startCol = bbox.minCol,
                gapFromPrevious = gap,
            )
        }
    }

    /**
     * BFS flood-fill from a starting cell, collecting the bounding box of the connected component.
     */
    private fun bfs(
        start: Long,
        nonEmpty: Set<Long>,
        visited: MutableSet<Long>,
        maxRow: Int,
        maxCol: Int,
    ): BoundingBox {
        val queue = ArrayDeque<Long>()
        queue.add(start)
        visited.add(start)

        var minRow = unpackRow(start)
        var maxR = minRow
        var minCol = unpackCol(start)
        var maxC = minCol

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val r = unpackRow(current)
            val c = unpackCol(current)

            minRow = minOf(minRow, r)
            maxR = maxOf(maxR, r)
            minCol = minOf(minCol, c)
            maxC = maxOf(maxC, c)

            // 4-directional neighbors
            for ((dr, dc) in DIRECTIONS) {
                val nr = r + dr
                val nc = c + dc
                if (nr < 0 || nr >= maxRow || nc < 0 || nc >= maxCol) continue
                val neighbor = pack(nr, nc)
                if (neighbor in nonEmpty && neighbor !in visited) {
                    visited.add(neighbor)
                    queue.add(neighbor)
                }
            }
        }

        return BoundingBox(minRow, maxR, minCol, maxC)
    }

    /**
     * Merge bounding boxes whose row AND column ranges overlap.
     * Side-by-side tables with no column overlap are kept separate.
     */
    private fun mergeOverlapping(boxes: List<BoundingBox>): List<BoundingBox> {
        val sorted = boxes.sortedBy { it.minRow }
        val result = mutableListOf<BoundingBox>()
        var current = sorted[0]

        for (i in 1 until sorted.size) {
            val next = sorted[i]
            val rowOverlap = next.minRow <= current.maxRow + mergeGapThreshold
            val colOverlap = next.minCol <= current.maxCol + 1 && next.maxCol >= current.minCol - 1
            if (rowOverlap && colOverlap) {
                current = BoundingBox(
                    minOf(current.minRow, next.minRow),
                    maxOf(current.maxRow, next.maxRow),
                    minOf(current.minCol, next.minCol),
                    maxOf(current.maxCol, next.maxCol),
                )
            } else {
                result.add(current)
                current = next
            }
        }
        result.add(current)
        return result
    }

    private fun filterMergeRegions(
        allRegions: List<MergeRegion>,
        startRow: Int,
        endRow: Int,
        startCol: Int,
        endCol: Int,
    ): List<MergeRegion> {
        val rowCount = endRow - startRow + 1
        val colCount = endCol - startCol + 1
        return allRegions.filter { region ->
            region.firstRow <= endRow && region.lastRow >= startRow &&
                region.firstCol <= endCol && region.lastCol >= startCol
        }.map { region ->
            MergeRegion(
                firstRow = (region.firstRow - startRow).coerceAtLeast(0),
                lastRow = (region.lastRow - startRow).coerceAtMost(rowCount - 1),
                firstCol = (region.firstCol - startCol).coerceAtLeast(0),
                lastCol = (region.lastCol - startCol).coerceAtMost(colCount - 1),
            )
        }
    }

    private data class BoundingBox(
        val minRow: Int,
        val maxRow: Int,
        val minCol: Int,
        val maxCol: Int,
    )

    companion object {
        private val DIRECTIONS = arrayOf(
            intArrayOf(-1, 0), // up
            intArrayOf(1, 0),  // down
            intArrayOf(0, -1), // left
            intArrayOf(0, 1),  // right
        )

        /** Pack (row, col) into a single Long for fast hashing. */
        private fun pack(row: Int, col: Int): Long =
            (row.toLong() shl 32) or (col.toLong() and 0xFFFFFFFFL)

        private fun unpackRow(packed: Long): Int = (packed shr 32).toInt()
        private fun unpackCol(packed: Long): Int = (packed and 0xFFFFFFFFL).toInt()
    }
}
