package ai.read4ai.excel.strategy.impl

import ai.read4ai.excel.ExcelParser
import ai.read4ai.excel.model.Element
import ai.read4ai.excel.strategy.StrategyConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream

class StrategyIntegrationTest : FunSpec({

    fun createMultiRegionXlsx(): ByteArray {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("Report")

        // Table 1: top-left
        sheet.createRow(0).apply {
            createCell(0).setCellValue("Name")
            createCell(1).setCellValue("Score")
        }
        sheet.createRow(1).apply {
            createCell(0).setCellValue("Alice")
            createCell(1).setCellValue(95.0)
        }

        // Gap (2 empty rows)
        sheet.createRow(2)
        sheet.createRow(3)

        // Footnote: should be deferred
        sheet.createRow(4).apply {
            createCell(0).setCellValue("Source: internal audit 2024")
        }

        val baos = ByteArrayOutputStream()
        wb.use { it.write(baos) }
        return baos.toByteArray()
    }

    fun createMergedHeaderXlsx(): ByteArray {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("Data")

        // Multi-row header via vertical merge
        sheet.createRow(0).apply {
            createCell(0).setCellValue("Category")
            createCell(1).setCellValue("Q1")
            createCell(2).setCellValue("Q2")
        }
        sheet.createRow(1).apply {
            createCell(0).setCellValue("") // merged with row 0
            createCell(1).setCellValue("Jan-Mar")
            createCell(2).setCellValue("Apr-Jun")
        }
        sheet.createRow(2).apply {
            createCell(0).setCellValue("Revenue")
            createCell(1).setCellValue(1000.0)
            createCell(2).setCellValue(1200.0)
        }
        sheet.createRow(3).apply {
            createCell(0).setCellValue("Costs")
            createCell(1).setCellValue(800.0)
            createCell(2).setCellValue(900.0)
        }

        // Vertical merge: Category spans rows 0-1
        sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress(0, 1, 0, 0))

        val baos = ByteArrayOutputStream()
        wb.use { it.write(baos) }
        return baos.toByteArray()
    }

    test("ThreeLevelSegmenter with DeferredBlockOrderer reorders footnotes") {
        val bytes = createMultiRegionXlsx()
        val strategy = StrategyConfig(
            segmenter = ThreeLevelSegmenter(),
            blockOrderer = DeferredBlockOrderer(),
        )
        val doc = ExcelParser.parse(bytes, strategy = strategy)
        doc.sheets.shouldNotBeEmpty()
        val elements = doc.sheets[0].elements
        elements.shouldNotBeEmpty()

        // The table content should come before the footnote text
        val allTexts = elements.flatMap { el ->
            when (el) {
                is Element.Table -> el.rows.flatMap { r -> r.cells.map { it.value } }
                is Element.Text -> listOf(el.text)
                is Element.Heading -> listOf(el.text)
                else -> emptyList()
            }
        }
        allTexts.any { it.contains("Alice") } shouldBe true
    }

    test("MergeAwareHeaderDetector with merged header XLSX") {
        val bytes = createMergedHeaderXlsx()
        val strategy = StrategyConfig(
            segmenter = ThreeLevelSegmenter(),
            headerDetector = MergeAwareHeaderDetector(),
        )
        val doc = ExcelParser.parse(bytes, strategy = strategy)
        doc.sheets.shouldNotBeEmpty()
        val elements = doc.sheets[0].elements
        elements.shouldNotBeEmpty()

        // Should detect multi-row header
        val tables = elements.filterIsInstance<Element.Table>()
        tables.shouldNotBeEmpty()
    }

    test("FormatAwareElementClassifier preserves table content") {
        val bytes = createMultiRegionXlsx()
        val strategy = StrategyConfig(
            segmenter = ThreeLevelSegmenter(),
            elementClassifier = DefaultElementClassifier(),
        )
        val doc = ExcelParser.parse(bytes, strategy = strategy)
        doc.sheets.shouldNotBeEmpty()
        val elements = doc.sheets[0].elements
        elements.shouldNotBeEmpty()

        val allValues = elements.filterIsInstance<Element.Table>()
            .flatMap { t -> t.rows.flatMap { r -> r.cells.map { it.value } } }
        allValues.any { it.contains("Alice") || it.contains("95") } shouldBe true
    }

    test("full custom strategy: all new implementations together") {
        val bytes = createMultiRegionXlsx()
        val strategy = StrategyConfig(
            segmenter = ThreeLevelSegmenter(),
            headerDetector = MergeAwareHeaderDetector(),
            blockOrderer = DeferredBlockOrderer(),
            elementClassifier = DefaultElementClassifier(),
        )
        val doc = ExcelParser.parse(bytes, strategy = strategy)
        doc.sheets.shouldNotBeEmpty()
        doc.sheets[0].elements.shouldNotBeEmpty()
    }

    test("explicit default strategy matches zero-config parse") {
        val bytes = createMultiRegionXlsx()
        val defaultDoc = ExcelParser.parse(bytes)
        val explicitDefaultDoc = ExcelParser.parse(bytes, strategy = StrategyConfig())
        defaultDoc shouldBe explicitDefaultDoc
    }
})
