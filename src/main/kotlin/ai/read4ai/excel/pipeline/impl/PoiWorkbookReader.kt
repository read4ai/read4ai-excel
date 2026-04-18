package ai.read4ai.excel.pipeline.impl

import ai.read4ai.excel.Config
import ai.read4ai.excel.ExcelParseException
import ai.read4ai.excel.grid.WorkbookLoader
import ai.read4ai.excel.pipeline.WorkbookReader
import org.apache.poi.EncryptedDocumentException
import org.apache.poi.ss.usermodel.Workbook

/**
 * Default [WorkbookReader] that delegates to [WorkbookLoader] with relaxed ZIP security.
 *
 * This reproduces the existing ExcelParser behavior.
 */
class PoiWorkbookReader : WorkbookReader {

    override fun read(data: ByteArray, config: Config): Workbook {
        val loader = WorkbookLoader(config)
        return try {
            loader.openWorkbook(data, useRelaxedZipSecurity = true)
        } catch (e: EncryptedDocumentException) {
            throw ExcelParseException.EncryptedFile(cause = e)
        } catch (e: Exception) {
            throw ExcelParseException.InvalidFormat(
                message = "Failed to open workbook: ${e.message}",
                cause = e,
            )
        }
    }
}
