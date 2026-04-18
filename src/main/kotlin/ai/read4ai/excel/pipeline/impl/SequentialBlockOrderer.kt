package ai.read4ai.excel.pipeline.impl

import ai.read4ai.excel.pipeline.Block
import ai.read4ai.excel.pipeline.BlockOrderer

/**
 * Default [BlockOrderer] that preserves the original segment order without reordering.
 *
 * This reproduces the existing ExcelParser behavior.
 */
class SequentialBlockOrderer : BlockOrderer {

    override fun order(blocks: List<Block>): List<Block> = blocks
}
