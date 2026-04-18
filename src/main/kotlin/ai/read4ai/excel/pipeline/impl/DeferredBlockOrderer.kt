package ai.read4ai.excel.pipeline.impl

import ai.read4ai.excel.pipeline.Block
import ai.read4ai.excel.pipeline.BlockOrderer
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Block orderer that separates primary and deferred blocks.
 *
 * - Segments with small gaps from previous content are "primary"
 * - Segments with gap >= [minDeferredGapRows] rows are candidates for deferral
 * - However, a candidate is NOT deferred if it is followed by a primary block
 *   (it is likely a section header, not a footnote)
 * - Output: all primary blocks first, then all deferred blocks
 *
 * @param minDeferredGapRows minimum gap rows to consider a block deferred (default: 2)
 */
class DeferredBlockOrderer(
    private val minDeferredGapRows: Int = DEFAULT_MIN_DEFERRED_GAP,
) : BlockOrderer {

    private val log = KotlinLogging.logger {}

    override fun order(blocks: List<Block>): List<Block> {
        if (blocks.isEmpty()) return blocks

        // First pass: mark candidates
        val isCandidate = BooleanArray(blocks.size) { i ->
            blocks[i].segment.gapFromPrevious >= minDeferredGapRows
        }

        // Second pass: a candidate followed by a non-candidate is a section header, not a footnote
        val isDeferred = BooleanArray(blocks.size)
        for (i in blocks.indices) {
            if (!isCandidate[i]) continue

            val nextIndex = (i + 1).takeIf { it < blocks.size }
            val followedByPrimary = nextIndex != null && !isCandidate[nextIndex]

            isDeferred[i] = !followedByPrimary
        }

        val primary = mutableListOf<Block>()
        val deferred = mutableListOf<Block>()

        for (i in blocks.indices) {
            if (isDeferred[i]) {
                deferred.add(blocks[i].copy(isDeferred = true))
            } else {
                primary.add(blocks[i].copy(isDeferred = false))
            }
        }

        log.debug {
            "${primary.size} primary, ${deferred.size} deferred block(s)"
        }

        return primary + deferred
    }

    companion object {
        private const val DEFAULT_MIN_DEFERRED_GAP = 2
    }
}
