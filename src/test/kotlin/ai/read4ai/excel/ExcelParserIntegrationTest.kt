package ai.read4ai.excel

import ai.read4ai.excel.model.Element
import ai.read4ai.excel.output.JsonFormatter
import ai.read4ai.excel.output.MarkdownFormatter
import ai.read4ai.excel.strategy.StrategyConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream

class ExcelParserIntegrationTest : FunSpec({

    fun createSimpleXlsx(): ByteArray {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("TestSheet")

        // Header row
        val headerRow = sheet.createRow(0)
        headerRow.createCell(0).setCellValue("Name")
        headerRow.createCell(1).setCellValue("Age")
        headerRow.createCell(2).setCellValue("City")

        // Data rows
        val row1 = sheet.createRow(1)
        row1.createCell(0).setCellValue("Alice")
        row1.createCell(1).setCellValue(30.0)
        row1.createCell(2).setCellValue("Seoul")

        val row2 = sheet.createRow(2)
        row2.createCell(0).setCellValue("Bob")
        row2.createCell(1).setCellValue(25.0)
        row2.createCell(2).setCellValue("Tokyo")

        val baos = ByteArrayOutputStream()
        wb.use { it.write(baos) }
        return baos.toByteArray()
    }

    test("parse simple XLSX produces correct ExcelDocument") {
        val bytes = createSimpleXlsx()
        val doc = ExcelParser.parse(bytes, fileName = "simple.xlsx")

        doc.fileName shouldBe "simple.xlsx"
        doc.numberOfSheets shouldBe 1
        doc.sheets shouldHaveAtLeastSize 1
        doc.sheets[0].sheetName shouldBe "TestSheet"
        doc.sheets[0].elements.shouldNotBeEmpty()
    }

    test("parsed document has table element with correct data") {
        val bytes = createSimpleXlsx()
        val doc = ExcelParser.parse(bytes)

        val tables = doc.sheets[0].elements.filterIsInstance<Element.Table>()
        tables.shouldNotBeEmpty()

        val table = tables.first()
        table.rows.shouldHaveAtLeastSize(2)

        // Verify cell content is present
        val allCellValues = table.rows.flatMap { row -> row.cells.map { it.value } }
        allCellValues.any { it.contains("Alice") } shouldBe true
        allCellValues.any { it.contains("Bob") } shouldBe true
        allCellValues.any { it.contains("Name") } shouldBe true
    }

    test("JSON output is valid and contains expected content") {
        val bytes = createSimpleXlsx()
        val doc = ExcelParser.parse(bytes, fileName = "test.xlsx")
        val json = JsonFormatter.toRawJson(doc)

        json.shouldNotBeBlank()
        json shouldContain "TestSheet"
        json shouldContain "Alice"

        // Verify it can be deserialized back
        val roundTripped = JsonFormatter.fromJson(json)
        roundTripped.numberOfSheets shouldBe doc.numberOfSheets
    }

    test("Markdown output is valid and contains expected content") {
        val bytes = createSimpleXlsx()
        val doc = ExcelParser.parse(bytes, fileName = "report.xlsx")
        val md = MarkdownFormatter().toMarkdown(doc)

        md.shouldNotBeBlank()
        md shouldNotContain "# report.xlsx"
        md shouldContain "## TestSheet"
        md shouldContain "Alice"
        md shouldContain "Bob"
    }

    test("parse with default config works") {
        val bytes = createSimpleXlsx()
        val doc = ExcelParser.parse(bytes, config = ExcelConfig())
        doc.sheets.shouldNotBeEmpty()
    }

    test("parse with custom config works") {
        val bytes = createSimpleXlsx()
        val doc = ExcelParser.parse(bytes, config = ExcelConfig(imageOutput = ExcelConfig.ImageOutput.SKIP))
        doc.sheets.shouldNotBeEmpty()
    }

    // --- Complex integration test ---

    fun createComplexXlsx(): ByteArray {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("Complex")

        var rowIdx = 0

        // Title row
        val titleRow = sheet.createRow(rowIdx++)
        titleRow.createCell(0).setCellValue("2024\uB144 \uC608\uC0B0 \uBCF4\uACE0\uC11C")

        // Empty rows (gap)
        sheet.createRow(rowIdx++)
        sheet.createRow(rowIdx++)

        // Table 1: headers
        val t1Header = sheet.createRow(rowIdx++)
        t1Header.createCell(0).setCellValue("Category")
        t1Header.createCell(1).setCellValue("Q1")
        t1Header.createCell(2).setCellValue("Q2")

        // Table 1: data
        val t1r1 = sheet.createRow(rowIdx++)
        t1r1.createCell(0).setCellValue("Revenue")
        t1r1.createCell(1).setCellValue(1000.0)
        t1r1.createCell(2).setCellValue(1200.0)

        val t1r2 = sheet.createRow(rowIdx++)
        t1r2.createCell(0).setCellValue("Expenses")
        t1r2.createCell(1).setCellValue(800.0)
        t1r2.createCell(2).setCellValue(900.0)

        // Empty rows (gap between tables)
        sheet.createRow(rowIdx++)
        sheet.createRow(rowIdx++)

        // Table 2: headers
        val t2Header = sheet.createRow(rowIdx++)
        t2Header.createCell(0).setCellValue("Department")
        t2Header.createCell(1).setCellValue("Headcount")

        // Table 2: data
        val t2r1 = sheet.createRow(rowIdx++)
        t2r1.createCell(0).setCellValue("Engineering")
        t2r1.createCell(1).setCellValue(50.0)

        val t2r2 = sheet.createRow(rowIdx++)
        t2r2.createCell(0).setCellValue("Sales")
        t2r2.createCell(1).setCellValue(30.0)

        // Merged cell for title
        sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 2))

        val baos = ByteArrayOutputStream()
        wb.use { it.write(baos) }
        return baos.toByteArray()
    }

    test("complex XLSX with title, gap, and two tables parses correctly") {
        val bytes = createComplexXlsx()
        val doc = ExcelParser.parse(bytes, fileName = "complex.xlsx")

        doc.numberOfSheets shouldBe 1
        val elements = doc.sheets[0].elements
        elements.shouldNotBeEmpty()

        // Should have multiple elements (title/heading + tables)
        elements.size shouldBeGreaterThan 1
    }

    test("complex XLSX produces tables with correct cell values") {
        val bytes = createComplexXlsx()
        val doc = ExcelParser.parse(bytes)

        val tables = doc.sheets[0].elements.filterIsInstance<Element.Table>()
        tables.shouldNotBeEmpty()

        val allCellValues = tables.flatMap { t -> t.rows.flatMap { r -> r.cells.map { it.value } } }
        allCellValues.any { it.contains("Revenue") || it.contains("1000") } shouldBe true
    }

    test("complex XLSX JSON output is valid and round-trips") {
        val bytes = createComplexXlsx()
        val doc = ExcelParser.parse(bytes, fileName = "complex.xlsx")
        val json = JsonFormatter.toRawJson(doc)

        json.shouldNotBeBlank()
        val roundTripped = JsonFormatter.fromJson(json)
        roundTripped.numberOfSheets shouldBe 1
        roundTripped.sheets[0].elements.shouldNotBeEmpty()
    }

    test("complex XLSX markdown output contains both tables") {
        val bytes = createComplexXlsx()
        val doc = ExcelParser.parse(bytes, fileName = "complex.xlsx")
        val md = MarkdownFormatter().toMarkdown(doc)

        md.shouldNotBeBlank()
        md shouldContain "Revenue"
        md shouldContain "Department"
    }

    test("complex XLSX with merged cells preserves merge markers or values") {
        val bytes = createComplexXlsx()
        // Use simple preset for this structural test — balanced may classify differently
        val doc = ExcelParser.parse(bytes, strategy = StrategyConfig(
            segmenter = ai.read4ai.excel.strategy.impl.SimpleSegmenter(),
            headerDetector = ai.read4ai.excel.strategy.impl.SingleRowHeaderDetector(),
            blockOrderer = ai.read4ai.excel.strategy.impl.SequentialBlockOrderer(),
            elementClassifier = ai.read4ai.excel.strategy.impl.DefaultElementClassifier(),
        ))

        val allTexts = doc.sheets[0].elements.flatMap { el ->
            when (el) {
                is Element.Table -> el.rows.flatMap { r -> r.cells.map { it.value } }
                is Element.Heading -> listOf(el.text)
                is Element.Text -> listOf(el.text)
                is Element.Note -> listOf(el.text)
                is Element.Image -> listOfNotNull(el.description)
            }
        }
        allTexts.any { it.contains("2024") } shouldBe true
    }

    // --- Multi-sheet test ---

    fun createMultiSheetXlsx(): ByteArray {
        val wb = XSSFWorkbook()

        val sheet1 = wb.createSheet("Overview")
        sheet1.createRow(0).createCell(0).setCellValue("Summary")
        sheet1.createRow(1).createCell(0).setCellValue("Total")
        sheet1.createRow(1).createCell(1).setCellValue(999.0)

        val sheet2 = wb.createSheet("Details")
        sheet2.createRow(0).createCell(0).setCellValue("Item")
        sheet2.createRow(0).createCell(1).setCellValue("Count")
        sheet2.createRow(1).createCell(0).setCellValue("Widget")
        sheet2.createRow(1).createCell(1).setCellValue(42.0)

        val baos = ByteArrayOutputStream()
        wb.use { it.write(baos) }
        return baos.toByteArray()
    }

    test("multi-sheet XLSX parses all sheets") {
        val bytes = createMultiSheetXlsx()
        val doc = ExcelParser.parse(bytes, fileName = "multi.xlsx")

        doc.numberOfSheets shouldBe 2
        doc.sheets shouldHaveAtLeastSize 2
        doc.sheets[0].sheetName shouldBe "Overview"
        doc.sheets[1].sheetName shouldBe "Details"
    }

    test("multi-sheet markdown includes both sheet sections") {
        val bytes = createMultiSheetXlsx()
        val doc = ExcelParser.parse(bytes)
        val md = MarkdownFormatter().toMarkdown(doc)

        md shouldContain "## Overview"
        md shouldContain "## Details"
        // Verify the data from both sheets is present somewhere in the output
        md shouldContain "999"
        md shouldContain "42"
    }

    // --- Formula test ---

    fun createFormulaXlsx(): ByteArray {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("Calc")

        val row = sheet.createRow(0)
        row.createCell(0).setCellValue("Value A")
        row.createCell(1).setCellValue("Value B")
        row.createCell(2).setCellValue("Sum")

        val dataRow = sheet.createRow(1)
        dataRow.createCell(0).setCellValue(10.0)
        dataRow.createCell(1).setCellValue(20.0)
        dataRow.createCell(2).cellFormula = "A2+B2"

        val baos = ByteArrayOutputStream()
        wb.use { it.write(baos) }
        return baos.toByteArray()
    }

    test("formula cells are evaluated") {
        val bytes = createFormulaXlsx()
        val doc = ExcelParser.parse(bytes)

        val tables = doc.sheets[0].elements.filterIsInstance<Element.Table>()
        tables.shouldNotBeEmpty()

        val allValues = tables.flatMap { t -> t.rows.flatMap { r -> r.cells.map { it.value } } }
        allValues.any { it == "30" } shouldBe true
    }

    // --- Boolean cell test ---

    fun createBooleanXlsx(): ByteArray {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("Booleans")
        val row = sheet.createRow(0)
        row.createCell(0).setCellValue("Active")
        row.createCell(1).setCellValue(true)
        val row2 = sheet.createRow(1)
        row2.createCell(0).setCellValue("Disabled")
        row2.createCell(1).setCellValue(false)

        val baos = ByteArrayOutputStream()
        wb.use { it.write(baos) }
        return baos.toByteArray()
    }

    test("boolean cells are converted to TRUE/FALSE strings") {
        val bytes = createBooleanXlsx()
        val doc = ExcelParser.parse(bytes)

        val allValues = doc.sheets[0].elements
            .filterIsInstance<Element.Table>()
            .flatMap { t -> t.rows.flatMap { r -> r.cells.map { it.value } } }
        allValues.any { it == "TRUE" } shouldBe true
        allValues.any { it == "FALSE" } shouldBe true
    }
})
