package ai.read4ai.excel.pipeline

import ai.read4ai.excel.model.Element

/**
 * A classified block -- a segment with its header info, element classification,
 * and ordering metadata.
 *
 * @property segment the source segment
 * @property headerInfo detected header information
 * @property element the classified element type
 * @property isDeferred whether this block should be ordered after primary blocks
 */
data class Block(
    val segment: Segment,
    val headerInfo: HeaderInfo,
    val element: Element,
    val isDeferred: Boolean,
)
