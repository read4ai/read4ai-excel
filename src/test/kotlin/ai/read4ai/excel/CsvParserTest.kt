package ai.read4ai.excel

import ai.read4ai.excel.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class CsvParserTest : FunSpec({

    test("parse TSV (tab-delimited)") {
        val tsv = "Name\tAge\nAlice\t30"

        val doc = CsvParser.parse(
            tsv.toByteArray(),
            config = CsvConfig(delimiter = '\t'),
        )

        val table = doc.sheets[0].elements[0] as Element.Table
        table.rows[0].cells[0].value shouldBe "Name"
        table.rows[0].cells[1].value shouldBe "Age"
        table.rows[1].cells[0].value shouldBe "Alice"
        table.rows[1].cells[1].value shouldBe "30"
    }

    test("sheet name defaults to Sheet1 when no filename") {
        val csv = "a,b\n1,2"
        val doc = CsvParser.parse(csv.toByteArray())
        doc.sheets[0].sheetName shouldBe "Sheet1"
    }

})
