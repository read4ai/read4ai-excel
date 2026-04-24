package ai.read4ai.excel.strategy

import ai.read4ai.excel.ExcelConfig
import org.apache.poi.ss.usermodel.Workbook

/**
 * Reads raw file bytes into an Apache POI [Workbook].
 *
 * This is the first step of the parse flow.
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
    fun read(data: ByteArray, config: ExcelConfig): Workbook
}
