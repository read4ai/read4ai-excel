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
