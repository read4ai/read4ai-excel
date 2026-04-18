package ai.read4ai.excel.cell

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank

class DateFormatterTest : FunSpec({

    context("isUnprocessedDatePattern") {

        test("detects yyyy pattern") {
            DateFormatter.isUnprocessedDatePattern("yyyy-mm-dd").shouldBeTrue()
        }

        test("detects yy pattern") {
            DateFormatter.isUnprocessedDatePattern("yy/mm/dd").shouldBeTrue()
        }

        test("detects mm-dd pattern") {
            DateFormatter.isUnprocessedDatePattern("mm-dd").shouldBeTrue()
        }

        test("detects hh:mm:ss pattern") {
            DateFormatter.isUnprocessedDatePattern("hh:mm:ss").shouldBeTrue()
        }

        test("detects case-insensitive patterns") {
            DateFormatter.isUnprocessedDatePattern("YYYY-MM-DD").shouldBeTrue()
        }

        test("rejects plain text") {
            DateFormatter.isUnprocessedDatePattern("Hello World").shouldBeFalse()
        }

        test("rejects numbers only") {
            DateFormatter.isUnprocessedDatePattern("12345").shouldBeFalse()
        }

        test("rejects empty string") {
            DateFormatter.isUnprocessedDatePattern("").shouldBeFalse()
        }

        test("detects Korean date keywords with date patterns") {
            DateFormatter.isUnprocessedDatePattern("yyyy\uB144 mm\uC6D4 dd\uC77C").shouldBeTrue()
        }
    }

    context("isDateFormatPattern") {

        test("recognizes yyyy-mm-dd") {
            DateFormatter.isDateFormatPattern("yyyy-mm-dd").shouldBeTrue()
        }

        test("recognizes dd/MM/yyyy") {
            DateFormatter.isDateFormatPattern("dd/MM/yyyy").shouldBeTrue()
        }

        test("recognizes hh:mm:ss") {
            DateFormatter.isDateFormatPattern("hh:mm:ss").shouldBeTrue()
        }

        test("rejects General format") {
            DateFormatter.isDateFormatPattern("General").shouldBeFalse()
        }

        test("rejects pure text like #,##0") {
            DateFormatter.isDateFormatPattern("#,##0").shouldBeFalse()
        }

        test("recognizes short date format") {
            DateFormatter.isDateFormatPattern("m/d/yy").shouldBeTrue()
        }

        test("Korean keywords alone do not match without date tokens") {
            // Korean keywords require datePatternRegex to also match
            DateFormatter.isDateFormatPattern("\uB144\uC6D4\uC77C").shouldBeFalse()
        }

        test("Korean keywords with date tokens match") {
            DateFormatter.isDateFormatPattern("yyyy\uB144 mm\uC6D4 dd\uC77C").shouldBeTrue()
        }
    }

    context("determineJavaDatePattern") {

        test("converts yyyy-mm-dd to yyyy-MM-dd") {
            val result = DateFormatter.determineJavaDatePattern("yyyy-mm-dd", null)
            result shouldBe "yyyy-MM-dd"
        }

        test("converts mm/dd/yyyy to MM/dd/yyyy") {
            val result = DateFormatter.determineJavaDatePattern("mm/dd/yyyy", null)
            result shouldBe "MM/dd/yyyy"
        }

        test("converts hh:mm:ss to HH:MM:ss") {
            val result = DateFormatter.determineJavaDatePattern("hh:mm:ss", null)
            result shouldContain "HH"
        }

        test("strips color codes") {
            val result = DateFormatter.determineJavaDatePattern("[Color1]yyyy-mm-dd", null)
            result shouldBe "yyyy-MM-dd"
        }

        test("strips locale codes") {
            val result = DateFormatter.determineJavaDatePattern("[\$-409]yyyy-mm-dd", null)
            result shouldBe "yyyy-MM-dd"
        }

        test("strips DBNum codes") {
            val result = DateFormatter.determineJavaDatePattern("[DBNum1]yyyy-mm-dd", null)
            result shouldBe "yyyy-MM-dd"
        }

        test("strips quotes") {
            val result = DateFormatter.determineJavaDatePattern("yyyy\"year\"mm\"month\"dd\"day\"", null)
            result shouldContain "yyyy"
            result shouldContain "dd"
        }

        test("converts AM/PM to a") {
            val result = DateFormatter.determineJavaDatePattern("hh:mm AM/PM", null)
            result shouldContain "a"
        }
    }

    context("processDateValue") {

        test("processes numeric date value to formatted string") {
            // 44927.0 = 2023-01-01 in Excel serial date
            val result = DateFormatter.processDateValue(
                numericValue = 44927.0,
                formattedValue = "yyyy-mm-dd",
                cellStyle = null,
                force = true,
            )
            result.shouldNotBeBlank()
            result shouldContain "2023"
        }

        test("returns formatted value unchanged when not a date pattern and not forced") {
            val result = DateFormatter.processDateValue(
                numericValue = 100.0,
                formattedValue = "Hello",
                cellStyle = null,
                force = false,
            )
            result shouldBe "Hello"
        }

        test("forced processing attempts date conversion") {
            val result = DateFormatter.processDateValue(
                numericValue = 44927.0,
                formattedValue = "General",
                cellStyle = null,
                force = true,
            )
            result.shouldNotBeBlank()
        }

        test("fallback to yyyy-MM-dd on pattern error") {
            val result = DateFormatter.processDateValue(
                numericValue = 44927.0,
                formattedValue = "invalid{{{pattern",
                cellStyle = null,
                force = true,
            )
            result.shouldNotBeBlank()
            result shouldContain "2023"
        }
    }
})
