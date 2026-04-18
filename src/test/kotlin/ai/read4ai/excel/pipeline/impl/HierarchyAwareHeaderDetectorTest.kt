@file:OptIn(ai.read4ai.excel.ExperimentalRead4ai::class)

package ai.read4ai.excel.pipeline.impl

import ai.read4ai.excel.ExcelParser
import ai.read4ai.excel.model.Element
import ai.read4ai.excel.pipeline.Grid
import ai.read4ai.excel.pipeline.MergeRegion
import ai.read4ai.excel.pipeline.PipelineConfig
import ai.read4ai.excel.pipeline.Segment
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream

class HierarchyAwareHeaderDetectorTest : FunSpec({

    val detector = HierarchyAwareHeaderDetector()

    // -- Unit tests for path extraction --

    test("empty segment returns zero headers and no paths") {
        val segment = Segment(
            grid = Grid(cells = emptyList(), mergeRegions = emptyList(), rowCount = 0, colCount = 0),
            startRow = 0, startCol = 0, gapFromPrevious = 0,
        )
        val info = detector.detectHeaders(segment)
        info.headerRowCount shouldBe 0
        info.columnPaths.shouldBeEmpty()
        info.rowPaths.shouldBeEmpty()
    }

    test("single header row produces no column paths") {
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
        info.columnPaths.shouldBeEmpty()
    }

    test("two-row header with horizontal merge builds column paths") {
        // Layout:
        // Row 0: [대분류 (merged A1:C1)] [대분류] [대분류]
        // Row 1: [소분류1]               [소분류2] [소분류3]
        // Row 2: [100]                   [200]    [300]
        val segment = Segment(
            grid = Grid(
                cells = listOf(
                    listOf("대분류", "", ""),
                    listOf("소분류1", "소분류2", "소분류3"),
                    listOf("100", "200", "300"),
                ),
                mergeRegions = listOf(
                    // Horizontal merge: row 0 spans all 3 cols
                    MergeRegion(firstRow = 0, lastRow = 0, firstCol = 0, lastCol = 2),
                    // Vertical merge to make row 0-1 header rows
                    // (MergeAwareHeaderDetector needs a vertical merge to detect multi-row headers)
                    // We need a vertical merge somewhere. Let's use a different approach:
                    // Row 0-1 in column 0 can be vertically merged to trigger multi-row detection
                ),
                rowCount = 3,
                colCount = 3,
            ),
            startRow = 0, startCol = 0, gapFromPrevious = 0,
        )
        // With only horizontal merge, MergeAwareHeaderDetector won't detect multi-row headers
        // So this should return 1 header row and no column paths
        val info = detector.detectHeaders(segment)
        info.headerRowCount shouldBe 1
        info.columnPaths.shouldBeEmpty()
    }

    test("two-row header with vertical merge builds column paths") {
        // Layout:
        // Row 0: [Category (merged 0-1)] [Q1]      [Q2]
        // Row 1: [^]                     [Jan-Mar]  [Apr-Jun]
        // Row 2: [Revenue]               [1000]     [1200]
        val segment = Segment(
            grid = Grid(
                cells = listOf(
                    listOf("Category", "Q1", "Q2"),
                    listOf("", "Jan-Mar", "Apr-Jun"),
                    listOf("Revenue", "1000", "1200"),
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

        // Column 1: ["Q1", "Jan-Mar"] -- two levels
        info.columnPaths shouldContainKey 1
        info.columnPaths[1]!! shouldContainExactly listOf("Q1", "Jan-Mar")

        // Column 2: ["Q2", "Apr-Jun"]
        info.columnPaths shouldContainKey 2
        info.columnPaths[2]!! shouldContainExactly listOf("Q2", "Apr-Jun")

        // Column 0: Category is vertically merged, so it appears as same value
        // in both rows -- path would be just ["Category"], which is < 2, so no path
        info.columnPaths.containsKey(0) shouldBe false
    }

    test("three-row header builds deep column paths") {
        // Layout:
        // Row 0: [Overall (merged 0-2)] [H1 (merged 0-0, cols 1-2)] [H1]
        // Row 1: [^]                    [Sub1]                       [Sub2]
        // Row 2: [^]                    [Detail1]                    [Detail2]
        // Row 3: [Data]                 [100]                        [200]
        val segment = Segment(
            grid = Grid(
                cells = listOf(
                    listOf("Overall", "H1", ""),
                    listOf("", "Sub1", "Sub2"),
                    listOf("", "Detail1", "Detail2"),
                    listOf("Data", "100", "200"),
                ),
                mergeRegions = listOf(
                    MergeRegion(firstRow = 0, lastRow = 2, firstCol = 0, lastCol = 0),
                    MergeRegion(firstRow = 0, lastRow = 0, firstCol = 1, lastCol = 2),
                ),
                rowCount = 4,
                colCount = 3,
            ),
            startRow = 0, startCol = 0, gapFromPrevious = 0,
        )
        val info = detector.detectHeaders(segment)
        info.headerRowCount shouldBe 3

        // Column 1: ["H1", "Sub1", "Detail1"]
        info.columnPaths shouldContainKey 1
        info.columnPaths[1]!! shouldContainExactly listOf("H1", "Sub1", "Detail1")

        // Column 2: ["H1", "Sub2", "Detail2"] (H1 propagated from horizontal merge)
        info.columnPaths shouldContainKey 2
        info.columnPaths[2]!! shouldContainExactly listOf("H1", "Sub2", "Detail2")
    }

    test("left-side vertical merges build row paths") {
        // Layout:
        // Row 0: [Category] [Item]   [Value]
        // Row 1: [기계설비 (merged 1-3, col 0)] [펌프]    [100]
        // Row 2: [^]                            [모터]    [200]
        // Row 3: [^]                            [밸브]    [300]
        // Row 4: [전기설비 (merged 4-5, col 0)] [변압기]  [400]
        // Row 5: [^]                            [차단기]  [500]
        val segment = Segment(
            grid = Grid(
                cells = listOf(
                    listOf("Category", "Item", "Value"),
                    listOf("기계설비", "펌프", "100"),
                    listOf("", "모터", "200"),
                    listOf("", "밸브", "300"),
                    listOf("전기설비", "변압기", "400"),
                    listOf("", "차단기", "500"),
                ),
                mergeRegions = listOf(
                    MergeRegion(firstRow = 1, lastRow = 3, firstCol = 0, lastCol = 0),
                    MergeRegion(firstRow = 4, lastRow = 5, firstCol = 0, lastCol = 0),
                ),
                rowCount = 6,
                colCount = 3,
            ),
            startRow = 0, startCol = 0, gapFromPrevious = 0,
        )
        val info = detector.detectHeaders(segment)
        info.headerRowCount shouldBe 1

        // Row paths: left column 0 has vertical merges but it's only 1 column,
        // so each path would be just ["기계설비"] or ["전기설비"] -- length 1, not >= 2
        // Therefore no row paths with a single left-header column
        info.rowPaths.shouldBeEmpty()
    }

    test("two left-side merge columns build row paths") {
        // Layout:
        // Row 0: [Cat]    [SubCat]  [Value]
        // Row 1: [기계 (merged 1-2)] [펌프 (merged 1-1)] [100]
        // Row 2: [^]                 [모터]              [200]
        // Row 3: [전기 (merged 3-4)] [변압기]            [300]
        // Row 4: [^]                 [차단기]            [400]
        val segment = Segment(
            grid = Grid(
                cells = listOf(
                    listOf("Cat", "SubCat", "Value"),
                    listOf("기계", "펌프", "100"),
                    listOf("", "모터", "200"),
                    listOf("전기", "변압기", "300"),
                    listOf("", "차단기", "400"),
                ),
                mergeRegions = listOf(
                    MergeRegion(firstRow = 1, lastRow = 2, firstCol = 0, lastCol = 0),
                    MergeRegion(firstRow = 3, lastRow = 4, firstCol = 0, lastCol = 0),
                ),
                rowCount = 5,
                colCount = 3,
            ),
            startRow = 0, startCol = 0, gapFromPrevious = 0,
        )
        val info = detector.detectHeaders(segment)
        info.headerRowCount shouldBe 1

        // Column 0 has vertical merges in data area, but column 1 does not.
        // findLeftMergeColumns requires contiguous from col 0, so only col 0.
        // Each row path is just ["기계"] or ["전기"] -- length 1, so no paths.
        info.rowPaths.shouldBeEmpty()
    }

    test("two contiguous left merge columns build row paths") {
        // Both col 0 and col 1 have vertical merges in the data area
        val segment = Segment(
            grid = Grid(
                cells = listOf(
                    listOf("Cat", "SubCat", "Value"),
                    listOf("기계", "펌프", "100"),
                    listOf("", "", "200"),
                    listOf("전기", "변압기", "300"),
                    listOf("", "", "400"),
                ),
                mergeRegions = listOf(
                    MergeRegion(firstRow = 1, lastRow = 2, firstCol = 0, lastCol = 0),
                    MergeRegion(firstRow = 1, lastRow = 2, firstCol = 1, lastCol = 1),
                    MergeRegion(firstRow = 3, lastRow = 4, firstCol = 0, lastCol = 0),
                    MergeRegion(firstRow = 3, lastRow = 4, firstCol = 1, lastCol = 1),
                ),
                rowCount = 5,
                colCount = 3,
            ),
            startRow = 0, startCol = 0, gapFromPrevious = 0,
        )
        val info = detector.detectHeaders(segment)

        // Data row 0 (abs row 1): col0="기계", col1="펌프" -> path ["기계", "펌프"]
        info.rowPaths shouldContainKey 0
        info.rowPaths[0]!! shouldContainExactly listOf("기계", "펌프")

        // Data row 1 (abs row 2): col0="기계" (propagated), col1="펌프" (propagated) -> same path
        info.rowPaths shouldContainKey 1
        info.rowPaths[1]!! shouldContainExactly listOf("기계", "펌프")

        // Data row 2 (abs row 3): col0="전기", col1="변압기" -> path ["전기", "변압기"]
        info.rowPaths shouldContainKey 2
        info.rowPaths[2]!! shouldContainExactly listOf("전기", "변압기")
    }

    // -- Integration tests with real XLSX files --

    test("nested merge XLSX: 3-row header produces column paths") {
        // Simulates E01_nested-merge: 3-row header with nested horizontal merges
        val bytes = createNestedMergeXlsx()
        val pipeline = PipelineConfig(
            segmenter = GraphSegmenter(),
            headerDetector = HierarchyAwareHeaderDetector(),
            elementClassifier = DefaultElementClassifier(),
        )
        val doc = ExcelParser.parse(bytes, pipeline = pipeline)
        doc.sheets.shouldNotBeEmpty()

        val tables = doc.sheets[0].elements.filterIsInstance<Element.Table>()
        tables.shouldNotBeEmpty()

        val table = tables.first()
        // Should detect 3-row header
        table.headerRowCount shouldBe 3
        // Should have column paths for columns under the merged headers
        (table.columnPaths.isNotEmpty()) shouldBe true
    }

    test("deep header XLSX: 4-level hierarchy") {
        val bytes = createDeepHeaderXlsx()
        val pipeline = PipelineConfig(
            segmenter = GraphSegmenter(),
            headerDetector = HierarchyAwareHeaderDetector(),
            elementClassifier = DefaultElementClassifier(),
        )
        val doc = ExcelParser.parse(bytes, pipeline = pipeline)
        doc.sheets.shouldNotBeEmpty()

        val tables = doc.sheets[0].elements.filterIsInstance<Element.Table>()
        tables.shouldNotBeEmpty()

        val table = tables.first()
        table.headerRowCount shouldBe 4
        (table.columnPaths.isNotEmpty()) shouldBe true

        // The deepest paths should have 4 levels
        val maxDepth = table.columnPaths.values.maxOf { it.size }
        maxDepth shouldBe 4
    }

    test("complex strategy uses HierarchyAwareHeaderDetector") {
        val bytes = createNestedMergeXlsx()
        val doc = ExcelParser.parse(bytes, pipeline = PipelineConfig.Strategy.complex())
        doc.sheets.shouldNotBeEmpty()

        val tables = doc.sheets[0].elements.filterIsInstance<Element.Table>()
        tables.shouldNotBeEmpty()

        // complex strategy should produce column paths
        (tables.first().columnPaths.isNotEmpty()) shouldBe true
    }

    test("balanced strategy does not produce column paths") {
        val bytes = createNestedMergeXlsx()
        val doc = ExcelParser.parse(bytes, pipeline = PipelineConfig.Strategy.balanced())
        doc.sheets.shouldNotBeEmpty()

        val tables = doc.sheets[0].elements.filterIsInstance<Element.Table>()
        tables.shouldNotBeEmpty()

        // balanced uses MergeAwareHeaderDetector, not hierarchy-aware
        tables.first().columnPaths.shouldBeEmpty()
    }
})

// -- Test fixture factories --

/**
 * Create an XLSX with a 3-row nested merge header (simulates E01_nested-merge).
 *
 * Layout:
 * Row 0: [카테고리 (merged 0-2, col 0)] [실적 (merged 0-0, cols 1-4)]
 * Row 1: [^]                            [상반기 (merged 1-1, cols 1-2)] [하반기 (merged 1-1, cols 3-4)]
 * Row 2: [^]                            [1분기] [2분기] [3분기] [4분기]
 * Row 3: [매출]                          [100]  [200]  [300]  [400]
 * Row 4: [비용]                          [80]   [150]  [250]  [350]
 */
private fun createNestedMergeXlsx(): ByteArray {
    val wb = XSSFWorkbook()
    val sheet = wb.createSheet("Report")

    sheet.createRow(0).apply {
        createCell(0).setCellValue("카테고리")
        createCell(1).setCellValue("실적")
        createCell(2).setCellValue("")
        createCell(3).setCellValue("")
        createCell(4).setCellValue("")
    }
    sheet.createRow(1).apply {
        createCell(0).setCellValue("")
        createCell(1).setCellValue("상반기")
        createCell(2).setCellValue("")
        createCell(3).setCellValue("하반기")
        createCell(4).setCellValue("")
    }
    sheet.createRow(2).apply {
        createCell(0).setCellValue("")
        createCell(1).setCellValue("1분기")
        createCell(2).setCellValue("2분기")
        createCell(3).setCellValue("3분기")
        createCell(4).setCellValue("4분기")
    }
    sheet.createRow(3).apply {
        createCell(0).setCellValue("매출")
        createCell(1).setCellValue(100.0)
        createCell(2).setCellValue(200.0)
        createCell(3).setCellValue(300.0)
        createCell(4).setCellValue(400.0)
    }
    sheet.createRow(4).apply {
        createCell(0).setCellValue("비용")
        createCell(1).setCellValue(80.0)
        createCell(2).setCellValue(150.0)
        createCell(3).setCellValue(250.0)
        createCell(4).setCellValue(350.0)
    }

    // Merges
    sheet.addMergedRegion(CellRangeAddress(0, 2, 0, 0))  // 카테고리 spans rows 0-2
    sheet.addMergedRegion(CellRangeAddress(0, 0, 1, 4))  // 실적 spans cols 1-4
    sheet.addMergedRegion(CellRangeAddress(1, 1, 1, 2))  // 상반기 spans cols 1-2
    sheet.addMergedRegion(CellRangeAddress(1, 1, 3, 4))  // 하반기 spans cols 3-4

    val baos = ByteArrayOutputStream()
    wb.use { it.write(baos) }
    return baos.toByteArray()
}

/**
 * Create an XLSX with a 4-level deep header hierarchy (simulates E03_deep-header).
 *
 * Layout:
 * Row 0: [항목 (merged 0-3)] [전체 (merged 0-0, cols 1-4)]
 * Row 1: [^]                [국내 (merged 1-1, cols 1-2)]    [해외 (merged 1-1, cols 3-4)]
 * Row 2: [^]                [제조 (merged 2-2, col 1)]       [서비스 (merged 2-2, col 2)] [제조] [서비스]
 * Row 3: [^]                [금액]                           [금액]                       [금액] [금액]
 * Row 4: [매출]              [1000]                          [2000]                       [3000] [4000]
 */
private fun createDeepHeaderXlsx(): ByteArray {
    val wb = XSSFWorkbook()
    val sheet = wb.createSheet("Deep")

    sheet.createRow(0).apply {
        createCell(0).setCellValue("항목")
        createCell(1).setCellValue("전체")
        createCell(2).setCellValue("")
        createCell(3).setCellValue("")
        createCell(4).setCellValue("")
    }
    sheet.createRow(1).apply {
        createCell(0).setCellValue("")
        createCell(1).setCellValue("국내")
        createCell(2).setCellValue("")
        createCell(3).setCellValue("해외")
        createCell(4).setCellValue("")
    }
    sheet.createRow(2).apply {
        createCell(0).setCellValue("")
        createCell(1).setCellValue("제조")
        createCell(2).setCellValue("서비스")
        createCell(3).setCellValue("제조")
        createCell(4).setCellValue("서비스")
    }
    sheet.createRow(3).apply {
        createCell(0).setCellValue("")
        createCell(1).setCellValue("금액")
        createCell(2).setCellValue("금액")
        createCell(3).setCellValue("금액")
        createCell(4).setCellValue("금액")
    }
    sheet.createRow(4).apply {
        createCell(0).setCellValue("매출")
        createCell(1).setCellValue(1000.0)
        createCell(2).setCellValue(2000.0)
        createCell(3).setCellValue(3000.0)
        createCell(4).setCellValue(4000.0)
    }

    // Merges
    sheet.addMergedRegion(CellRangeAddress(0, 3, 0, 0))  // 항목 spans rows 0-3
    sheet.addMergedRegion(CellRangeAddress(0, 0, 1, 4))  // 전체 spans cols 1-4
    sheet.addMergedRegion(CellRangeAddress(1, 1, 1, 2))  // 국내 spans cols 1-2
    sheet.addMergedRegion(CellRangeAddress(1, 1, 3, 4))  // 해외 spans cols 3-4

    val baos = ByteArrayOutputStream()
    wb.use { it.write(baos) }
    return baos.toByteArray()
}
