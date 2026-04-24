@file:OptIn(ai.read4ai.excel.ExperimentalRead4ai::class)

package ai.read4ai.excel.strategy

import ai.read4ai.excel.ExcelConfig
import ai.read4ai.excel.ExcelParser
import ai.read4ai.excel.model.Element
import ai.read4ai.excel.strategy.impl.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream

class StrategyTest : FunSpec({

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

    // --- StrategyConfig defaults ---

    test("StrategyConfig() creates default implementations") {
        val config = StrategyConfig()
        config.workbookReader.shouldBeInstanceOf<PoiWorkbookReader>()
        config.gridExtractor.shouldBeInstanceOf<DefaultGridExtractor>()
        config.segmenter.shouldBeInstanceOf<GraphSegmenter>()
        config.headerDetector.shouldBeInstanceOf<MergeAwareHeaderDetector>()
        config.blockOrderer.shouldBeInstanceOf<DeferredBlockOrderer>()
        config.elementClassifier.shouldBeInstanceOf<DefaultElementClassifier>()
    }

    // --- Zero-config default strategy ---

    test("explicit default strategy produces same result as zero-config parse") {
        val bytes = createSimpleXlsx()
        val docDefault = ExcelParser.parse(bytes, fileName = "test.xlsx")
        val docStrategy = ExcelParser.parse(bytes, fileName = "test.xlsx", strategy = StrategyConfig())
        docDefault shouldBe docStrategy
    }

    test("explicit default strategy with two tables matches zero-config parse") {
        val bytes = createTwoTableXlsx()
        val docDefault = ExcelParser.parse(bytes)
        val docStrategy = ExcelParser.parse(bytes, strategy = StrategyConfig())
        docDefault shouldBe docStrategy
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
                    headerInfo = HeaderInfo(headerRowCount = 1, headerRows = listOf(listOf("A"))),
                    element = Element.Text(text = "first"),
                    isDeferred = false,
                ),
                Block(
                    segment = Segment(
                        grid = Grid(cells = listOf(listOf("B")), mergeRegions = emptyList(), rowCount = 1, colCount = 1),
                        startRow = 5, startCol = 0, gapFromPrevious = 3,
                    ),
                    headerInfo = HeaderInfo(headerRowCount = 1, headerRows = listOf(listOf("B"))),
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
            val headerInfo = HeaderInfo(headerRowCount = 1, headerRows = listOf(listOf("Name", "Age")))
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
            val headerInfo = HeaderInfo(headerRowCount = 0, headerRows = emptyList())
            val element = classifier.classify(segment, headerInfo)
            element.shouldBeInstanceOf<Element.Text>()
            element.shouldBeInstanceOf<Element.Text>().text shouldBe "Hello"
        }

        test("drops intro banner rows before detected header when building a table") {
            val classifier = DefaultElementClassifier()
            val segment = Segment(
                grid = Grid(
                    cells = listOf(
                        listOf("2025년 10월 수수료 안내", "", "", ""),
                        listOf("", "", "", ""),
                        listOf("제품군", "6년", "", ""),
                        listOf("", "단품요금", "신규결합", "기존결합"),
                        listOf("정수기", "39900", "35900", "35900"),
                    ),
                    mergeRegions = emptyList(),
                    rowCount = 5,
                    colCount = 4,
                ),
                startRow = 10,
                startCol = 0,
                gapFromPrevious = 0,
            )
            val headerInfo = HeaderInfo(
                headerStartRow = 2,
                headerRowCount = 2,
                headerRows = listOf(
                    listOf("제품군", "6년", "", ""),
                    listOf("", "단품요금", "신규결합", "기존결합"),
                ),
                columnPaths = mapOf(
                    1 to listOf("6년", "단품요금"),
                    2 to listOf("6년", "신규결합"),
                    3 to listOf("6년", "기존결합"),
                ),
            )

            val element = classifier.classify(segment, headerInfo)
            element.shouldBeInstanceOf<Element.Table>()
            element.shouldBeInstanceOf<Element.Table>().startRow shouldBe 12
            element.rows.first().cells.map { it.value } shouldBe listOf("제품군", "6년", "", "")
            element.rows.drop(2).first().cells.map { it.value } shouldBe listOf("정수기", "39900", "35900", "35900")
        }
    }

    // --- Multi-table: absolute row indices via startRow + rowIndex ---

    test("multi-table: second table has correct absolute row coordinates") {
        val bytes = createTwoTableXlsx()
        val doc = ExcelParser.parse(bytes, strategy = StrategyConfig())
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

    test("multi-table: all strategies produce correct absolute coordinates") {
        val bytes = createTwoTableXlsx()
        val strategies = listOf(
            StrategyConfig.balanced(),
            StrategyConfig.complex(),
            StrategyConfig.structural(),
            StrategyConfig.scattered(),
        )

        for (strategy in strategies) {
            val doc = ExcelParser.parse(bytes, strategy = strategy)
            val tables = doc.sheets[0].elements.filterIsInstance<Element.Table>()
            tables shouldHaveAtLeastSize 2

            val t2 = tables[1]
            // Second table at sheet row 4 -> startRow + rowIndex + 1 = 5 (Excel row 5)
            val absRow = t2.startRow + t2.rows[0].rowIndex + 1
            absRow shouldBe 5
        }
    }

    // --- Custom strategy wiring ---

    test("custom segmenter is used when provided") {
        val bytes = createSimpleXlsx()

        // A custom segmenter that returns zero segments (forces fallback)
        val emptySegmenter = object : Segmenter {
            override fun segment(grid: Grid): List<Segment> = emptyList()
        }

        val doc = ExcelParser.parse(
            bytes,
            strategy = StrategyConfig(segmenter = emptySegmenter),
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
            strategy = StrategyConfig(blockOrderer = reverseOrderer),
        )
        doc.sheets[0].elements.shouldNotBeEmpty()
    }
})
