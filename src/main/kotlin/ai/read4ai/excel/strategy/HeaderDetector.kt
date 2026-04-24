package ai.read4ai.excel.strategy

/** Identifies header rows within a [Segment]. */
interface HeaderDetector {
    fun detectHeaders(segment: Segment): HeaderInfo
}
