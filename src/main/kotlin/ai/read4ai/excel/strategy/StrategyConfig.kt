package ai.read4ai.excel.strategy

import ai.read4ai.excel.ExperimentalRead4ai
import ai.read4ai.excel.strategy.impl.*

/** Configuration selecting implementations for parsing strategy stages. See companion presets for tested strategies. */
data class StrategyConfig(
    val workbookReader: WorkbookReader = PoiWorkbookReader(),
    val gridExtractor: GridExtractor = DefaultGridExtractor(),
    val segmenter: Segmenter = GraphSegmenter(),
    val headerDetector: HeaderDetector = MergeAwareHeaderDetector(),
    val blockOrderer: BlockOrderer = DeferredBlockOrderer(),
    val elementClassifier: ElementClassifier = DefaultElementClassifier(),
) {
    companion object {
        /** Balanced: graph segmentation, merge-aware headers, deferred ordering. */
        fun balanced(): StrategyConfig = StrategyConfig()

        /** Structural (experimental): ThreeLevel segmentation, merge-aware headers, sequential ordering. */
        @ExperimentalRead4ai
        fun structural(): StrategyConfig = StrategyConfig(
            segmenter = ThreeLevelSegmenter(),
            headerDetector = MergeAwareHeaderDetector(),
            blockOrderer = SequentialBlockOrderer(),
            elementClassifier = DefaultElementClassifier(),
        )

        /** Scattered (experimental): graph segmentation WITHOUT row-range merging, for scattered data islands. */
        @ExperimentalRead4ai
        fun scattered(): StrategyConfig = StrategyConfig(
            segmenter = GraphSegmenter(mergeOverlappingRows = false),
            headerDetector = SingleRowHeaderDetector(),
            blockOrderer = SequentialBlockOrderer(),
            elementClassifier = DefaultElementClassifier(),
        )

        /** Complex (experimental): hierarchy-aware header detection for multi-level merged headers. */
        @ExperimentalRead4ai
        fun complex(): StrategyConfig = StrategyConfig(
            headerDetector = HierarchyAwareHeaderDetector(),
        )
    }
}
