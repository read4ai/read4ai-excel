package ai.read4ai.excel.image

import org.apache.poi.hssf.usermodel.HSSFPicture
import org.apache.poi.openxml4j.opc.PackageRelationship
import org.apache.poi.openxml4j.opc.PackagingURIHelper
import org.apache.poi.openxml4j.opc.TargetMode
import org.apache.poi.ss.usermodel.Picture
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFDrawing
import org.apache.poi.xssf.usermodel.XSSFPicture
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

internal object ImageExtractor {

    fun extractPictureBytes(pic: Picture, sheet: Sheet): Triple<String?, String?, ByteArray?> {
        return when (pic) {
            is XSSFPicture -> {
                val pd = runCatching { pic.pictureData }.getOrNull()
                if (pd != null) {
                    Triple(
                        runCatching { pd.suggestFileExtension() }.getOrNull(),
                        runCatching { pd.mimeType }.getOrNull(),
                        runCatching { pd.data }.getOrNull()
                    )
                } else {
                    val blip = runCatching { pic.ctPicture.blipFill.blip }.getOrNull()
                    val embed = runCatching { blip?.embed }.getOrNull()
                    val drawing = sheet.drawingPatriarch as? XSSFDrawing
                    if (!embed.isNullOrBlank() && drawing != null) resolveEmbeddedImageFromRelationship(drawing, embed)
                    else Triple(null, null, null)
                }
            }

            is HSSFPicture -> {
                val pd = runCatching { pic.pictureData }.getOrNull()
                Triple(
                    runCatching { pd?.suggestFileExtension() }.getOrNull(),
                    runCatching { pd?.mimeType }.getOrNull(),
                    runCatching { pd?.data }.getOrNull()
                )
            }

            else -> Triple(null, null, runCatching { pic.pictureData?.data }.getOrNull())
        }
    }

    fun pictureToBufferedImage(
        ext: String?,
        mime: String?,
        raw: ByteArray,
    ): BufferedImage? {
        val e = ext?.lowercase() ?: when {
            mime?.contains("png", true) == true -> "png"
            mime?.contains("jpeg", true) == true || mime?.contains("jpg", true) == true -> "jpg"
            mime?.contains("gif", true) == true -> "gif"
            mime?.contains("bmp", true) == true -> "bmp"
            mime?.contains("dib", true) == true -> "dib"
            mime?.contains("tiff", true) == true -> "tiff"
            mime?.contains("emf", true) == true -> "emf"
            mime?.contains("wmf", true) == true -> "wmf"
            else -> null
        }

        return when (e) {
            "png", "jpg", "jpeg", "gif", "bmp", "tif", "tiff" ->
                runCatching { ImageIO.read(ByteArrayInputStream(raw)) }.getOrNull()

            "dib" -> {
                val bmpBytes = DibBmpConverter.convertDibToBmp(raw)
                if (bmpBytes != null) runCatching { ImageIO.read(ByteArrayInputStream(bmpBytes)) }.getOrNull() else null
            }

            "emf" -> EmfWmfRenderer.renderEmf(raw)
            "wmf" -> EmfWmfRenderer.renderWmf(raw)
            "pict" -> null
            else -> runCatching { ImageIO.read(ByteArrayInputStream(raw)) }.getOrNull()
        }
    }

    private fun resolveEmbeddedImageFromRelationship(
        drawing: XSSFDrawing,
        rId: String
    ): Triple<String?, String?, ByteArray?> {
        return try {
            val relIter = drawing.packagePart.relationships.iterator()
            var targetRel: PackageRelationship? = null
            while (relIter.hasNext()) {
                val rel = relIter.next()
                if (rel.id == rId) {
                    targetRel = rel; break
                }
            }
            if (targetRel == null) return Triple(null, null, null)
            if (targetRel.targetMode == TargetMode.EXTERNAL) {
                return Triple(null, null, null)
            }
            val sourceUri = drawing.packagePart.partName.uri
            val resolvedUri =
                PackagingURIHelper.resolvePartUri(sourceUri, targetRel.targetURI)
            val partName = PackagingURIHelper.createPartName(resolvedUri)
            val pkg = drawing.packagePart.`package`
            val part = pkg.getPart(partName) ?: return Triple(null, null, null)
            val bytes = part.getInputStream().use { inputStream -> inputStream.readBytes() }
            val contentType = part.contentType
            val ext = when {
                contentType.contains("png") -> "png"
                contentType.contains("jpeg") || contentType.contains("jpg") -> "jpg"
                contentType.contains("gif") -> "gif"
                contentType.contains("bmp") -> "bmp"
                contentType.contains("tiff") -> "tiff"
                contentType.contains("emf") -> "emf"
                contentType.contains("wmf") -> "wmf"
                else -> null
            }
            Triple(ext, contentType, bytes)
        } catch (_: Throwable) {
            Triple(null, null, null)
        }
    }
}
