package ai.read4ai.excel.pipeline.impl

import ai.read4ai.excel.model.Element
import ai.read4ai.excel.pipeline.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class DeferredBlockOrdererTest : FunSpec({

    val orderer = DeferredBlockOrderer()

    fun makeBlock(text: String, gap: Int): Block {
        return Block(
            segment = Segment(
                grid = Grid(
                    cells = listOf(listOf(text)),
                    mergeRegions = emptyList(),
                    rowCount = 1,
                    colCount = 1,
                ),
                startRow = 0,
                startCol = 0,
                gapFromPrevious = gap,
            ),
            headerInfo = HeaderInfo(headerRowCount = 0, headerRows = emptyList()),
            element = Element.Text(text = text),
            isDeferred = false,
        )
    }

    test("empty list returns empty") {
        orderer.order(emptyList()).shouldBeEmpty()
    }

    test("all primary blocks stay in order") {
        val blocks = listOf(
            makeBlock("A", gap = 0),
            makeBlock("B", gap = 1),
        )
        val ordered = orderer.order(blocks)
        ordered shouldHaveSize 2
        (ordered[0].element as Element.Text).text shouldBe "A"
        (ordered[1].element as Element.Text).text shouldBe "B"
        ordered[0].isDeferred shouldBe false
        ordered[1].isDeferred shouldBe false
    }

    test("candidate followed by primary is NOT deferred (section header)") {
        val blocks = listOf(
            makeBlock("header", gap = 3),   // candidate, but followed by primary
            makeBlock("content", gap = 0),   // primary
        )
        val ordered = orderer.order(blocks)
        ordered shouldHaveSize 2
        (ordered[0].element as Element.Text).text shouldBe "header"
        ordered[0].isDeferred shouldBe false
        (ordered[1].element as Element.Text).text shouldBe "content"
        ordered[1].isDeferred shouldBe false
    }

    test("candidate at the end IS deferred (footnote)") {
        val blocks = listOf(
            makeBlock("content", gap = 0),
            makeBlock("footnote", gap = 5),  // candidate, last block → deferred
        )
        val ordered = orderer.order(blocks)
        ordered shouldHaveSize 2
        (ordered[0].element as Element.Text).text shouldBe "content"
        ordered[0].isDeferred shouldBe false
        (ordered[1].element as Element.Text).text shouldBe "footnote"
        ordered[1].isDeferred shouldBe true
    }

    test("mixed: header kept in place, footnote deferred") {
        val blocks = listOf(
            makeBlock("header", gap = 3),    // candidate + followed by primary → NOT deferred
            makeBlock("table", gap = 0),     // primary
            makeBlock("footnote", gap = 5),  // candidate + last → deferred
        )
        val ordered = orderer.order(blocks)
        ordered shouldHaveSize 3
        (ordered[0].element as Element.Text).text shouldBe "header"
        ordered[0].isDeferred shouldBe false
        (ordered[1].element as Element.Text).text shouldBe "table"
        ordered[1].isDeferred shouldBe false
        (ordered[2].element as Element.Text).text shouldBe "footnote"
        ordered[2].isDeferred shouldBe true
    }

    test("gap of 1 is not deferred") {
        val blocks = listOf(
            makeBlock("A", gap = 0),
            makeBlock("B", gap = 1),
        )
        val ordered = orderer.order(blocks)
        ordered[1].isDeferred shouldBe false
    }

    test("all blocks are candidates → all deferred (no primary to anchor them)") {
        val blocks = listOf(
            makeBlock("A", gap = 3),
            makeBlock("B", gap = 3),
            makeBlock("C", gap = 3),
        )
        val ordered = orderer.order(blocks)
        ordered shouldHaveSize 3
        ordered.all { it.isDeferred } shouldBe true
    }

    test("preserves relative order: section headers stay, footnotes move") {
        val blocks = listOf(
            makeBlock("P1", gap = 0),
            makeBlock("Header", gap = 3),  // followed by P2 → not deferred
            makeBlock("P2", gap = 1),
            makeBlock("Footnote", gap = 4), // followed by P3 → not deferred
            makeBlock("P3", gap = 0),
            makeBlock("Appendix", gap = 5), // last → deferred
        )
        val ordered = orderer.order(blocks)
        val primaryTexts = ordered.filter { !it.isDeferred }.map { (it.element as Element.Text).text }
        val deferredTexts = ordered.filter { it.isDeferred }.map { (it.element as Element.Text).text }

        primaryTexts shouldBe listOf("P1", "Header", "P2", "Footnote", "P3")
        deferredTexts shouldBe listOf("Appendix")
    }
})
