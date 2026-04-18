package ai.read4ai.excel.output

import ai.read4ai.excel.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf

class MarkdownFormatterTest : FunSpec({

    // Shared instance for all tests
    val writer = MarkdownFormatter()

    test("MarkdownFormatter implements DocumentFormatter") {
        writer.shouldBeInstanceOf<DocumentFormatter>()
    }

    test("heading element renders with correct prefix") {
        val heading = Element.Heading(text = "Title", level = 2)
        writer.elementToMarkdown(heading) shouldBe "## Title"
    }

    test("heading level 1") {
        val heading = Element.Heading(text = "Top", level = 1)
        writer.elementToMarkdown(heading) shouldBe "# Top"
    }

    test("heading level 3") {
        val heading = Element.Heading(text = "Sub", level = 3)
        writer.elementToMarkdown(heading) shouldBe "### Sub"
    }

    test("heading level clamped to 6 max") {
        val heading = Element.Heading(text = "Deep", level = 10)
        writer.elementToMarkdown(heading) shouldStartWith "######"
    }

    test("heading level clamped to 1 min") {
        val heading = Element.Heading(text = "Top", level = 0)
        writer.elementToMarkdown(heading) shouldStartWith "#"
        writer.elementToMarkdown(heading) shouldBe "# Top"
    }

    test("text element renders as plain text") {
        val text = Element.Text(text = "Some paragraph")
        writer.elementToMarkdown(text) shouldBe "Some paragraph"
    }

    test("note element renders as blockquote") {
        val note = Element.Note(text = "Important info")
        writer.elementToMarkdown(note) shouldBe "> Important info"
    }

    test("image with description renders description") {
        val img = Element.Image(base64 = "abc", mimeType = "image/png", description = "A chart showing sales")
        writer.elementToMarkdown(img) shouldBe "A chart showing sales"
    }

    test("image with base64 but no description renders type placeholder") {
        val img = Element.Image(base64 = "abc", mimeType = "image/jpeg", description = null)
        writer.elementToMarkdown(img) shouldBe "[Image: image/jpeg]"
    }

    test("image with no base64 and no description renders generic placeholder") {
        val img = Element.Image(base64 = null, mimeType = null, description = null)
        writer.elementToMarkdown(img) shouldBe "[Image]"
    }

    test("table renders as markdown table with pipes and row index") {
        val table = Element.Table(
            rows = listOf(
                Row(0, listOf(Cell("Name"), Cell("Age"))),
                Row(1, listOf(Cell("Alice"), Cell("30"))),
            ),
            headerRowCount = 1,
        )
        val md = writer.elementToMarkdown(table)
        md shouldContain "| 1 | Name | Age |"
        md shouldContain "| --- | --- | --- |"
        md shouldContain "| 2 | Alice | 30 |"
    }

    test("empty table renders placeholder") {
        val table = Element.Table(rows = emptyList(), headerRowCount = 0)
        writer.elementToMarkdown(table) shouldBe "*Empty table*"
    }

    test("table escapes pipe characters in cell values") {
        val table = Element.Table(
            rows = listOf(
                Row(0, listOf(Cell("A|B"))),
                Row(1, listOf(Cell("C"))),
            ),
            headerRowCount = 1,
        )
        val md = writer.elementToMarkdown(table)
        md shouldContain "A\\|B"
        md shouldNotContain "| A|B |"
    }

    test("table replaces newlines with br tags") {
        val table = Element.Table(
            rows = listOf(
                Row(0, listOf(Cell("Header"))),
                Row(1, listOf(Cell("Line1\nLine2"))),
            ),
            headerRowCount = 1,
        )
        val md = writer.elementToMarkdown(table)
        md shouldContain "Line1<br>Line2"
    }

    test("full document markdown excludes filename by default") {
        val doc = ExcelDocument(
            fileName = "report.xlsx",
            numberOfSheets = 1,
            sheets = listOf(
                Sheet(0, "Data", listOf(Element.Text(text = "Hello"))),
            ),
        )
        val md = writer.toMarkdown(doc)
        md shouldNotContain "# report.xlsx"
        md shouldContain "## Data"
    }

    test("full document markdown includes filename when requested") {
        val doc = ExcelDocument(
            fileName = "report.xlsx",
            numberOfSheets = 1,
            sheets = listOf(
                Sheet(0, "Data", listOf(Element.Text(text = "Hello"))),
            ),
        )
        val md = writer.toMarkdown(doc, includeFileName = true)
        md shouldContain "# report.xlsx"
    }

    test("write() produces same output as toMarkdown()") {
        val doc = ExcelDocument(
            fileName = null,
            numberOfSheets = 1,
            sheets = listOf(
                Sheet(0, "Sheet1", listOf(Element.Text(text = "Content"))),
            ),
        )
        writer.format(doc) shouldBe writer.toMarkdown(doc)
    }

    test("sheet renders as h2 with sheet name") {
        val sheet = Sheet(0, "Summary", listOf(Element.Text(text = "Content")))
        val md = writer.sheetToMarkdown(sheet)
        md shouldContain "## Summary"
    }

    test("multiple elements in sheet are separated by blank lines") {
        val doc = ExcelDocument(
            fileName = null,
            numberOfSheets = 1,
            sheets = listOf(
                Sheet(
                    0, "Sheet1", listOf(
                        Element.Heading(text = "Title", level = 3),
                        Element.Text(text = "Paragraph"),
                    )
                ),
            ),
        )
        val md = writer.toMarkdown(doc)
        md shouldContain "### Title"
        md shouldContain "Paragraph"
    }

    test("merged cell annotated with span info") {
        val table = Element.Table(
            rows = listOf(
                Row(0, listOf(Cell("Header", mergedRight = 2, mergedDown = 0))),
                Row(1, listOf(Cell("A"), Cell("B"), Cell("C"))),
            ),
            headerRowCount = 1,
        )
        val md = writer.elementToMarkdown(table)
        md shouldContain "Header [merged 1x3]"
    }

    test("merged cell with both row and col span") {
        val table = Element.Table(
            rows = listOf(
                Row(0, listOf(Cell("Big", mergedRight = 1, mergedDown = 2))),
            ),
            headerRowCount = 0,
        )
        val md = writer.elementToMarkdown(table)
        md shouldContain "Big [merged 3x2]"
    }

    test("non-merged cell has no annotation") {
        val table = Element.Table(
            rows = listOf(
                Row(0, listOf(Cell("Plain"))),
            ),
            headerRowCount = 0,
        )
        val md = writer.elementToMarkdown(table)
        md shouldContain "| Plain |"
        md shouldNotContain "merged"
    }

    test("multiple sheets are separated") {
        val doc = ExcelDocument(
            fileName = null,
            numberOfSheets = 2,
            sheets = listOf(
                Sheet(0, "Sheet1", listOf(Element.Text(text = "A"))),
                Sheet(1, "Sheet2", listOf(Element.Text(text = "B"))),
            ),
        )
        val md = writer.toMarkdown(doc)
        md shouldContain "## Sheet1"
        md shouldContain "## Sheet2"
    }
})
