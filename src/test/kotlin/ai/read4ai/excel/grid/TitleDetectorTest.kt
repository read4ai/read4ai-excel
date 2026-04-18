package ai.read4ai.excel.grid

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class TitleDetectorTest : FunSpec({

    context("isLikelyTitleText") {

        test("bullet \u25FE prefix is title") {
            TitleDetector.isLikelyTitleText("\u25FE Summary").shouldBeTrue()
        }

        test("bullet \u25AA prefix is title") {
            TitleDetector.isLikelyTitleText("\u25AA Overview").shouldBeTrue()
        }

        test("bullet \u2022 prefix is title") {
            TitleDetector.isLikelyTitleText("\u2022 Details").shouldBeTrue()
        }

        test("middle dot \u00B7 prefix is title") {
            TitleDetector.isLikelyTitleText("\u00B7 Section").shouldBeTrue()
        }

        test("dash prefix is title") {
            TitleDetector.isLikelyTitleText("- Section Title").shouldBeTrue()
        }

        test("year pattern 2024 is title") {
            TitleDetector.isLikelyTitleText("2024\uB144").shouldBeTrue()
        }

        test("year pattern 2023 is title") {
            TitleDetector.isLikelyTitleText("2023\uB144 \uC608\uC0B0").shouldBeTrue()
        }

        test("year pattern 1990 is title") {
            TitleDetector.isLikelyTitleText("1990\uB144").shouldBeTrue()
        }

        test("\uD328\uB110 keyword is title when short") {
            TitleDetector.isLikelyTitleText("\uD328\uB110 A").shouldBeTrue()
        }

        test("tilde prefix is title") {
            TitleDetector.isLikelyTitleText("~ Subtitle").shouldBeTrue()
        }

        test("\uB144 prefix is title") {
            TitleDetector.isLikelyTitleText("\uB144\uB3C4\uBCC4").shouldBeTrue()
        }

        test("empty string is not title") {
            TitleDetector.isLikelyTitleText("").shouldBeFalse()
        }

        test("whitespace only is not title") {
            TitleDetector.isLikelyTitleText("   ").shouldBeFalse()
        }

        test("plain text is not title") {
            TitleDetector.isLikelyTitleText("Hello World").shouldBeFalse()
        }

        test("number not matching year pattern is not title") {
            TitleDetector.isLikelyTitleText("12345").shouldBeFalse()
        }
    }

    context("detectTitle") {

        test("returns null for empty rows") {
            TitleDetector.detectTitle(emptyList()).shouldBeNull()
        }

        test("returns null when no title pattern found") {
            val rows = listOf(
                listOf("Name", "Age", "City"),
                listOf("Alice", "30", "Seoul"),
            )
            TitleDetector.detectTitle(rows).shouldBeNull()
        }

        test("detects bullet title in first row") {
            val rows = listOf(
                listOf("\u25FE Revenue Report"),
                listOf("Q1", "Q2", "Q3"),
            )
            val result = TitleDetector.detectTitle(rows)
            result.shouldNotBeNull()
            result.titleText shouldBe "\u25FE Revenue Report"
        }

        test("detects title and removes title cell from remaining rows") {
            val rows = listOf(
                listOf("\u25FE Report", ""),
                listOf("A", "B"),
            )
            val result = TitleDetector.detectTitle(rows)
            result.shouldNotBeNull()
            result.titleText shouldBe "\u25FE Report"
            // Title row should have the title cell blanked out,
            // and if all blank, the row is removed
            result.remainingRows.forEach { row ->
                row.any { it.isNotBlank() }.shouldBeTrue()
            }
        }

        test("detects year-based title") {
            val rows = listOf(
                listOf("2024\uB144 \uC608\uC0B0"),
                listOf("Item", "Amount"),
                listOf("Salary", "50000"),
            )
            val result = TitleDetector.detectTitle(rows)
            result.shouldNotBeNull()
            result.titleText shouldBe "2024\uB144 \uC608\uC0B0"
        }

        test("only checks first 2 rows") {
            val rows = listOf(
                listOf("Name", "Value"),
                listOf("A", "B"),
                listOf("\u25FE Hidden Title"),
            )
            TitleDetector.detectTitle(rows).shouldBeNull()
        }
    }

    context("normalizeTitleText") {

        test("trims whitespace") {
            TitleDetector.normalizeTitleText("  Hello  ") shouldBe "Hello"
        }

        test("preserves content") {
            TitleDetector.normalizeTitleText("\u25FE Title") shouldBe "\u25FE Title"
        }
    }

    context("formatTitle") {

        test("empty text returns empty") {
            TitleDetector.formatTitle("") shouldBe ""
        }

        test("whitespace only returns empty") {
            TitleDetector.formatTitle("   ") shouldBe ""
        }

        test("text without bullet gets \u25FE prefix") {
            TitleDetector.formatTitle("Summary") shouldBe "\u25FE Summary"
        }

        test("text with \u25FE prefix is not double-prefixed") {
            TitleDetector.formatTitle("\u25FE Summary") shouldBe "\u25FE Summary"
        }

        test("text with \u25AA prefix is not double-prefixed") {
            TitleDetector.formatTitle("\u25AA Overview") shouldBe "\u25AA Overview"
        }

        test("text with \u2022 prefix is not double-prefixed") {
            TitleDetector.formatTitle("\u2022 Details") shouldBe "\u2022 Details"
        }

        test("text with dash prefix is not double-prefixed") {
            TitleDetector.formatTitle("- Section") shouldBe "- Section"
        }
    }
})
