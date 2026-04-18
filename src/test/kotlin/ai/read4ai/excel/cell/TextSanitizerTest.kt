package ai.read4ai.excel.cell

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

class TextSanitizerTest : FunSpec({

    test("normal text passes through unchanged") {
        TextSanitizer.sanitizeOutput("Hello World") shouldBe "Hello World"
    }

    test("trims leading and trailing whitespace") {
        TextSanitizer.sanitizeOutput("  Hello  ") shouldBe "Hello"
    }

    test("removes null character U+0000") {
        TextSanitizer.sanitizeOutput("Hello\u0000World") shouldBe "HelloWorld"
    }

    test("removes BEL character U+0007") {
        TextSanitizer.sanitizeOutput("Hello\u0007World") shouldBe "HelloWorld"
    }

    test("removes backspace U+0008") {
        TextSanitizer.sanitizeOutput("Hello\u0008World") shouldBe "HelloWorld"
    }

    test("removes vertical tab U+000B") {
        TextSanitizer.sanitizeOutput("Hello\u000BWorld") shouldBe "HelloWorld"
    }

    test("removes form feed U+000C") {
        TextSanitizer.sanitizeOutput("Hello\u000CWorld") shouldBe "HelloWorld"
    }

    test("preserves newline U+000A") {
        TextSanitizer.sanitizeOutput("Hello\nWorld") shouldBe "Hello\nWorld"
    }

    test("preserves carriage return U+000D") {
        TextSanitizer.sanitizeOutput("Hello\rWorld") shouldBe "Hello\rWorld"
    }

    test("preserves tab U+0009") {
        TextSanitizer.sanitizeOutput("Hello\tWorld") shouldBe "Hello\tWorld"
    }

    test("removes zero-width space U+200B") {
        TextSanitizer.sanitizeOutput("Hello\u200BWorld") shouldBe "HelloWorld"
    }

    test("removes zero-width non-joiner U+200C") {
        TextSanitizer.sanitizeOutput("Hello\u200CWorld") shouldBe "HelloWorld"
    }

    test("removes zero-width joiner U+200D") {
        TextSanitizer.sanitizeOutput("Hello\u200DWorld") shouldBe "HelloWorld"
    }

    test("removes word joiner U+2060") {
        TextSanitizer.sanitizeOutput("Hello\u2060World") shouldBe "HelloWorld"
    }

    test("removes BOM U+FEFF") {
        TextSanitizer.sanitizeOutput("\uFEFFHello") shouldBe "Hello"
    }

    test("removes soft hyphen U+00AD") {
        TextSanitizer.sanitizeOutput("Hello\u00ADWorld") shouldBe "HelloWorld"
    }

    test("removes DEL U+007F") {
        TextSanitizer.sanitizeOutput("Hello\u007FWorld") shouldBe "HelloWorld"
    }

    test("removes reserved placeholder patterns") {
        TextSanitizer.sanitizeOutput("reserved-1x0a") shouldBe ""
        TextSanitizer.sanitizeOutput("text reserved-42x1f text") shouldBe "text  text"
    }

    test("removes multiple hidden characters at once") {
        val dirty = "\u200B\u200CHello\u0000\u0007World\uFEFF"
        val result = TextSanitizer.sanitizeOutput(dirty)
        result shouldBe "HelloWorld"
        result shouldNotContain "\u200B"
        result shouldNotContain "\u0000"
        result shouldNotContain "\uFEFF"
    }

    test("empty string returns empty") {
        TextSanitizer.sanitizeOutput("") shouldBe ""
    }

    test("string with only hidden characters returns empty") {
        TextSanitizer.sanitizeOutput("\u200B\u200C\u200D") shouldBe ""
    }

    test("preserves Korean characters") {
        TextSanitizer.sanitizeOutput("\uD55C\uAD6D\uC5B4 \uD14C\uC2A4\uD2B8") shouldBe "\uD55C\uAD6D\uC5B4 \uD14C\uC2A4\uD2B8"
    }

    test("preserves CJK characters") {
        TextSanitizer.sanitizeOutput("\u4E00\u4E8C\u4E09") shouldBe "\u4E00\u4E8C\u4E09"
    }
})
