package ai.read4ai.excel.pipeline.impl

import ai.read4ai.excel.pipeline.Grid
import ai.read4ai.excel.pipeline.MergeRegion
import ai.read4ai.excel.pipeline.Segment
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class MergeAwareHeaderDetectorTest : FunSpec({

    val detector = MergeAwareHeaderDetector()

    test("empty segment returns zero headers") {
        val segment = Segment(
            grid = Grid(cells = emptyList(), mergeRegions = emptyList(), rowCount = 0, colCount = 0),
            startRow = 0, startCol = 0, gapFromPrevious = 0,
        )
        val info = detector.detectHeaders(segment)
        info.headerRowCount shouldBe 0
        info.headerRows shouldHaveSize 0
    }

    test("no merges: single header row") {
        val segment = Segment(
            grid = Grid(
                cells = listOf(
                    listOf("Name", "Age"),
                    listOf("Alice", "30"),
                ),
                mergeRegions = emptyList(),
                rowCount = 2,
                colCount = 2,
            ),
            startRow = 0, startCol = 0, gapFromPrevious = 0,
        )
        val info = detector.detectHeaders(segment)
        info.headerRowCount shouldBe 1
        info.headerRows[0] shouldBe listOf("Name", "Age")
    }

    test("vertical merge on first row expands header to multiple rows") {
        val segment = Segment(
            grid = Grid(
                cells = listOf(
                    listOf("Category", "Q1", "Q2"),
                    listOf("^", "Jan", "Apr"),
                    listOf("Revenue", "100", "200"),
                ),
                mergeRegions = listOf(
                    MergeRegion(firstRow = 0, lastRow = 1, firstCol = 0, lastCol = 0),
                ),
                rowCount = 3,
                colCount = 3,
            ),
            startRow = 0, startCol = 0, gapFromPrevious = 0,
        )
        val info = detector.detectHeaders(segment)
        info.headerRowCount shouldBe 2
        info.headerRows shouldHaveSize 2
        info.headerRows[0][0] shouldBe "Category"
        info.headerRows[1][1] shouldBe "Jan"
    }

    test("empty header cells get placeholder names") {
        val segment = Segment(
            grid = Grid(
                cells = listOf(
                    listOf("Name", "", ""),
                    listOf("Alice", "30", "Seoul"),
                ),
                mergeRegions = emptyList(),
                rowCount = 2,
                colCount = 3,
            ),
            startRow = 0, startCol = 0, gapFromPrevious = 0,
        )
        val info = detector.detectHeaders(segment)
        info.headerRowCount shouldBe 1
        info.headerRows[0][0] shouldBe "Name"
        info.headerRows[0][1] shouldContain "header_"
        info.headerRows[0][2] shouldContain "header_"
    }

    test("all-empty rows return zero headers") {
        val segment = Segment(
            grid = Grid(
                cells = listOf(listOf("", ""), listOf("", "")),
                mergeRegions = emptyList(),
                rowCount = 2,
                colCount = 2,
            ),
            startRow = 0, startCol = 0, gapFromPrevious = 0,
        )
        val info = detector.detectHeaders(segment)
        info.headerRowCount shouldBe 0
    }

    test("three-row merged header") {
        val segment = Segment(
            grid = Grid(
                cells = listOf(
                    listOf("Overall", "H1", "H2"),
                    listOf("^", "Sub1", "Sub2"),
                    listOf("^", "Detail1", "Detail2"),
                    listOf("Data", "100", "200"),
                ),
                mergeRegions = listOf(
                    MergeRegion(firstRow = 0, lastRow = 2, firstCol = 0, lastCol = 0),
                ),
                rowCount = 4,
                colCount = 3,
            ),
            startRow = 0, startCol = 0, gapFromPrevious = 0,
        )
        val info = detector.detectHeaders(segment)
        info.headerRowCount shouldBe 3
        info.headerRows shouldHaveSize 3
    }
})
