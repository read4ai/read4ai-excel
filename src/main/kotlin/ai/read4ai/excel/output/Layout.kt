package ai.read4ai.excel.output

import ai.read4ai.excel.ExperimentalRead4ai

/**
 * Output layout within a formatter recipe.
 *
 * Combine with a formatter:
 * - `JsonFormatter(Layout.COMPACT)` — 2D arrays + sparse merges
 * - `JsonFormatter(Layout.ROW_OBJECT)` — row objects with inline merges (experimental)
 * - `MarkdownFormatter(Layout.COMPACT)` — pipe tables (default)
 *
 * Not every output-type/layout combination is supported. `MarkdownFormatter` rejects
 * [ROW_OBJECT].
 */
enum class Layout {
    /** 2D table layout. Default. */
    COMPACT,
    /** Row-object layout (one record per row). Experimental; JSON only. */
    @ExperimentalRead4ai
    ROW_OBJECT,
}
