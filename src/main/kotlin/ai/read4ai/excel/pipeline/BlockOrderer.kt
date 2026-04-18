package ai.read4ai.excel.pipeline

/** Determines the output order of [Block]s (e.g., primary content before footnotes). */
interface BlockOrderer {
    fun order(blocks: List<Block>): List<Block>
}
