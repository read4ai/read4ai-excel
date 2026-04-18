package ai.read4ai.excel.image

import ai.read4ai.excel.cell.TextSanitizer
import org.apache.poi.hssf.usermodel.*
import org.apache.poi.ss.usermodel.Picture
import org.apache.poi.xssf.usermodel.*

internal object PictureCollector {

    data class ShapeTextEntry(
        val shape: Any,
        val text: String,
    )

    fun collectPicturesXssf(drawing: XSSFDrawing): List<Picture> {
        fun expand(list: List<XSSFShape>): List<Picture> {
            val out = mutableListOf<Picture>()
            for (s in list) {
                when (s) {
                    is XSSFPicture -> out.add(s)
                    is XSSFShapeGroup -> out.addAll(expand(xssfGroupChildren(s)))
                }
            }
            return out
        }
        return expand(drawing.shapes)
    }

    fun xssfGroupChildren(group: XSSFShapeGroup): List<XSSFShape> {
        return try {
            val m = group.javaClass.getMethod("getShapes")
            @Suppress("UNCHECKED_CAST")
            m.invoke(group) as? List<XSSFShape> ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun collectPicturesHssf(patriarch: HSSFPatriarch): List<Picture> {
        fun expand(list: List<HSSFShape>): List<Picture> {
            val out = mutableListOf<Picture>()
            for (s in list) {
                when (s) {
                    is Picture -> out.add(s)
                    is HSSFShapeGroup -> out.addAll(expand(s.children))
                }
            }
            return out
        }
        return expand(patriarch.children)
    }

    fun collectShapeTextEntries(drawing: XSSFDrawing): List<ShapeTextEntry> {
        val out = mutableListOf<ShapeTextEntry>()

        fun expand(shapes: List<XSSFShape>) {
            for (shape in shapes) {
                when (shape) {
                    is XSSFShapeGroup -> expand(xssfGroupChildren(shape))
                    is XSSFPicture -> continue
                    is XSSFSimpleShape -> {
                        val text = extractShapeText(shape)
                        if (text != null) out.add(ShapeTextEntry(shape, text))
                    }
                }
            }
        }

        expand(drawing.shapes)
        return out
    }

    fun collectShapeTextEntries(patriarch: HSSFPatriarch): List<ShapeTextEntry> {
        val out = mutableListOf<ShapeTextEntry>()

        fun expand(shapes: List<HSSFShape>) {
            for (shape in shapes) {
                when (shape) {
                    is HSSFShapeGroup -> expand(shape.children)
                    is HSSFPicture -> continue
                    is HSSFSimpleShape -> {
                        val text = extractShapeText(shape)
                        if (text != null) out.add(ShapeTextEntry(shape, text))
                    }
                }
            }
        }

        expand(patriarch.children)
        return out
    }

    private fun extractShapeText(shape: XSSFSimpleShape): String? {
        val paragraphText = runCatching {
            shape.textParagraphs.joinToString(separator = "\n") { para ->
                para.textRuns.joinToString(separator = "") { run -> run.text ?: "" }
            }
        }.getOrNull()

        val raw = when {
            !paragraphText.isNullOrBlank() -> paragraphText
            else -> runCatching { shape.text }.getOrNull()
        }?.trim()

        if (raw.isNullOrBlank()) return null
        val sanitized = TextSanitizer.sanitizeOutput(raw).trim()
        return sanitized.takeIf { it.isNotEmpty() }
    }

    private fun extractShapeText(shape: HSSFSimpleShape): String? {
        val raw = runCatching { shape.string?.string }.getOrNull()?.trim()
        if (raw.isNullOrBlank()) return null
        val sanitized = TextSanitizer.sanitizeOutput(raw).trim()
        return sanitized.takeIf { it.isNotEmpty() }
    }
}
