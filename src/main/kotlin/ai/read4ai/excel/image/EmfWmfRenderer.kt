package ai.read4ai.excel.image

import org.apache.poi.hemf.usermodel.HemfPicture
import org.apache.poi.hwmf.usermodel.HwmfPicture
import java.awt.RenderingHints
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream

internal object EmfWmfRenderer {

    fun renderEmf(raw: ByteArray): BufferedImage? {
        return try {
            val hemf = HemfPicture(ByteArrayInputStream(raw))
            val dim = runCatching { hemf.size }.getOrNull()
            val wPt = dim?.width ?: 400.0
            val hPt = dim?.height ?: 300.0
            val scale = 3.0
            var w = (wPt * scale).toInt().coerceAtLeast(1)
            var h = (hPt * scale).toInt().coerceAtLeast(1)
            val maxSide = 3000
            if (w > maxSide || h > maxSide) {
                val s = minOf(maxSide.toDouble() / w, maxSide.toDouble() / h)
                w = (w * s).toInt().coerceAtLeast(1)
                h = (h * s).toInt().coerceAtLeast(1)
            }
            val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            val g2 = img.createGraphics()
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            val rect = Rectangle2D.Double(0.0, 0.0, w.toDouble(), h.toDouble())
            run { hemf.draw(g2, rect) }
            g2.dispose()
            img
        } catch (_: Throwable) {
            null
        }
    }

    fun renderWmf(raw: ByteArray): BufferedImage? {
        return try {
            val wmf = HwmfPicture(ByteArrayInputStream(raw))
            val dim = runCatching { wmf.size }.getOrNull()
            val wPt = dim?.width ?: 400.0
            val hPt = dim?.height ?: 300.0
            val scale = 3.0
            var w = (wPt * scale).toInt().coerceAtLeast(1)
            var h = (hPt * scale).toInt().coerceAtLeast(1)
            val maxSide = 3000
            if (w > maxSide || h > maxSide) {
                val s = minOf(maxSide.toDouble() / w, maxSide.toDouble() / h)
                w = (w * s).toInt().coerceAtLeast(1)
                h = (h * s).toInt().coerceAtLeast(1)
            }
            val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            val g2 = img.createGraphics()
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            val rect = Rectangle2D.Double(0.0, 0.0, w.toDouble(), h.toDouble())
            run { wmf.draw(g2, rect) }
            g2.dispose()
            img
        } catch (_: Throwable) {
            null
        }
    }
}
