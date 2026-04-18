package ai.read4ai.excel.cell

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.FormulaError
import org.apache.poi.xssf.usermodel.XSSFWorkbook

class CellStringConverterTest : FunSpec({

    fun createWorkbookContext(): Triple<XSSFWorkbook, org.apache.poi.ss.usermodel.FormulaEvaluator, DataFormatter> {
        val wb = XSSFWorkbook()
        val evaluator = wb.creationHelper.createFormulaEvaluator()
        val dataFormatter = DataFormatter()
        return Triple(wb, evaluator, dataFormatter)
    }

    test("null cell returns empty string") {
        val (wb, evaluator, dataFormatter) = createWorkbookContext()
        wb.use {
            CellStringConverter.convertCellToString(null, evaluator, dataFormatter) shouldBe ""
        }
    }

    test("blank cell returns empty string") {
        val (wb, evaluator, dataFormatter) = createWorkbookContext()
        wb.use {
            val sheet = wb.createSheet()
            val row = sheet.createRow(0)
            val cell = row.createCell(0)
            // cell is blank by default
            CellStringConverter.convertCellToString(cell, evaluator, dataFormatter) shouldBe ""
        }
    }

    test("string cell returns its value") {
        val (wb, evaluator, dataFormatter) = createWorkbookContext()
        wb.use {
            val sheet = wb.createSheet()
            val row = sheet.createRow(0)
            val cell = row.createCell(0)
            cell.setCellValue("Hello World")
            CellStringConverter.convertCellToString(cell, evaluator, dataFormatter) shouldBe "Hello World"
        }
    }

    test("integer numeric cell returns without decimal point") {
        val (wb, evaluator, dataFormatter) = createWorkbookContext()
        wb.use {
            val sheet = wb.createSheet()
            val row = sheet.createRow(0)
            val cell = row.createCell(0)
            cell.setCellValue(42.0)
            CellStringConverter.convertCellToString(cell, evaluator, dataFormatter) shouldBe "42"
        }
    }

    test("fractional numeric cell returns with decimal") {
        val (wb, evaluator, dataFormatter) = createWorkbookContext()
        wb.use {
            val sheet = wb.createSheet()
            val row = sheet.createRow(0)
            val cell = row.createCell(0)
            cell.setCellValue(3.14)
            val result = CellStringConverter.convertCellToString(cell, evaluator, dataFormatter)
            result shouldBe "3.14"
        }
    }

    test("boolean true cell returns 'TRUE'") {
        val (wb, evaluator, dataFormatter) = createWorkbookContext()
        wb.use {
            val sheet = wb.createSheet()
            val row = sheet.createRow(0)
            val cell = row.createCell(0)
            cell.setCellValue(true)
            CellStringConverter.convertCellToString(cell, evaluator, dataFormatter) shouldBe "TRUE"
        }
    }

    test("boolean false cell returns 'FALSE'") {
        val (wb, evaluator, dataFormatter) = createWorkbookContext()
        wb.use {
            val sheet = wb.createSheet()
            val row = sheet.createRow(0)
            val cell = row.createCell(0)
            cell.setCellValue(false)
            CellStringConverter.convertCellToString(cell, evaluator, dataFormatter) shouldBe "FALSE"
        }
    }

    test("error cell returns error string") {
        val (wb, evaluator, dataFormatter) = createWorkbookContext()
        wb.use {
            val sheet = wb.createSheet()
            val row = sheet.createRow(0)
            val cell = row.createCell(0)
            cell.setCellErrorValue(FormulaError.DIV0.code)
            CellStringConverter.convertCellToString(cell, evaluator, dataFormatter) shouldBe "#DIV/0!"
        }
    }

    test("simple formula returns computed result") {
        val (wb, evaluator, dataFormatter) = createWorkbookContext()
        wb.use {
            val sheet = wb.createSheet()
            val row = sheet.createRow(0)
            val cellA = row.createCell(0)
            cellA.setCellValue(10.0)
            val cellB = row.createCell(1)
            cellB.setCellValue(20.0)
            val cellC = row.createCell(2)
            cellC.cellFormula = "A1+B1"

            evaluator.evaluateAll()
            CellStringConverter.convertCellToString(cellC, evaluator, dataFormatter) shouldBe "30"
        }
    }

    test("string formula returns string result") {
        val (wb, evaluator, dataFormatter) = createWorkbookContext()
        wb.use {
            val sheet = wb.createSheet()
            val row = sheet.createRow(0)
            val cellA = row.createCell(0)
            cellA.setCellValue("Hello")
            val cellB = row.createCell(1)
            cellB.setCellValue(" World")
            val cellC = row.createCell(2)
            cellC.cellFormula = "CONCATENATE(A1,B1)"

            evaluator.evaluateAll()
            CellStringConverter.convertCellToString(cellC, evaluator, dataFormatter) shouldBe "Hello World"
        }
    }

    test("special characters in string are preserved") {
        val (wb, evaluator, dataFormatter) = createWorkbookContext()
        wb.use {
            val sheet = wb.createSheet()
            val row = sheet.createRow(0)
            val cell = row.createCell(0)
            cell.setCellValue("Price: \$100 & Tax < 10%")
            val result = CellStringConverter.convertCellToString(cell, evaluator, dataFormatter)
            result shouldContain "\$100"
            result shouldContain "&"
            result shouldContain "<"
        }
    }

    test("unicode characters are preserved") {
        val (wb, evaluator, dataFormatter) = createWorkbookContext()
        wb.use {
            val sheet = wb.createSheet()
            val row = sheet.createRow(0)
            val cell = row.createCell(0)
            cell.setCellValue("\uD55C\uAD6D\uC5B4 \uD14C\uC2A4\uD2B8")
            CellStringConverter.convertCellToString(cell, evaluator, dataFormatter) shouldBe "\uD55C\uAD6D\uC5B4 \uD14C\uC2A4\uD2B8"
        }
    }

    test("cell with hidden characters gets sanitized") {
        val (wb, evaluator, dataFormatter) = createWorkbookContext()
        wb.use {
            val sheet = wb.createSheet()
            val row = sheet.createRow(0)
            val cell = row.createCell(0)
            cell.setCellValue("Hello\u200BWorld\u0000Test")
            val result = CellStringConverter.convertCellToString(cell, evaluator, dataFormatter)
            result shouldNotContain "\u200B"
            result shouldNotContain "\u0000"
            result shouldContain "Hello"
            result shouldContain "World"
        }
    }

    test("large integer does not use scientific notation") {
        val (wb, evaluator, dataFormatter) = createWorkbookContext()
        wb.use {
            val sheet = wb.createSheet()
            val row = sheet.createRow(0)
            val cell = row.createCell(0)
            cell.setCellValue(1234567890.0)
            val result = CellStringConverter.convertCellToString(cell, evaluator, dataFormatter)
            result shouldBe "1234567890"
        }
    }

    test("zero returns '0'") {
        val (wb, evaluator, dataFormatter) = createWorkbookContext()
        wb.use {
            val sheet = wb.createSheet()
            val row = sheet.createRow(0)
            val cell = row.createCell(0)
            cell.setCellValue(0.0)
            CellStringConverter.convertCellToString(cell, evaluator, dataFormatter) shouldBe "0"
        }
    }

    test("negative integer returns without decimal") {
        val (wb, evaluator, dataFormatter) = createWorkbookContext()
        wb.use {
            val sheet = wb.createSheet()
            val row = sheet.createRow(0)
            val cell = row.createCell(0)
            cell.setCellValue(-5.0)
            CellStringConverter.convertCellToString(cell, evaluator, dataFormatter) shouldBe "-5"
        }
    }

    test("whitespace-only string returns empty after trim") {
        val (wb, evaluator, dataFormatter) = createWorkbookContext()
        wb.use {
            val sheet = wb.createSheet()
            val row = sheet.createRow(0)
            val cell = row.createCell(0)
            cell.setCellValue("   ")
            val result = CellStringConverter.convertCellToString(cell, evaluator, dataFormatter)
            result shouldBe ""
        }
    }
})
