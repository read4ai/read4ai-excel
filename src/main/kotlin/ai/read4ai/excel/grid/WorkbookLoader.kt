package ai.read4ai.excel.grid

import ai.read4ai.excel.Config
import org.apache.poi.openxml4j.util.ZipSecureFile
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.util.IOUtils
import java.io.ByteArrayInputStream

internal class WorkbookLoader(
    private val config: Config = Config(),
) {

    fun openWorkbook(excelBytes: ByteArray, useRelaxedZipSecurity: Boolean = true): Workbook {
        val createWorkbook = {
            ByteArrayInputStream(excelBytes).use { inputStream ->
                WorkbookFactory.create(inputStream)
            }
        }

        return if (useRelaxedZipSecurity) {
            withRelaxedZipSecureSettings(createWorkbook)
        } else {
            createWorkbook()
        }
    }

    /**
     * Temporarily relax ZIP security settings for large Excel/Office files.
     *
     * POI imposes limits to prevent Zip bomb attacks:
     * - MinInflateRatio: decompression ratio limit (default: 0.01)
     * - MaxFileCount: ZIP internal entry count limit (default: 1000)
     *
     * Legitimate large files (many images, charts, sheets) can hit these limits,
     * so we temporarily relax them during processing and restore afterward.
     */
    private fun <T> withRelaxedZipSecureSettings(action: () -> T): T {
        synchronized(zipSecureSettingsLock) {
            IOUtils.setByteArrayMaxOverride(config.maxRecordSize)

            val originalMinInflateRatio = ZipSecureFile.getMinInflateRatio()
            val originalMaxFileCount = ZipSecureFile.getMaxFileCount()

            if (originalMinInflateRatio <= RELAXED_MIN_INFLATE_RATIO &&
                originalMaxFileCount >= RELAXED_MAX_FILE_COUNT
            ) {
                return action()
            }

            return try {
                ZipSecureFile.setMinInflateRatio(RELAXED_MIN_INFLATE_RATIO)
                ZipSecureFile.setMaxFileCount(RELAXED_MAX_FILE_COUNT)
                action()
            } finally {
                ZipSecureFile.setMinInflateRatio(originalMinInflateRatio)
                ZipSecureFile.setMaxFileCount(originalMaxFileCount)
            }
        }
    }

    private companion object {
        private const val RELAXED_MIN_INFLATE_RATIO = 0.001
        private const val RELAXED_MAX_FILE_COUNT = 100_000L
        private val zipSecureSettingsLock = Any()
    }
}
