package ai.read4ai.excel.grid

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.poi.ss.util.CellRangeAddress

internal object MarkdownRenderer {

    private val log = KotlinLogging.logger {}

    data class MarkdownTableResult(
        val markdownTable: String,
        val headerRows: List<List<String>>,
        val headerStartIndex: Int,
    )

    /**
     * Convert a grid to a markdown table.
     * Determines headers from the first non-empty row and merged regions.
     */
    fun toMarkdownTableWithHeaders(
        grid: List<List<String>>,
        mergedRegions: List<CellRangeAddress>,
        escapePipe: Boolean = true,
    ): MarkdownTableResult {
        if (grid.isEmpty()) return MarkdownTableResult("*Empty sheet*\n", emptyList(), 0)

        val colCount = grid.maxOfOrNull { it.size } ?: 0
        if (colCount == 0 && grid.isNotEmpty()) {
            return MarkdownTableResult("*Empty table*\n", emptyList(), 0)
        }

        val processedGridSequence = grid.asSequence().map { row ->
            row.map { cell ->
                val pipeEscaped = if (escapePipe) cell.replace("|", "\\|") else cell
                pipeEscaped
                    .replace("\r\n", "<br>")
                    .replace("\n", "<br>")
                    .replace("\r", "<br>")
            }
        }

        val processedGrid = processedGridSequence.toList()

        val firstNonEmptyRowIndex = processedGrid.indexOfFirst { !SpatialSegmenter.isRowEmpty(it) }

        val (actualHeaderStartIndex, collectedHeaderRows, collectedHeaderIndicesInProcessedGrid) = when {
            firstNonEmptyRowIndex != -1 -> {
                val headerRows = mutableListOf(processedGrid[firstNonEmptyRowIndex])
                val headerIndices = mutableListOf(firstNonEmptyRowIndex)

                val startRowIndex = firstNonEmptyRowIndex
                val verticalMergedRows = GridExtractor.findVerticallyMergedRows(startRowIndex, mergedRegions, processedGrid.size)

                verticalMergedRows.forEach { rowIndex ->
                    if (rowIndex < processedGrid.size && rowIndex > startRowIndex) {
                        headerRows.add(processedGrid[rowIndex])
                        headerIndices.add(rowIndex)
                    }
                }

                log.debug {
                    "Header starts at grid row $firstNonEmptyRowIndex, collected ${headerRows.size} header row(s)."
                }

                Triple(firstNonEmptyRowIndex, headerRows, headerIndices)
            }

            else -> {
                log.debug {
                    "No non-empty rows found."
                }
                Triple(0, mutableListOf<List<String>>(), mutableListOf<Int>())
            }
        }

        val finalHeaderRows = if (collectedHeaderRows.isEmpty() && colCount > 0) {
            val defaultHeader = List(colCount) { "Column ${it + 1}" }
            log.debug {
                "No specific headers found, using generated default header."
            }
            mutableListOf(defaultHeader)
        } else {
            fillEmptyHeaderCells(collectedHeaderRows, colCount)
        }

        val formatRowToMarkdown = { row: List<String> ->
            row.let {
                if (it.size < colCount) it + List(colCount - it.size) { "" } else it
            }.take(colCount).joinToString(" | ", prefix = "| ", postfix = " |")
        }

        val headerMarkdown = finalHeaderRows.joinToString("\n") { formatRowToMarkdown(it) }

        val bodyMarkdown = processedGrid.asSequence()
            .filterIndexed { index, row ->
                index !in collectedHeaderIndicesInProcessedGrid && !SpatialSegmenter.isRowEmpty(row)
            }
            .joinToString("\n") { formatRowToMarkdown(it) }

        val markdownTable = when {
            headerMarkdown.isNotEmpty() || bodyMarkdown.isNotEmpty() -> {
                val parts = mutableListOf<String>()
                if (headerMarkdown.isNotEmpty()) parts.add(headerMarkdown)
                if (bodyMarkdown.isNotEmpty()) parts.add(bodyMarkdown)
                parts.joinToString("\n")
            }

            grid.isNotEmpty() -> {
                List(colCount) { "" }.joinToString(" | ", prefix = "| ", postfix = " |")
            }

            else -> ""
        }

        return MarkdownTableResult(
            markdownTable = if (markdownTable.isEmpty()) markdownTable else "$markdownTable\n",
            headerRows = finalHeaderRows,
            headerStartIndex = actualHeaderStartIndex,
        )
    }

    /**
     * Fill empty header cells with "empty header N" placeholders.
     */
    fun fillEmptyHeaderCells(
        headerRows: List<List<String>>,
        colCount: Int,
    ): MutableList<List<String>> {
        if (headerRows.isEmpty()) return mutableListOf()

        var emptyHeaderCounter = 1
        val filledHeaderRows = mutableListOf<List<String>>()

        val columnFilledStatus = mutableMapOf<Int, Boolean>()

        headerRows.forEach { row ->
            val filledRow = row.mapIndexed { colIndex, cell ->
                when {
                    columnFilledStatus[colIndex] == true -> cell
                    cell.isBlank() -> {
                        columnFilledStatus[colIndex] = true
                        "empty header ${emptyHeaderCounter++}"
                    }
                    else -> {
                        columnFilledStatus[colIndex] = true
                        cell
                    }
                }
            }

            val finalRow = if (filledRow.size < colCount) {
                filledRow + (filledRow.size until colCount).map { colIndex ->
                    if (columnFilledStatus[colIndex] != true) {
                        columnFilledStatus[colIndex] = true
                        "empty header ${emptyHeaderCounter++}"
                    } else {
                        ""
                    }
                }
            } else {
                filledRow
            }

            filledHeaderRows.add(finalRow)
        }

        return filledHeaderRows
    }
}
