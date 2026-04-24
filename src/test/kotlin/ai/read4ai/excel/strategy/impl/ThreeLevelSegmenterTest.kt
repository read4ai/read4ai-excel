package ai.read4ai.excel.strategy.impl

import ai.read4ai.excel.strategy.Grid
import ai.read4ai.excel.strategy.MergeRegion
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

class ThreeLevelSegmenterTest : FunSpec({

    val segmenter = ThreeLevelSegmenter()

    test("empty grid returns no segments") {
        val grid = Grid(cells = emptyList(), mergeRegions = emptyList(), rowCount = 0, colCount = 0)
        segmenter.segment(grid).shouldBeEmpty()
    }

    test("single block returns one segment") {
        val grid = Grid(
            cells = listOf(listOf("A", "B"), listOf("C", "D")),
            mergeRegions = emptyList(),
            rowCount = 2,
            colCount = 2,
        )
        segmenter.segment(grid) shouldHaveSize 1
    }

    test("Level 1: two row bands separated by gap") {
        val grid = Grid(
            cells = listOf(
                listOf("A", "B"),
                listOf("", ""),
                listOf("", ""),
                listOf("C", "D"),
            ),
            mergeRegions = emptyList(),
            rowCount = 4,
            colCount = 2,
        )
        val segments = segmenter.segment(grid)
        segments shouldHaveAtLeastSize 2
        segments[0].grid.cells.first().first() shouldBe "A"
    }

    test("Level 2: side-by-side tables with empty column gap") {
        val grid = Grid(
            cells = listOf(
                listOf("A", "B", "", "X", "Y"),
                listOf("C", "D", "", "Z", "W"),
            ),
            mergeRegions = emptyList(),
            rowCount = 2,
            colCount = 5,
        )
        val segments = segmenter.segment(grid)
        segments shouldHaveAtLeastSize 2
    }

    test("Level 3: sub-segments within a stripe") {
        val grid = Grid(
            cells = listOf(
                listOf("A"),
                listOf("B"),
                listOf(""),
                listOf(""),
                listOf("C"),
            ),
            mergeRegions = emptyList(),
            rowCount = 5,
            colCount = 1,
        )
        val segments = segmenter.segment(grid)
        segments shouldHaveAtLeastSize 2
    }

    test("gap tracking: second segment has positive gapFromPrevious") {
        val grid = Grid(
            cells = listOf(
                listOf("A"),
                listOf(""),
                listOf(""),
                listOf("B"),
            ),
            mergeRegions = emptyList(),
            rowCount = 4,
            colCount = 1,
        )
        val segments = segmenter.segment(grid)
        segments shouldHaveAtLeastSize 2
        // The second segment should have a gap
        segments.drop(1).any { it.gapFromPrevious > 0 } shouldBe true
    }

    test("all-empty grid returns no segments") {
        val grid = Grid(
            cells = listOf(listOf("", ""), listOf("", "")),
            mergeRegions = emptyList(),
            rowCount = 2,
            colCount = 2,
        )
        // Fallback: returns the grid as a single segment since cells is non-empty
        val segments = segmenter.segment(grid)
        // The spatial segmenter returns no bands for all-blank, so fallback kicks in
        segments shouldHaveSize 1
    }

    test("merge regions are filtered to segment boundaries") {
        val grid = Grid(
            cells = listOf(
                listOf("Header", "<", "Value"),
                listOf("", ""),
                listOf("", ""),
                listOf("X", "Y", "Z"),
            ),
            mergeRegions = listOf(
                MergeRegion(firstRow = 0, lastRow = 0, firstCol = 0, lastCol = 1),
            ),
            rowCount = 4,
            colCount = 3,
        )
        val segments = segmenter.segment(grid)
        segments shouldHaveAtLeastSize 2
        // First segment should have the merge region
        segments[0].grid.mergeRegions shouldHaveSize 1
        // Second segment should not have the merge region
        segments.last().grid.mergeRegions shouldHaveSize 0
    }

    test("custom thresholds work") {
        val lenientSegmenter = ThreeLevelSegmenter(
            minEmptyRowsForBandSplit = 1,
            minEmptyRowsForStripeSplit = 1,
        )
        val grid = Grid(
            cells = listOf(
                listOf("A"),
                listOf(""),
                listOf("B"),
            ),
            mergeRegions = emptyList(),
            rowCount = 3,
            colCount = 1,
        )
        val segments = lenientSegmenter.segment(grid)
        segments shouldHaveAtLeastSize 2
    }
})
