# Roadmap

## v0 — Foundation (experimental)

### v0.3.x — Code stabilization

- Public API minimized, internal visibility enforced
- Pipeline simplified: Strategy + Format
- Java 17+ support with Multi-Release JAR

### v0.2.x — Language awareness + quality improvements

- Language detection (KO/EN/JA) with assist prompt utility
- Smarter DeferredBlockOrderer (section headers preserved)
- Layout experiment (JSON > Markdown > HTML confirmed)
- HierarchyAwareHeaderDetector (experimental)

### v0.1.x — Pipeline design + benchmark loop

- Composable pipeline with a tested default strategy (balanced)
- Compact JSON + Markdown output
- Merge region metadata in output (A1-range notation)
- Continuous benchmark verification against golden set
- [Live demo](https://huggingface.co/spaces/read4ai/read4ai) with Ask AI

---

## v1 — Stable release

- Stable public API with semantic versioning guarantees
- One well-tested default strategy (balanced), continuously improved
- Multi-JVM target: jvm25 (default) + jvm21 + jvm17

## v2 — Multi-model optimization + adaptive parsing

- Model-aware optimization: reduce accuracy gap across models (Claude, GPT, Gemini)
- Multiple verified strategies for different file types
- Auto-detect optimal strategy based on file structure

## Future

- Zero-config parsing — the engine adapts automatically
- Cross-sheet relationship inference
- Logical record assembly — grouping multi-row items into single logical units
- Embedded image extraction — VLM integration for cell-level images
- Cell formatting awareness — background color, font style, conditional formatting as semantic signals
- jvm11 support

## Out of scope

The following are intentionally excluded from this library's responsibility:

- **Chunking and embedding** — downstream knowledge pipeline concerns, not parsing
- **Domain-specific semantics extraction**
    - detecting document type (invoice, VE analysis, financial report)
    - extracting business logic (O/X flags, cost aggregation). The parser provides structure; the AI interprets meaning
- **Data validation or cleansing** — the parser faithfully reproduces what the file contains
