package ai.read4ai.excel.pipeline

import ai.read4ai.excel.ExperimentalRead4ai
import ai.read4ai.excel.pipeline.impl.*

/** Configuration selecting which option to use at each pipeline step. See [Strategy] for tested combinations. */
data class PipelineConfig(
    val workbookReader: WorkbookReader = PoiWorkbookReader(),
    val gridExtractor: GridExtractor = DefaultGridExtractor(),
    val segmenter: Segmenter = GraphSegmenter(),
    val headerDetector: HeaderDetector = MergeAwareHeaderDetector(),
    val blockOrderer: BlockOrderer = DeferredBlockOrderer(),
    val elementClassifier: ElementClassifier = DefaultElementClassifier(),
) {
    /** Tested strategy combinations verified against the golden set. */
    object Strategy {

        /** Balanced (default): graph segmentation, merge-aware headers, deferred ordering. */
        fun balanced(): PipelineConfig = PipelineConfig()

        /** Structural (experimental): ThreeLevel segmentation, merge-aware headers, sequential ordering. */
        @ExperimentalRead4ai
        fun structural(): PipelineConfig = PipelineConfig(
            segmenter = ThreeLevelSegmenter(),
            headerDetector = MergeAwareHeaderDetector(),
            blockOrderer = SequentialBlockOrderer(),
            elementClassifier = DefaultElementClassifier(),
        )

        /** Scattered (experimental): graph segmentation WITHOUT row-range merging, for scattered data islands. */
        @ExperimentalRead4ai
        fun scattered(): PipelineConfig = PipelineConfig(
            segmenter = GraphSegmenter(mergeOverlappingRows = false),
            headerDetector = SingleRowHeaderDetector(),
            blockOrderer = SequentialBlockOrderer(),
            elementClassifier = DefaultElementClassifier(),
        )

        /** Complex (experimental): hierarchy-aware header detection for multi-level merged headers. */
        @ExperimentalRead4ai
        fun complex(): PipelineConfig = PipelineConfig(
            headerDetector = HierarchyAwareHeaderDetector(),
        )

    }
}
