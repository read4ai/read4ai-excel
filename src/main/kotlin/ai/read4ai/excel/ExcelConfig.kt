package ai.read4ai.excel

/**
 * Configuration for [ExcelParser].
 *
 * @property maxRecordSize maximum byte-array size for POI record handling (default 100 MB)
 * @property maxConcurrentImageRequests maximum parallel image processing requests
 * @property imageOutput how embedded images should be handled in the output
 * @property hybridConfig optional callback configuration for AI-assisted image description
 */
data class ExcelConfig(
    val maxRecordSize: Int = 100_000_000,
    val maxConcurrentImageRequests: Int = 16,
    val imageOutput: ImageOutput = ImageOutput.SKIP,
    val hybridConfig: HybridConfig? = null,
) {
    /** Controls how embedded images are represented in the parsed output. */
    enum class ImageOutput {
        /** Skip all images. */
        SKIP,
        /** Include image as inline base64-encoded data. */
        BASE64,
        /** Use [HybridConfig.describeImage] callback to generate text descriptions. */
        HYBRID,
    }

    /** Configuration for hybrid image-to-text processing. */
    data class HybridConfig(
        val describeImage: (base64: String, mimeType: String) -> String,
    )
}
