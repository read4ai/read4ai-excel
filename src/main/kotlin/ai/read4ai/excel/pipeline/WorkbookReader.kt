package ai.read4ai.excel.pipeline

import ai.read4ai.excel.Config
import org.apache.poi.ss.usermodel.Workbook

/**
 * Reads raw file bytes into an Apache POI [Workbook].
 *
 * This is the first step of the parsing pipeline.
 * Implementations may vary in ZIP security settings, format support, etc.
 */
interface WorkbookReader {

    /**
     * Open and return a [Workbook] from raw file bytes.
     *
     * @param data raw Excel file content (XLSX or XLS)
     * @param config parsing configuration
     * @return the opened workbook (caller is responsible for closing)
     */
    fun read(data: ByteArray, config: Config): Workbook
}
