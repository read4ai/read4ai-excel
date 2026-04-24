package ai.read4ai.excel.strategy.impl

import ai.read4ai.excel.strategy.Block
import ai.read4ai.excel.strategy.BlockOrderer

/**
 * Default [BlockOrderer] that preserves the original segment order without reordering.
 *
 * This reproduces the existing ExcelParser behavior.
 */
class SequentialBlockOrderer : BlockOrderer {

    override fun order(blocks: List<Block>): List<Block> = blocks
}
