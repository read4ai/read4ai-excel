package ai.read4ai.excel.output

import ai.read4ai.excel.ExperimentalRead4ai
import ai.read4ai.excel.lang.AssistPrompt
import ai.read4ai.excel.model.Element
import ai.read4ai.excel.model.ExcelDocument
import ai.read4ai.excel.model.Sheet

/**
 * Short, format-aware system-prompt-like text used by [Assist.ON].
 *
 * Two layers:
 * - **root** describes the whole envelope (sheets array, language)
 * - **sheet** describes how to read that sheet's data (1-based indices,
 *   merge notation; merge guidance is only emitted when the sheet
 *   actually has merges)
 *
 * Kept brief to minimise prompt length and avoid the LLM treating the
 * notes as separate instructions.
 */
internal object PromptText {

    // ------------------------------------------------------------------
    // Root prompt (document-wide)
    // ------------------------------------------------------------------

    fun rootJsonCompact(document: ExcelDocument): String = buildString {
        appendLine(AssistPrompt.from(document))
        append(ROOT_JSON_COMPACT)
    }

    @OptIn(ExperimentalRead4ai::class)
    fun rootJsonRowObject(document: ExcelDocument): String = buildString {
        appendLine(AssistPrompt.from(document))
        append(ROOT_JSON_ROW_OBJECT)
    }

    fun rootMarkdown(document: ExcelDocument): String = buildString {
        appendLine(AssistPrompt.from(document))
        append(ROOT_MARKDOWN)
    }

    // ------------------------------------------------------------------
    // Sheet prompt (per-sheet, mergeRegions-aware)
    // ------------------------------------------------------------------

    fun sheetJsonCompact(sheet: Sheet): String = buildString {
        append(SHEET_JSON_BASE)
        if (sheet.elements.any { it is Element.Table && it.columnPaths.isNotEmpty() }) {
            append('\n')
            append(SHEET_JSON_COMPACT_PATHS)
        }
        if (sheet.mergeRegions.isNotEmpty()) {
            append('\n')
            append(SHEET_JSON_COMPACT_MERGE)
        }
    }

    @OptIn(ExperimentalRead4ai::class)
    fun sheetJsonRowObject(sheet: Sheet): String = buildString {
        append(SHEET_JSON_BASE)
        append('\n')
        append(SHEET_JSON_ROW_OBJECT_CELLS)
        if (sheet.mergeRegions.isNotEmpty()) {
            append('\n')
            append(SHEET_JSON_ROW_OBJECT_MERGE)
        }
    }

    fun sheetMarkdown(sheet: Sheet): String = buildString {
        append(SHEET_MARKDOWN_BASE)
        if (sheet.mergeRegions.isNotEmpty()) {
            append('\n')
            append(SHEET_MARKDOWN_MERGE)
        }
    }

    // ------------------------------------------------------------------
    // Static text fragments
    // ------------------------------------------------------------------

    private const val ROOT_JSON_COMPACT =
        """- `sheets` is an array of sheet objects; each has `sheetName`, `elements`, optional `mergeRegions`, and (when present) a `prompt` describing how to read that sheet."""

    @ExperimentalRead4ai
    private const val ROOT_JSON_ROW_OBJECT =
        """- `sheets` is an array of sheet objects; each has `sheetName`, `elements`, optional `mergeRegions`, and (when present) a `prompt` describing how to read that sheet.
- Tables use the row-object layout — each row is `{"row": N, "cells": [...]}`."""

    private const val ROOT_MARKDOWN =
        """- The document is rendered as `## SheetName` sections.
- Each sheet starts with a `_Prompt_` block describing how to read it.
- Lines wrapped in single underscores (e.g. `_Prompt_`, `_Language: ..._`, `_Merged: ..._`, `_Table starts at ..._`) are metadata. Do NOT count them as data rows or sheets."""

    private const val SHEET_JSON_BASE =
        """- `startRow`/`startCol` are 1-based Excel row/column numbers.
- `cell` is the anchor cell of a heading/text block.
- Tables may include `range`, `endRow`/`endCol`, `headerRowCount`, `headerEndRow`, and `bodyStartRow` metadata.
- `headerCells` maps each resolved header column to the header cell where that label is anchored."""

    private const val SHEET_JSON_COMPACT_MERGE =
        """- Cell values "<"/"^" indicate left/up merge continuations.
- `mergeRegions` lists this sheet's merges (e.g. "A1:D1"); `mergedRanges` and `mergedRangeDetails` do the same per table."""

    private const val SHEET_JSON_COMPACT_PATHS =
        """- `columnPaths` and `resolvedHeaders` give fully qualified header names for multi-row headers."""

    @ExperimentalRead4ai
    private const val SHEET_JSON_ROW_OBJECT_CELLS =
        """- A merged cell becomes `{"v": "...", "mr": N, "md": N}` where `mr`/`md` are merged-right/down counts."""

    @ExperimentalRead4ai
    private const val SHEET_JSON_ROW_OBJECT_MERGE =
        """- `mergeRegions` lists this sheet's merges (e.g. "A1:D1")."""

    private const val SHEET_MARKDOWN_BASE =
        """- The first column of each table row is the 1-based Excel row number."""

    private const val SHEET_MARKDOWN_MERGE =
        """- `[merged RxC]` after a cell value means the cell spans R rows × C columns.
- `_Merged: ..._` lists this sheet's merged ranges."""
}
