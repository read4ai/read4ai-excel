# Roadmap

## v0 — Foundation (experimental)

### v0.1.x — Initial release

- Composable pipeline (6 input axes + pluggable `DocumentFormatter`)
- Default `balanced` strategy verified on a public golden set
- Language detection (KO / EN / JA)
- Java 17 baseline, pure Kotlin + Apache POI
- [Live demo](https://huggingface.co/spaces/read4ai/read4ai) with Ask AI

### v0.2.x — Formatter 3-axis output

- Third axis **Assist** (`NONE` / `ON`, experimental): embeds a system-prompt-like `prompt` block at the document root and inside every sheet so an LLM can interpret the output without external instructions
- Element position fields (`Element.Heading` / `Text` / `Note` / `Image` now carry `startRow`) let an LLM pinpoint where section titles sit in the sheet
- Benchmark best updated to `balanced-rowobj` at 89.2% (v0.1.1: 86.3%)

---

## v1 — Stable release

- Stable public API with semantic versioning guarantees
- One well-tested default strategy (balanced), continuously improved
- Multi-JVM target: jvm17 (default) + jvm21 + jvm25

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
