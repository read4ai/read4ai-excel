# Roadmap

## v0 — Foundation (experimental)

### v0.1.x — Initial release

- Composable pipeline with interface-based steps (6 stages, user-swappable)
- Default strategy `balanced` tested against a golden set
- Output layer as interface (`DocumentWriter`) — users can plug in custom formats
- JSON (compact / row-object) and Markdown writers out of the box
- Merge region metadata, multi-level headers, hierarchy paths
- Language detection (KO / EN / JA)
- `@ExperimentalRead4ai` opt-in marker for unstable APIs
- Java 25 baseline, virtual threads, pure Kotlin + Apache POI
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
