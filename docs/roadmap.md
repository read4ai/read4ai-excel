# Roadmap

## v0 — Foundation (experimental)

### v0.3.2 — Support matrix grounding

- Compact JSON adds structured support-matrix projections for wide O/X-style tables
- Adjacent matrix row transitions expose changed headers without domain-specific rules
- Sectioned tables connect nearby headings to header cell coordinates for repeated-header sheets
- This is the last planned v0 release before the v1.0.0 stabilization push

### v0.3.0 — Strategy naming + default uplift

- Breaking terminology/API rename: `pipeline` -> `strategy`, `PipelineConfig` -> `StrategyConfig`
- Strategy package and public examples aligned to the new recipe / strategy / output vocabulary
- Default recipe updated to `balanced-json`, with selective row identity and offset-table metadata
- Benchmark total improved to `92.1%` overall

### v0.2.x — Output recipes

- Output recipes: type (JSON / Markdown) + layout (`COMPACT` / `ROW_OBJECT`), with **Assist** (`NONE` / `ON`, experimental) as an optional guidance modifier
- Element position fields (`Element.Heading` / `Text` / `Note` / `Image` now carry `startRow`) let an LLM pinpoint where section titles sit in the sheet
- Benchmark best updated to `balanced-rowobj` at 89.2% (v0.1.1: 86.3%)

### v0.1.x — Initial release

- Composable strategy stages + pluggable `DocumentFormatter`
- `balanced` strategy verified on a public golden set
- Language detection (KO / EN / JA)
- Java 17 baseline, pure Kotlin + Apache POI
- [Live demo](https://huggingface.co/spaces/read4ai/read4ai) with Ask AI

---

## v1 — Stable release

- Stable public API with semantic versioning guarantees
- One well-tested zero-config strategy (`balanced`), continuously improved
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

- **Chunking and embedding** — downstream knowledge workflow concerns, not parsing
- **Domain-specific semantics extraction**
    - detecting document type (invoice, VE analysis, financial report)
    - extracting business logic (O/X flags, cost aggregation). The parser provides structure; the AI interprets meaning
- **Data validation or cleansing** — the parser faithfully reproduces what the file contains
