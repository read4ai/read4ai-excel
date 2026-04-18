package ai.read4ai.excel.pipeline

/** Splits a [Grid] into [Segment]s in reading order -- rectangular sub-regions representing distinct content blocks. */
interface Segmenter {
    fun segment(grid: Grid): List<Segment>
}
