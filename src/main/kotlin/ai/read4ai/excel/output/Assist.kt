package ai.read4ai.excel.output

import ai.read4ai.excel.ExperimentalRead4ai

/**
 * Whether the formatter embeds a short system-prompt-like block alongside
 * the document content so an LLM can interpret the payload without external
 * instructions.
 *
 * - [NONE] (default): pure data output, no extra text.
 * - [ON]: a `prompt` field / block is added at the document root and inside
 *   every sheet. The root block describes the overall envelope (sheets array,
 *   language). Each sheet block describes how to read its data
 *   (1-based indices, merge notation; merge guidance is only included when
 *   the sheet actually has merges).
 */
enum class Assist {
    NONE,

    @ExperimentalRead4ai
    ON,
}
