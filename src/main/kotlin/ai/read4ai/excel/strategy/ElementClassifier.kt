package ai.read4ai.excel.strategy

import ai.read4ai.excel.model.Element

/** Classifies a [Segment] into an [Element] type (Table, Heading, Text, etc.). */
interface ElementClassifier {
    fun classify(segment: Segment, headerInfo: HeaderInfo): Element
}
