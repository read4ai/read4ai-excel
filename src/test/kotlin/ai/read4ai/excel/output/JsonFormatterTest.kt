package ai.read4ai.excel.output

import ai.read4ai.excel.ExperimentalRead4ai
import ai.read4ai.excel.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.types.shouldBeInstanceOf

class JsonFormatterTest : FunSpec({

    fun sampleDocument(): ExcelDocument = ExcelDocument(
        fileName = "test.xlsx",
        numberOfSheets = 1,
        sheets = listOf(
            Sheet(
                sheetIndex = 0,
                sheetName = "Sheet1",
                elements = listOf(
                    Element.Heading(text = "Report Title", level = 2),
                    Element.Table(
                        rows = listOf(
                            Row(rowIndex = 0, cells = listOf(Cell("Name"), Cell("Age"))),
                            Row(rowIndex = 1, cells = listOf(Cell("Alice"), Cell("30"))),
                        ),
                        headerRowCount = 1,
                    ),
                    Element.Text(text = "Some note"),
                    Element.Note(text = "Footer note"),
                    Element.Image(base64 = null, mimeType = "image/png", description = "A chart"),
                ),
            ),
        ),
    )

    test("JsonFormatter implements DocumentFormatter") {
        val writer = JsonFormatter()
        writer.shouldBeInstanceOf<DocumentFormatter>()
    }

    test("write() produces valid non-blank output") {
        val json = JsonFormatter().format(sampleDocument())
        json.shouldNotBeBlank()
    }

    test("write() contains sheet name") {
        val json = JsonFormatter().format(sampleDocument())
        json shouldContain "Sheet1"
    }

    test("write() contains element content") {
        val json = JsonFormatter().format(sampleDocument())
        json shouldContain "Report Title"
        json shouldContain "Alice"
        json shouldContain "Some note"
    }

    test("ROW_OBJECT layout produces row records") {
        @OptIn(ExperimentalRead4ai::class)
        val json = JsonFormatter(Layout.ROW_OBJECT).format(sampleDocument())
        json shouldContain "\"row\""
        json shouldContain "\"cells\""
    }

    test("compact layout attaches nearby heading as table section") {
        val doc = ExcelDocument(
            fileName = "sections.xlsx",
            numberOfSheets = 1,
            sheets = listOf(
                Sheet(
                    sheetIndex = 0,
                    sheetName = "Sections",
                    elements = listOf(
                        Element.Heading(text = "TV 지원 어플", startRow = 4, startCol = 1),
                        Element.Table(
                            rows = listOf(
                                Row(rowIndex = 0, cells = listOf(Cell("Platform"), Cell("YouTube"))),
                                Row(rowIndex = 1, cells = listOf(Cell("Web OS"), Cell("O"))),
                            ),
                            headerRowCount = 1,
                            startRow = 6,
                            startCol = 1,
                        ),
                    ),
                ),
            ),
        )

        val json = JsonFormatter().format(doc)
        json shouldContain "\"section\""
        json shouldContain "\"text\" : \"TV 지원 어플\""
        json shouldContain "\"cell\" : \"B5\""
        json shouldContain "\"sectionHeaderCells\""
        json shouldContain "\"TV 지원 어플 > Platform\" : \"B7\""
        json shouldContain "\"TV 지원 어플 > YouTube\" : \"C7\""
    }

    test("compact layout omits sectionHeaderCells for very wide sectioned tables") {
        val headers = (1..81).map { Cell("H$it") }
        val values = (1..81).map { Cell("V$it") }
        val doc = ExcelDocument(
            fileName = "wide-section.xlsx",
            numberOfSheets = 1,
            sheets = listOf(
                Sheet(
                    sheetIndex = 0,
                    sheetName = "Wide",
                    elements = listOf(
                        Element.Heading(text = "Large section", startRow = 0, startCol = 0),
                        Element.Table(
                            rows = listOf(
                                Row(rowIndex = 0, cells = headers),
                                Row(rowIndex = 1, cells = values),
                            ),
                            headerRowCount = 1,
                            startRow = 1,
                            startCol = 0,
                        ),
                    ),
                ),
            ),
        )

        val json = JsonFormatter().format(doc)
        json shouldContain "\"section\""
        json shouldContain "\"columns\""
        json.contains("\"sectionHeaderCells\"") shouldBe false
    }

    test("compact layout includes resolvedHeaders for multi-row tables") {
        val doc = ExcelDocument(
            fileName = "headers.xlsx",
            numberOfSheets = 1,
            sheets = listOf(
                Sheet(
                    sheetIndex = 0,
                    sheetName = "Report",
                    elements = listOf(
                        Element.Table(
                            rows = listOf(
                                Row(rowIndex = 0, cells = listOf(Cell("구분"), Cell("Q1", mergedRight = 1), Cell("^<"), Cell("Q2", mergedRight = 1), Cell("^<"))),
                                Row(rowIndex = 1, cells = listOf(Cell("^"), Cell("매출"), Cell("이익"), Cell("매출"), Cell("이익"))),
                                Row(rowIndex = 2, cells = listOf(Cell("전자"), Cell("100"), Cell("10"), Cell("120"), Cell("12"))),
                            ),
                            headerRowCount = 2,
                            columnPaths = mapOf(
                                1 to listOf("Q1", "매출"),
                                2 to listOf("Q1", "이익"),
                                3 to listOf("Q2", "매출"),
                                4 to listOf("Q2", "이익"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val json = JsonFormatter().format(doc)
        json shouldContain "\"resolvedHeaders\""
        json shouldContain "\"range\" : \"A1:G3\""
        json shouldContain "\"endRow\" : 3"
        json shouldContain "\"endCol\" : 7"
        json shouldContain "\"headerEndRow\" : 2"
        json shouldContain "\"bodyStartRow\" : 3"
        json shouldContain "\"2\" : \"Q1 > 매출\""
        json shouldContain "\"5\" : \"Q2 > 이익\""
        json shouldContain "\"headerCells\""
        json shouldContain "\"2\" : \"B2\""
        json shouldContain "\"5\" : \"E2\""
    }

    test("compact layout cleans merge placeholders out of columnPaths and resolvedHeaders") {
        val doc = ExcelDocument(
            fileName = "placeholder-paths.xlsx",
            numberOfSheets = 1,
            sheets = listOf(
                Sheet(
                    sheetIndex = 0,
                    sheetName = "Report",
                    elements = listOf(
                        Element.Table(
                            rows = listOf(
                                Row(rowIndex = 0, cells = listOf(Cell("제품"), Cell("Netflix"), Cell("Wavve"))),
                                Row(rowIndex = 1, cells = listOf(Cell("TV"), Cell("O"), Cell("X"))),
                            ),
                            headerRowCount = 1,
                            columnPaths = mapOf(
                                1 to listOf("<", "Netflix"),
                                2 to listOf("^", "Wavve"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val json = JsonFormatter().format(doc)
        json shouldContain "\"columnPaths\""
        json shouldContain "\"2\" : [ \"Netflix\" ]"
        json shouldContain "\"3\" : [ \"Wavve\" ]"
        json shouldContain "\"2\" : \"Netflix\""
        json shouldContain "\"3\" : \"Wavve\""
        json.contains("< > Netflix") shouldBe false
    }

    test("compact layout uses absolute sheet columns for columnPaths on offset tables") {
        val doc = ExcelDocument(
            fileName = "offset-paths.xlsx",
            numberOfSheets = 1,
            sheets = listOf(
                Sheet(
                    sheetIndex = 0,
                    sheetName = "Offset",
                    elements = listOf(
                        Element.Table(
                            rows = listOf(
                                Row(rowIndex = 0, cells = listOf(Cell("Platform"), Cell("모델"), Cell("Apps", mergedRight = 1), Cell("<"))),
                                Row(rowIndex = 1, cells = listOf(Cell("^"), Cell("^"), Cell("YouTube"), Cell("Netflix"))),
                                Row(rowIndex = 2, cells = listOf(Cell("Web OS"), Cell("UT"), Cell("O"), Cell("X"))),
                            ),
                            headerRowCount = 2,
                            startCol = 1,
                            columnPaths = mapOf(
                                2 to listOf("Apps", "YouTube"),
                                3 to listOf("Apps", "Netflix"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val json = JsonFormatter().format(doc)
        json shouldContain "\"columnPaths\""
        json shouldContain "\"4\" : [ \"Apps\", \"YouTube\" ]"
        json shouldContain "\"5\" : [ \"Apps\", \"Netflix\" ]"
        json shouldContain "\"4\" : \"Apps > YouTube\""
        json shouldContain "\"5\" : \"Apps > Netflix\""
        json shouldContain "\"4\" : \"D2\""
        json shouldContain "\"5\" : \"E2\""
    }

    test("compact layout includes column metadata for offset and multi-row tables") {
        val doc = ExcelDocument(
            fileName = "column-metadata.xlsx",
            numberOfSheets = 1,
            sheets = listOf(
                Sheet(
                    sheetIndex = 0,
                    sheetName = "Columns",
                    elements = listOf(
                        Element.Table(
                            rows = listOf(
                                Row(rowIndex = 0, cells = listOf(Cell("Platform"), Cell("모델"), Cell("Apps", mergedRight = 1), Cell("<"))),
                                Row(rowIndex = 1, cells = listOf(Cell("^"), Cell("^"), Cell("YouTube"), Cell("Netflix"))),
                                Row(rowIndex = 2, cells = listOf(Cell("Web OS"), Cell("UT"), Cell("O"), Cell("X"))),
                            ),
                            headerRowCount = 2,
                            startCol = 1,
                            columnPaths = mapOf(
                                2 to listOf("Apps", "YouTube"),
                                3 to listOf("Apps", "Netflix"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val json = JsonFormatter().format(doc)
        json shouldContain "\"columns\""
        json shouldContain "\"index\" : 4"
        json shouldContain "\"letter\" : \"D\""
        json shouldContain "\"header\" : \"Apps > YouTube\""
        json shouldContain "\"headerCell\" : \"D2\""
        json shouldContain "\"index\" : 5"
        json shouldContain "\"letter\" : \"E\""
        json shouldContain "\"header\" : \"Apps > Netflix\""
        json shouldContain "\"headerCell\" : \"E2\""
    }

    test("compact layout includes matrixRows for wide binary support matrices") {
        val doc = ExcelDocument(
            fileName = "support-matrix.xlsx",
            numberOfSheets = 1,
            sheets = listOf(
                Sheet(
                    sheetIndex = 0,
                    sheetName = "Support",
                    elements = listOf(
                        Element.Table(
                            rows = listOf(
                                Row(rowIndex = 0, cells = listOf(Cell("Platform"), Cell("Model"), Cell("Apps", mergedRight = 2), Cell("<"), Cell("<"))),
                                Row(rowIndex = 1, cells = listOf(Cell("^"), Cell("^"), Cell("YouTube"), Cell("Netflix"), Cell("TikTok"))),
                                Row(rowIndex = 2, cells = listOf(Cell("Web OS 23"), Cell("UR"), Cell("O"), Cell("O"), Cell("O"))),
                                Row(rowIndex = 3, cells = listOf(Cell("Web OS 24"), Cell("UT"), Cell("O"), Cell("X"), Cell("X"))),
                            ),
                            headerRowCount = 2,
                            startCol = 1,
                            columnPaths = mapOf(
                                2 to listOf("Apps", "YouTube"),
                                3 to listOf("Apps", "Netflix"),
                                4 to listOf("Apps", "TikTok"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val json = JsonFormatter().format(doc)
        json shouldContain "\"matrixRows\""
        json shouldContain "\"row\" : 3"
        json shouldContain "\"key\" : \"Web OS 23 / UR\""
        json shouldContain "\"Apps > YouTube\" : \"O\""
        json shouldContain "\"Apps > Netflix\" : \"O\""
        json shouldContain "\"Apps > TikTok\" : \"O\""
        json shouldContain "\"row\" : 4"
        json shouldContain "\"key\" : \"Web OS 24 / UT\""
        json shouldContain "\"Apps > Netflix\" : \"X\""
        json shouldContain "\"Apps > TikTok\" : \"X\""
        json shouldContain "\"groups\""
        json shouldContain "\"O\" : [ \"Apps > YouTube\" ]"
        json shouldContain "\"X\" : [ \"Apps > Netflix\", \"Apps > TikTok\" ]"
        json shouldContain "\"matrixTransitions\""
        json shouldContain "\"fromKey\" : \"Web OS 23 / UR\""
        json shouldContain "\"toKey\" : \"Web OS 24 / UT\""
        json shouldContain "\"O->X\" : [ \"Apps > Netflix\", \"Apps > TikTok\" ]"
    }

    test("compact layout omits prompt for simple single-header sheets") {
        val json = JsonFormatter().format(sampleDocument())
        json.contains("\"prompt\"") shouldBe false
    }

    test("compact layout omits column metadata for simple narrow tables") {
        val json = JsonFormatter().format(sampleDocument())
        json.contains("\"columns\"") shouldBe false
    }

    test("compact layout omits matrixRows for simple narrow tables") {
        val json = JsonFormatter().format(sampleDocument())
        json.contains("\"matrixRows\"") shouldBe false
    }

    test("compact layout omits matrixTransitions for simple narrow tables") {
        val json = JsonFormatter().format(sampleDocument())
        json.contains("\"matrixTransitions\"") shouldBe false
    }

    test("compact layout includes cell coordinates for headings and text") {
        val doc = ExcelDocument(
            fileName = "coords.xlsx",
            numberOfSheets = 1,
            sheets = listOf(
                Sheet(
                    sheetIndex = 0,
                    sheetName = "Coords",
                    elements = listOf(
                        Element.Heading(text = "제목", startRow = 4, startCol = 1),
                        Element.Text(text = "안내", startRow = 9, startCol = 3),
                    ),
                ),
            ),
        )

        val json = JsonFormatter().format(doc)
        json shouldContain "\"cell\" : \"B5\""
        json shouldContain "\"cell\" : \"D10\""
    }

    test("compact layout includes row identity for single-column tables") {
        val doc = ExcelDocument(
            fileName = "row-identity.xlsx",
            numberOfSheets = 1,
            sheets = listOf(
                Sheet(
                    sheetIndex = 0,
                    sheetName = "Rows",
                    elements = listOf(
                        Element.Table(
                            rows = listOf(
                                Row(rowIndex = 0, cells = listOf(Cell("Code"))),
                                Row(rowIndex = 3, cells = listOf(Cell("A-100"))),
                                Row(rowIndex = 4, cells = listOf(Cell("B-200"))),
                            ),
                            headerRowCount = 1,
                            startRow = 9,
                            startCol = 1,
                        ),
                    ),
                ),
            ),
        )

        val json = JsonFormatter().format(doc)
        json shouldContain "\"rowNumbers\" : [ 10, 13, 14 ]"
        json shouldContain "\"rowAnchors\""
        json shouldContain "\"row\" : 13"
        json shouldContain "\"cell\" : \"B13\""
        json shouldContain "\"label\" : \"A-100\""
    }

    test("compact layout describes leading blank columns") {
        val doc = ExcelDocument(
            fileName = "offset-table.xlsx",
            numberOfSheets = 1,
            sheets = listOf(
                Sheet(
                    sheetIndex = 0,
                    sheetName = "Offset",
                    elements = listOf(
                        Element.Table(
                            rows = listOf(
                                Row(rowIndex = 0, cells = listOf(Cell("Code"))),
                                Row(rowIndex = 1, cells = listOf(Cell("A-100"))),
                            ),
                            headerRowCount = 1,
                            startCol = 1,
                        ),
                    ),
                ),
            ),
        )

        val json = JsonFormatter().format(doc)
        json shouldContain "\"range\" : \"B1:B2\""
        json shouldContain "\"colCount\" : 1"
        json shouldContain "\"leadingBlankColCount\" : 1"
        json shouldContain "\"sheetColCount\" : 2"
    }

    test("compact layout includes row identity for narrow vertically merged tables") {
        val doc = ExcelDocument(
            fileName = "merged-row-identity.xlsx",
            numberOfSheets = 1,
            sheets = listOf(
                Sheet(
                    sheetIndex = 0,
                    sheetName = "MergedRows",
                    elements = listOf(
                        Element.Table(
                            rows = listOf(
                                Row(rowIndex = 0, cells = listOf(Cell("Group"), Cell("Model"), Cell("Part"))),
                                Row(rowIndex = 1, cells = listOf(Cell("42", mergedDown = 1), Cell("42LB5600"), Cell("P1"))),
                                Row(rowIndex = 2, cells = listOf(Cell("^"), Cell("42LF5600"), Cell(""))),
                            ),
                            headerRowCount = 1,
                            startRow = 4,
                            startCol = 1,
                        ),
                    ),
                ),
            ),
        )

        val json = JsonFormatter().format(doc)
        json shouldContain "\"rowNumbers\" : [ 5, 6, 7 ]"
        json shouldContain "\"label\" : \"42LF5600\""
    }

    test("compact layout omits row identity for ordinary two-column tables") {
        val doc = ExcelDocument(
            fileName = "ordinary.xlsx",
            numberOfSheets = 1,
            sheets = listOf(
                Sheet(
                    sheetIndex = 0,
                    sheetName = "Ordinary",
                    elements = listOf(
                        Element.Table(
                            rows = listOf(
                                Row(rowIndex = 0, cells = listOf(Cell("Code"), Cell("Value"))),
                                Row(rowIndex = 1, cells = listOf(Cell("A-100"), Cell("ready"))),
                            ),
                            headerRowCount = 1,
                        ),
                    ),
                ),
            ),
        )

        val json = JsonFormatter().format(doc)
        json.contains("\"rowNumbers\"") shouldBe false
        json.contains("\"rowAnchors\"") shouldBe false
    }

    test("compact layout omits row identity for large tables") {
        val rows = (0..200).map { rowIndex ->
            Row(rowIndex = rowIndex, cells = listOf(Cell("R$rowIndex"), Cell("V$rowIndex")))
        }
        val doc = ExcelDocument(
            fileName = "large.xlsx",
            numberOfSheets = 1,
            sheets = listOf(
                Sheet(
                    sheetIndex = 0,
                    sheetName = "Large",
                    elements = listOf(Element.Table(rows = rows, headerRowCount = 1)),
                ),
            ),
        )

        val json = JsonFormatter().format(doc)
        json.contains("\"rowNumbers\"") shouldBe false
        json.contains("\"rowAnchors\"") shouldBe false
    }

    test("compact layout omits row identity for wide tables") {
        val row = Row(rowIndex = 0, cells = (1..13).map { Cell("C$it") })
        val doc = ExcelDocument(
            fileName = "wide.xlsx",
            numberOfSheets = 1,
            sheets = listOf(
                Sheet(
                    sheetIndex = 0,
                    sheetName = "Wide",
                    elements = listOf(
                        Element.Table(
                            rows = listOf(row, Row(rowIndex = 1, cells = (1..13).map { Cell("V$it") })),
                            headerRowCount = 1,
                        ),
                    ),
                ),
            ),
        )

        val json = JsonFormatter().format(doc)
        json.contains("\"rowNumbers\"") shouldBe false
        json.contains("\"rowAnchors\"") shouldBe false
    }

    test("compact layout includes mergedRanges for merged cells") {
        val doc = ExcelDocument(
            fileName = "merge-table.xlsx",
            numberOfSheets = 1,
            sheets = listOf(
                Sheet(
                    sheetIndex = 0,
                    sheetName = "Merged",
                    elements = listOf(
                        Element.Table(
                            rows = listOf(
                                Row(
                                    rowIndex = 0,
                                    cells = listOf(Cell("Header", mergedRight = 1, mergedDown = 1), Cell("<")),
                                ),
                                Row(
                                    rowIndex = 1,
                                    cells = listOf(Cell("^"), Cell("^<")),
                                ),
                            ),
                            headerRowCount = 1,
                            startRow = 4,
                            startCol = 1,
                        ),
                    ),
                ),
            ),
        )

        val json = JsonFormatter().format(doc)
        json shouldContain "\"mergedRanges\""
        json shouldContain "\"mergedRangeDetails\""
        json shouldContain "B5:C6"
        json shouldContain "\"value\" : \"Header\""
    }

    test("compact layout skips resolvedHeaders for banner-like top rows") {
        val doc = ExcelDocument(
            fileName = "banner.xlsx",
            numberOfSheets = 1,
            sheets = listOf(
                Sheet(
                    sheetIndex = 0,
                    sheetName = "Banner",
                    elements = listOf(
                        Element.Table(
                            rows = listOf(
                                Row(rowIndex = 0, cells = listOf(Cell("긴 안내 문구", mergedRight = 3))),
                                Row(rowIndex = 1, cells = listOf(Cell(""), Cell(""), Cell(""), Cell(""))),
                                Row(rowIndex = 2, cells = listOf(Cell("A"), Cell("B"), Cell("C"), Cell("D"))),
                            ),
                            headerRowCount = 2,
                        ),
                    ),
                ),
            ),
        )

        val json = JsonFormatter().format(doc)
        json.contains("\"resolvedHeaders\"") shouldBe false
    }

    test("toRawJson produces valid non-blank output") {
        val json = JsonFormatter.toRawJson(sampleDocument())
        json.shouldNotBeBlank()
    }

    test("toRawJson contains fileName") {
        val json = JsonFormatter.toRawJson(sampleDocument())
        json shouldContain "test.xlsx"
    }

    test("toRawPrettyJson contains indentation") {
        val json = JsonFormatter.toRawPrettyJson(sampleDocument())
        json shouldContain "\n"
        json shouldContain "  "
    }

    test("round-trip: toRawJson then fromJson produces equal document") {
        val original = sampleDocument()
        val json = JsonFormatter.toRawJson(original)
        val deserialized = JsonFormatter.fromJson(json)
        deserialized shouldBe original
    }

    test("round-trip with pretty JSON") {
        val original = sampleDocument()
        val json = JsonFormatter.toRawPrettyJson(original)
        val deserialized = JsonFormatter.fromJson(json)
        deserialized shouldBe original
    }

    test("empty document serializes and deserializes") {
        val empty = ExcelDocument(
            fileName = null,
            numberOfSheets = 0,
            sheets = emptyList(),
        )
        val json = JsonFormatter.toRawJson(empty)
        val deserialized = JsonFormatter.fromJson(json)
        deserialized shouldBe empty
    }

    test("document with merged cell info survives round-trip") {
        val doc = ExcelDocument(
            fileName = "merge.xlsx",
            numberOfSheets = 1,
            sheets = listOf(
                Sheet(
                    sheetIndex = 0,
                    sheetName = "Merged",
                    elements = listOf(
                        Element.Table(
                            rows = listOf(
                                Row(
                                    rowIndex = 0,
                                    cells = listOf(Cell("Header", mergedRight = 2, mergedDown = 0)),
                                ),
                            ),
                            headerRowCount = 1,
                        ),
                    ),
                ),
            ),
        )
        val json = JsonFormatter.toRawJson(doc)
        val roundTripped = JsonFormatter.fromJson(json)
        val cell = (roundTripped.sheets[0].elements[0] as Element.Table).rows[0].cells[0]
        cell.mergedRight shouldBe 2
        cell.mergedDown shouldBe 0
    }

    test("mergeRegions array is serialized at sheet level") {
        val doc = ExcelDocument(
            fileName = "merge-demo.xlsx",
            numberOfSheets = 1,
            sheets = listOf(
                Sheet(
                    sheetIndex = 0,
                    sheetName = "Sheet1",
                    elements = listOf(
                        Element.Table(
                            rows = listOf(
                                Row(
                                    rowIndex = 0,
                                    cells = listOf(Cell("Category", mergedRight = 2, mergedDown = 0)),
                                ),
                                Row(
                                    rowIndex = 1,
                                    cells = listOf(Cell("A"), Cell("B"), Cell("C")),
                                ),
                            ),
                            headerRowCount = 1,
                        ),
                    ),
                    mergeRegions = listOf(
                        MergeRegionInfo(cell = "A1", rowSpan = 1, colSpan = 3),
                    ),
                ),
            ),
        )
        val json = JsonFormatter.toRawPrettyJson(doc)
        json shouldContain "\"mergeRegions\""
        json shouldContain "\"cell\" : \"A1\""
        json shouldContain "\"rowSpan\" : 1"
        json shouldContain "\"colSpan\" : 3"

        val roundTripped = JsonFormatter.fromJson(json)
        roundTripped.sheets[0].mergeRegions.size shouldBe 1
        roundTripped.sheets[0].mergeRegions[0].cell shouldBe "A1"
    }

    test("image element with base64 survives round-trip") {
        val doc = ExcelDocument(
            fileName = null,
            numberOfSheets = 1,
            sheets = listOf(
                Sheet(
                    sheetIndex = 0,
                    sheetName = "Images",
                    elements = listOf(
                        Element.Image(
                            base64 = "aGVsbG8=",
                            mimeType = "image/png",
                            description = null,
                        ),
                    ),
                ),
            ),
        )
        val json = JsonFormatter.toRawJson(doc)
        val result = JsonFormatter.fromJson(json)
        val img = result.sheets[0].elements[0] as Element.Image
        img.base64 shouldBe "aGVsbG8="
        img.mimeType shouldBe "image/png"
        img.description shouldBe null
    }
})
