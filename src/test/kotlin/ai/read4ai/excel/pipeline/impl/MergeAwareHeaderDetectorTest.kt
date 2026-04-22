package ai.read4ai.excel.pipeline.impl

import ai.read4ai.excel.pipeline.Grid
import ai.read4ai.excel.pipeline.MergeRegion
import ai.read4ai.excel.pipeline.Segment
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
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
        info.columnPaths[1] shouldContainExactly listOf("H1", "Sub1", "Detail1")
        info.columnPaths[2] shouldContainExactly listOf("H2", "Sub2", "Detail2")
    }

    test("skips intro banner row before actual merged header") {
        val segment = Segment(
            grid = Grid(
                cells = listOf(
                    listOf("2025년 10월 수수료 안내", "<", "<", "<", "<"),
                    listOf("^", "^<", "^<", "^<", "^<"),
                    listOf("제품군", "계약/의무 6년", "", "계약/의무 5년", ""),
                    listOf("", "단품요금", "신규결합", "단품요금", "신규결합"),
                    listOf("정수기", "36900", "32900", "39900", "35900"),
                ),
                mergeRegions = listOf(
                    MergeRegion(firstRow = 0, lastRow = 0, firstCol = 0, lastCol = 4),
                    MergeRegion(firstRow = 2, lastRow = 3, firstCol = 0, lastCol = 0),
                    MergeRegion(firstRow = 2, lastRow = 2, firstCol = 1, lastCol = 2),
                    MergeRegion(firstRow = 2, lastRow = 2, firstCol = 3, lastCol = 4),
                ),
                rowCount = 5,
                colCount = 5,
            ),
            startRow = 0, startCol = 0, gapFromPrevious = 0,
        )
        val info = detector.detectHeaders(segment)
        info.headerRowCount shouldBe 2
        info.headerRows[0][0] shouldBe "제품군"
        info.headerRows[0][1] shouldBe "계약/의무 6년"
        info.headerRows[0][3] shouldBe "계약/의무 5년"
        info.columnPaths[1] shouldContainExactly listOf("계약/의무 6년", "단품요금")
        info.columnPaths[4] shouldContainExactly listOf("계약/의무 5년", "신규결합")
    }
})
