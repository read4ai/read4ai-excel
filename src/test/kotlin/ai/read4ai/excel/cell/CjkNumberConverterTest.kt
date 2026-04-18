package ai.read4ai.excel.cell

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CjkNumberConverterTest : FunSpec({

    context("digitToCJK") {

        test("converts 0 to \u96F6") {
            CjkNumberConverter.digitToCJK('0') shouldBe '\u96F6'
        }

        test("converts 1 to \u4E00") {
            CjkNumberConverter.digitToCJK('1') shouldBe '\u4E00'
        }

        test("converts 2 to \u4E8C") {
            CjkNumberConverter.digitToCJK('2') shouldBe '\u4E8C'
        }

        test("converts 3 to \u4E09") {
            CjkNumberConverter.digitToCJK('3') shouldBe '\u4E09'
        }

        test("converts 4 to \u56DB") {
            CjkNumberConverter.digitToCJK('4') shouldBe '\u56DB'
        }

        test("converts 5 to \u4E94") {
            CjkNumberConverter.digitToCJK('5') shouldBe '\u4E94'
        }

        test("converts 6 to \u516D") {
            CjkNumberConverter.digitToCJK('6') shouldBe '\u516D'
        }

        test("converts 7 to \u4E03") {
            CjkNumberConverter.digitToCJK('7') shouldBe '\u4E03'
        }

        test("converts 8 to \u516B") {
            CjkNumberConverter.digitToCJK('8') shouldBe '\u516B'
        }

        test("converts 9 to \u4E5D") {
            CjkNumberConverter.digitToCJK('9') shouldBe '\u4E5D'
        }

        test("non-digit character passes through unchanged") {
            CjkNumberConverter.digitToCJK('A') shouldBe 'A'
            CjkNumberConverter.digitToCJK('.') shouldBe '.'
        }
    }

    context("toCJKNumber") {

        test("zero returns \u96F6") {
            CjkNumberConverter.toCJKNumber(0) shouldBe "\u96F6"
        }

        test("single digit 1 returns \u4E00") {
            CjkNumberConverter.toCJKNumber(1) shouldBe "\u4E00"
        }

        test("single digit 9 returns \u4E5D") {
            CjkNumberConverter.toCJKNumber(9) shouldBe "\u4E5D"
        }

        test("10 returns \u5341 (without leading \u4E00)") {
            CjkNumberConverter.toCJKNumber(10) shouldBe "\u5341"
        }

        test("11 returns \u5341\u4E00") {
            CjkNumberConverter.toCJKNumber(11) shouldBe "\u5341\u4E00"
        }

        test("19 returns \u5341\u4E5D") {
            CjkNumberConverter.toCJKNumber(19) shouldBe "\u5341\u4E5D"
        }

        test("20 returns \u4E8C\u5341") {
            CjkNumberConverter.toCJKNumber(20) shouldBe "\u4E8C\u5341"
        }

        test("100 returns \u4E00\u767E") {
            CjkNumberConverter.toCJKNumber(100) shouldBe "\u4E00\u767E"
        }

        test("101 returns \u96F6\u4E00\u767E\u4E00") {
            CjkNumberConverter.toCJKNumber(101) shouldBe "\u96F6\u4E00\u767E\u4E00"
        }

        test("110 returns \u4E00\u767E\u4E00\u5341") {
            CjkNumberConverter.toCJKNumber(110) shouldBe "\u4E00\u767E\u4E00\u5341"
        }

        test("1000 returns \u4E00\u5343") {
            CjkNumberConverter.toCJKNumber(1000) shouldBe "\u4E00\u5343"
        }

        test("1001 returns \u96F6\u4E00\u5343\u4E00") {
            CjkNumberConverter.toCJKNumber(1001) shouldBe "\u96F6\u4E00\u5343\u4E00"
        }

        test("10000 returns \u4E00\u4E07") {
            CjkNumberConverter.toCJKNumber(10000) shouldBe "\u4E00\u4E07"
        }

        test("negative number returns \u8CA0 prefix") {
            CjkNumberConverter.toCJKNumber(-5) shouldBe "\u8CA0\u4E94"
        }

        test("negative 42 returns \u8CA0\u56DB\u5341\u4E8C") {
            CjkNumberConverter.toCJKNumber(-42) shouldBe "\u8CA0\u56DB\u5341\u4E8C"
        }

        test("2025 returns \u96F6\u4E8C\u5343\u4E8C\u5341\u4E94") {
            CjkNumberConverter.toCJKNumber(2025) shouldBe "\u96F6\u4E8C\u5343\u4E8C\u5341\u4E94"
        }

        test("large number with \u5104 unit") {
            // 100,000,000 = 1\u5104
            CjkNumberConverter.toCJKNumber(100_000_000) shouldBe "\u4E00\u5104"
        }
    }
})
