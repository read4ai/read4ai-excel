@file:OptIn(ai.read4ai.excel.ExperimentalRead4ai::class)

package ai.read4ai.excel.pipeline

import ai.read4ai.excel.ExcelConfig
import ai.read4ai.excel.ExcelParser
import ai.read4ai.excel.model.Element
import ai.read4ai.excel.pipeline.impl.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream

class PipelineTest : FunSpec({

    fun createSimpleXlsx(): ByteArray {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("TestSheet")
        val headerRow = sheet.createRow(0)
        headerRow.createCell(0).setCellValue("Name")
        headerRow.createCell(1).setCellValue("Age")
        val row1 = sheet.createRow(1)
        row1.createCell(0).setCellValue("Alice")
        row1.createCell(1).setCellValue(30.0)
        val baos = ByteArrayOutputStream()
        wb.use { it.write(baos) }
        return baos.toByteArray()
    }

    fun createTwoTableXlsx(): ByteArray {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("Data")
        // Table 1
        sheet.createRow(0).apply {
            createCell(0).setCellValue("A")
            createCell(1).setCellValue("B")
        }
        sheet.createRow(1).apply {
            createCell(0).setCellValue("1")
            createCell(1).setCellValue("2")
        }
        // Gap
        sheet.createRow(2)
        sheet.createRow(3)
        // Table 2
        sheet.createRow(4).apply {
            createCell(0).setCellValue("X")
            createCell(1).setCellValue("Y")
        }
        sheet.createRow(5).apply {
            createCell(0).setCellValue("3")
            createCell(1).setCellValue("4")
        }
        val baos = ByteArrayOutputStream()
        wb.use { it.write(baos) }
        return baos.toByteArray()
    }

    // --- PipelineConfig defaults ---

    test("PipelineConfig() creates default implementations") {
        val config = PipelineConfig()
        config.workbookReader.shouldBeInstanceOf<PoiWorkbookReader>()
        config.gridExtractor.shouldBeInstanceOf<DefaultGridExtractor>()
        config.segmenter.shouldBeInstanceOf<GraphSegmenter>()
        config.headerDetector.shouldBeInstanceOf<MergeAwareHeaderDetector>()
        config.blockOrderer.shouldBeInstanceOf<DeferredBlockOrderer>()
        config.elementClassifier.shouldBeInstanceOf<DefaultElementClassifier>()
    }

    // --- Backward compatibility: default pipeline produces same result ---

    test("default pipeline produces same result as no-pipeline parse") {
        val bytes = createSimpleXlsx()
        val docDefault = ExcelParser.parse(bytes, fileName = "test.xlsx")
        val docPipeline = ExcelParser.parse(bytes, fileName = "test.xlsx", pipeline = PipelineConfig())
        docDefault shouldBe docPipeline
    }

    test("default pipeline with two tables matches legacy behavior") {
        val bytes = createTwoTableXlsx()
        val docDefault = ExcelParser.parse(bytes)
        val docPipeline = ExcelParser.parse(bytes, pipeline = PipelineConfig())
        docDefault shouldBe docPipeline
    }

    // --- Individual step tests ---

    context("SimpleSegmenter") {

        test("empty grid returns empty segments") {
            val segmenter = SimpleSegmenter()
            val grid = Grid(cells = emptyList(), mergeRegions = emptyList(), rowCount = 0, colCount = 0)
            segmenter.segment(grid) shouldHaveSize 0
        }

        test("single block grid returns one segment") {
            val segmenter = SimpleSegmenter()
            val grid = Grid(
                cells = listOf(listOf("A", "B"), listOf("C", "D")),
                mergeRegions = emptyList(),
                rowCount = 2,
                colCount = 2,
            )
            val segments = segmenter.segment(grid)
            segments shouldHaveSize 1
            segments[0].grid.cells shouldHaveSize 2
        }

        test("two blocks separated by gap returns two segments") {
            val segmenter = SimpleSegmenter()
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
        }
    }

    context("SingleRowHeaderDetector") {

        test("detects first non-empty row as header") {
            val detector = SingleRowHeaderDetector()
            val segment = Segment(
                grid = Grid(
                    cells = listOf(listOf("Name", "Age"), listOf("Alice", "30")),
                    mergeRegions = emptyList(),
                    rowCount = 2,
                    colCount = 2,
                ),
                startRow = 0,
                startCol = 0,
                gapFromPrevious = 0,
            )
            val info = detector.detectHeaders(segment)
            info.headerRowCount shouldBe 1
            info.headerRows shouldHaveSize 1
            info.headerRows[0] shouldBe listOf("Name", "Age")
        }

        test("empty grid returns zero headers") {
            val detector = SingleRowHeaderDetector()
            val segment = Segment(
                grid = Grid(cells = emptyList(), mergeRegions = emptyList(), rowCount = 0, colCount = 0),
                startRow = 0,
                startCol = 0,
                gapFromPrevious = 0,
            )
            val info = detector.detectHeaders(segment)
            info.headerRowCount shouldBe 0
            info.headerRows shouldHaveSize 0
        }
    }

    context("SequentialBlockOrderer") {

        test("preserves original order") {
            val orderer = SequentialBlockOrderer()
            val blocks = listOf(
                Block(
                    segment = Segment(
                        grid = Grid(cells = listOf(listOf("A")), mergeRegions = emptyList(), rowCount = 1, colCount = 1),
                        startRow = 0, startCol = 0, gapFromPrevious = 0,
                    ),
                    headerInfo = HeaderInfo(1, listOf(listOf("A"))),
                    element = Element.Text(text = "first"),
                    isDeferred = false,
                ),
                Block(
                    segment = Segment(
                        grid = Grid(cells = listOf(listOf("B")), mergeRegions = emptyList(), rowCount = 1, colCount = 1),
                        startRow = 5, startCol = 0, gapFromPrevious = 3,
                    ),
                    headerInfo = HeaderInfo(1, listOf(listOf("B"))),
                    element = Element.Text(text = "second"),
                    isDeferred = true,
                ),
            )
            val ordered = orderer.order(blocks)
            ordered shouldHaveSize 2
            ordered[0].element.shouldBeInstanceOf<Element.Text>().text shouldBe "first"
            ordered[1].element.shouldBeInstanceOf<Element.Text>().text shouldBe "second"
        }
    }

    context("DefaultElementClassifier") {

        test("classifies multi-row content as table") {
            val classifier = DefaultElementClassifier()
            val segment = Segment(
                grid = Grid(
                    cells = listOf(
                        listOf("Name", "Age"),
                        listOf("Alice", "30"),
                        listOf("Bob", "25"),
                    ),
                    mergeRegions = emptyList(),
                    rowCount = 3,
                    colCount = 2,
                ),
                startRow = 0,
                startCol = 0,
                gapFromPrevious = 0,
            )
            val headerInfo = HeaderInfo(1, listOf(listOf("Name", "Age")))
            val element = classifier.classify(segment, headerInfo)
            element.shouldBeInstanceOf<Element.Table>()
            element.shouldBeInstanceOf<Element.Table>().headerRowCount shouldBe 1
        }

        test("classifies isolated single cell as text") {
            val classifier = DefaultElementClassifier()
            val segment = Segment(
                grid = Grid(
                    cells = listOf(listOf("", ""), listOf("Hello", ""), listOf("", "")),
                    mergeRegions = emptyList(),
                    rowCount = 3,
                    colCount = 2,
                ),
                startRow = 0,
                startCol = 0,
                gapFromPrevious = 0,
            )
            val headerInfo = HeaderInfo(0, emptyList())
            val element = classifier.classify(segment, headerInfo)
            element.shouldBeInstanceOf<Element.Text>()
            element.shouldBeInstanceOf<Element.Text>().text shouldBe "Hello"
        }
    }

    // --- Multi-table: absolute row indices via startRow + rowIndex ---

    test("multi-table: second table has correct absolute row coordinates") {
        val bytes = createTwoTableXlsx()
        val doc = ExcelParser.parse(bytes, pipeline = PipelineConfig())
        val sheet = doc.sheets[0]

        // Should find at least 2 tables
        val tables = sheet.elements.filterIsInstance<Element.Table>()
        tables shouldHaveAtLeastSize 2

        // Table 1 starts at sheet row 0; Row.rowIndex is relative (0-based within table)
        val t1 = tables[0]
        t1.startRow shouldBe 0
        t1.rows[0].rowIndex shouldBe 0 // relative row 0 -> absolute row 0

        // Table 2 starts at sheet row 4; Row.rowIndex is relative (0-based within table)
        val t2 = tables[1]
        t2.startRow shouldBe 4
        t2.rows[0].rowIndex shouldBe 0 // relative row 0 -> absolute row 4

        // Verify absolute cell references: startRow + rowIndex + 1 = Excel 1-based row
        (t1.startRow + t1.rows[0].rowIndex + 1) shouldBe 1 // A1
        (t2.startRow + t2.rows[0].rowIndex + 1) shouldBe 5 // A5
    }

    test("multi-table: all pipelines produce correct absolute coordinates") {
        val bytes = createTwoTableXlsx()
        val pipelines = listOf(
            "balanced" to PipelineConfig.Strategy.balanced(),
            "complex" to PipelineConfig.Strategy.complex(),
            "structural" to PipelineConfig.Strategy.structural(),
            "scattered" to PipelineConfig.Strategy.scattered(),
        )

        for ((name, pipeline) in pipelines) {
            val doc = ExcelParser.parse(bytes, pipeline = pipeline)
            val tables = doc.sheets[0].elements.filterIsInstance<Element.Table>()
            tables shouldHaveAtLeastSize 2

            val t2 = tables[1]
            // Second table at sheet row 4 -> startRow + rowIndex + 1 = 5 (Excel row 5)
            val absRow = t2.startRow + t2.rows[0].rowIndex + 1
            absRow shouldBe 5
        }
    }

    // --- Custom pipeline wiring ---

    test("custom segmenter is used when provided") {
        val bytes = createSimpleXlsx()

        // A custom segmenter that returns zero segments (forces fallback)
        val emptySegmenter = object : Segmenter {
            override fun segment(grid: Grid): List<Segment> = emptyList()
        }

        val doc = ExcelParser.parse(
            bytes,
            pipeline = PipelineConfig(segmenter = emptySegmenter),
        )
        doc.sheets.shouldNotBeEmpty()
        // With empty segmenter, fallback creates a single table from entire grid
        val elements = doc.sheets[0].elements
        elements shouldHaveSize 1
        elements[0].shouldBeInstanceOf<Element.Table>()
    }

    test("custom block orderer reverses block order") {
        val bytes = createTwoTableXlsx()

        val reverseOrderer = object : BlockOrderer {
            override fun order(blocks: List<Block>): List<Block> = blocks.reversed()
        }

        val doc = ExcelParser.parse(
            bytes,
            pipeline = PipelineConfig(blockOrderer = reverseOrderer),
        )
        doc.sheets[0].elements.shouldNotBeEmpty()
    }
})
