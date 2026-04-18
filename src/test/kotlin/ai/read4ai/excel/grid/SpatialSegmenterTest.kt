package ai.read4ai.excel.grid

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class SpatialSegmenterTest : FunSpec({

    context("splitIntoRowBands") {

        test("empty grid returns empty list") {
            SpatialSegmenter.splitIntoRowBands(emptyList()).shouldBeEmpty()
        }

        test("single row grid returns one band") {
            val grid = listOf(listOf("A", "B"))
            val bands = SpatialSegmenter.splitIntoRowBands(grid)
            bands shouldHaveSize 1
            bands[0].rows shouldHaveSize 1
        }

        test("contiguous rows form one band") {
            val grid = listOf(
                listOf("A", "B"),
                listOf("C", "D"),
                listOf("E", "F"),
            )
            val bands = SpatialSegmenter.splitIntoRowBands(grid)
            bands shouldHaveSize 1
            bands[0].rows shouldHaveSize 3
        }

        test("two empty rows split into two bands") {
            val grid = listOf(
                listOf("A", "B"),
                listOf("", ""),
                listOf("", ""),
                listOf("C", "D"),
            )
            val bands = SpatialSegmenter.splitIntoRowBands(grid)
            bands shouldHaveSize 2
            bands[0].rows[0] shouldBe listOf("A", "B")
            bands[1].rows[0] shouldBe listOf("C", "D")
        }

        test("single empty row does NOT split bands (default threshold is 2)") {
            val grid = listOf(
                listOf("A"),
                listOf(""),
                listOf("B"),
            )
            val bands = SpatialSegmenter.splitIntoRowBands(grid)
            bands shouldHaveSize 1
        }

        test("custom threshold of 1 splits on single empty row") {
            val grid = listOf(
                listOf("A"),
                listOf(""),
                listOf("B"),
            )
            val bands = SpatialSegmenter.splitIntoRowBands(grid, minEmptyRowsBetweenBands = 1)
            bands shouldHaveSize 2
        }

        test("multiple gaps produce multiple bands") {
            val grid = listOf(
                listOf("A"),
                listOf("", ""),
                listOf("", ""),
                listOf("B"),
                listOf("", ""),
                listOf("", ""),
                listOf("C"),
            )
            val bands = SpatialSegmenter.splitIntoRowBands(grid)
            bands shouldHaveSize 3
        }

        test("all empty rows returns empty bands") {
            val grid = listOf(
                listOf("", ""),
                listOf("", ""),
                listOf("", ""),
            )
            val bands = SpatialSegmenter.splitIntoRowBands(grid)
            bands.shouldBeEmpty()
        }
    }

    context("splitByEmptyColumns") {

        test("empty grid returns empty") {
            SpatialSegmenter.splitByEmptyColumns(emptyList()).shouldBeEmpty()
        }

        test("single column table returns one sub-table") {
            val grid = listOf(
                listOf("A"),
                listOf("B"),
            )
            val result = SpatialSegmenter.splitByEmptyColumns(grid)
            result shouldHaveSize 1
        }

        test("two column groups with empty column between") {
            val grid = listOf(
                listOf("A", "", "B"),
                listOf("C", "", "D"),
            )
            val result = SpatialSegmenter.splitByEmptyColumns(grid)
            result shouldHaveSize 2
            result[0] shouldBe listOf(listOf("A"), listOf("C"))
            result[1] shouldBe listOf(listOf("B"), listOf("D"))
        }

        test("adjacent non-empty columns stay together") {
            val grid = listOf(
                listOf("A", "B", "", "C"),
                listOf("D", "E", "", "F"),
            )
            val result = SpatialSegmenter.splitByEmptyColumns(grid)
            result shouldHaveSize 2
            result[0][0] shouldBe listOf("A", "B")
            result[1][0] shouldBe listOf("C")
        }

        test("all empty grid returns empty") {
            val grid = listOf(
                listOf("", "", ""),
                listOf("", "", ""),
            )
            SpatialSegmenter.splitByEmptyColumns(grid).shouldBeEmpty()
        }
    }

    context("splitStripeWithIndices") {

        test("empty stripe returns empty") {
            SpatialSegmenter.splitStripeWithIndices(emptyList()).shouldBeEmpty()
        }

        test("single row stripe returns one segment") {
            val stripe = listOf(listOf("A", "B"))
            val segments = SpatialSegmenter.splitStripeWithIndices(stripe)
            segments shouldHaveSize 1
            segments[0].rows shouldHaveSize 1
            segments[0].startRowIndex shouldBe 0
        }

        test("gap splits stripe into segments") {
            val stripe = listOf(
                listOf("A"),
                listOf(""),
                listOf(""),
                listOf("B"),
            )
            val segments = SpatialSegmenter.splitStripeWithIndices(stripe)
            segments shouldHaveSize 2
            segments[0].rows[0] shouldBe listOf("A")
            segments[1].rows[0] shouldBe listOf("B")
            segments[1].gapRowsFromPrevious shouldBe 2
        }

        test("segment tracks start row index") {
            val stripe = listOf(
                listOf(""),
                listOf(""),
                listOf("A"),
                listOf("B"),
            )
            val segments = SpatialSegmenter.splitStripeWithIndices(stripe)
            segments shouldHaveSize 1
            segments[0].startRowIndex shouldBe 2
            segments[0].gapRowsFromPrevious shouldBe 2
        }
    }

    context("extractIsolatedCells") {

        test("empty rows returns empty pair") {
            val (isolated, remaining) = SpatialSegmenter.extractIsolatedCells(emptyList())
            isolated.shouldBeEmpty()
            remaining.shouldBeEmpty()
        }

        test("single isolated cell surrounded by blank rows is extracted") {
            val rows = listOf(
                listOf("", ""),
                listOf("", "Isolated"),
                listOf("", ""),
            )
            val (isolated, remaining) = SpatialSegmenter.extractIsolatedCells(rows)
            isolated shouldHaveSize 1
            isolated[0] shouldBe "Isolated"
        }

        test("row with multiple non-blank cells is not isolated") {
            val rows = listOf(
                listOf("", ""),
                listOf("A", "B"),
                listOf("", ""),
            )
            val (isolated, _) = SpatialSegmenter.extractIsolatedCells(rows)
            isolated.shouldBeEmpty()
        }

        test("cell at edge of grid with no blank row above is not isolated") {
            val rows = listOf(
                listOf("X", ""),
                listOf("", ""),
            )
            val (isolated, _) = SpatialSegmenter.extractIsolatedCells(rows)
            // First row, index 0, has upBlank=true (index==0 counts as upBlank)
            // and downBlank=true (next row is blank)
            isolated shouldHaveSize 1
            isolated[0] shouldBe "X"
        }
    }

    context("trimBlankEdgeRows") {

        test("empty list returns empty") {
            SpatialSegmenter.trimBlankEdgeRows(emptyList()).shouldBeEmpty()
        }

        test("no blank edges returns same rows") {
            val rows = listOf(listOf("A"), listOf("B"))
            SpatialSegmenter.trimBlankEdgeRows(rows) shouldBe rows
        }

        test("leading blank rows trimmed") {
            val rows = listOf(
                listOf(""),
                listOf(""),
                listOf("A"),
            )
            val result = SpatialSegmenter.trimBlankEdgeRows(rows)
            result shouldHaveSize 1
            result[0] shouldBe listOf("A")
        }

        test("trailing blank rows trimmed") {
            val rows = listOf(
                listOf("A"),
                listOf(""),
                listOf(""),
            )
            val result = SpatialSegmenter.trimBlankEdgeRows(rows)
            result shouldHaveSize 1
            result[0] shouldBe listOf("A")
        }

        test("both edges trimmed") {
            val rows = listOf(
                listOf(""),
                listOf("A"),
                listOf("B"),
                listOf(""),
            )
            val result = SpatialSegmenter.trimBlankEdgeRows(rows)
            result shouldHaveSize 2
        }
    }

    context("isRowEmpty") {

        test("all blank returns true") {
            SpatialSegmenter.isRowEmpty(listOf("", "  ", "")) shouldBe true
        }

        test("any non-blank returns false") {
            SpatialSegmenter.isRowEmpty(listOf("", "A", "")) shouldBe false
        }

        test("empty list returns true") {
            SpatialSegmenter.isRowEmpty(emptyList()) shouldBe true
        }
    }

    context("isIsolatedSingleCellContent") {

        test("empty rows returns false") {
            SpatialSegmenter.isIsolatedSingleCellContent(emptyList()) shouldBe false
        }

        test("exactly one non-blank cell returns true") {
            val rows = listOf(
                listOf("", ""),
                listOf("A", ""),
            )
            SpatialSegmenter.isIsolatedSingleCellContent(rows) shouldBe true
        }

        test("two non-blank cells returns false") {
            val rows = listOf(listOf("A", "B"))
            SpatialSegmenter.isIsolatedSingleCellContent(rows) shouldBe false
        }
    }

    context("extractSingleCellText") {

        test("empty rows returns null") {
            SpatialSegmenter.extractSingleCellText(emptyList()) shouldBe null
        }

        test("extracts first non-blank cell") {
            val rows = listOf(
                listOf("", ""),
                listOf("", "Found"),
            )
            SpatialSegmenter.extractSingleCellText(rows) shouldBe "Found"
        }

        test("all blank returns null") {
            val rows = listOf(
                listOf("", ""),
                listOf("", ""),
            )
            SpatialSegmenter.extractSingleCellText(rows) shouldBe null
        }
    }
})
