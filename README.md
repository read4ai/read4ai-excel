<p align="center">
  <img src="docs/images/banner.jpg" alt="read4ai-excel" width="100%">
</p>

# read4ai-excel

> **A predictable, maintainable, and transparent Excel parser.** — [Try the demo](https://huggingface.co/spaces/read4ai/read4ai)  
> **An intelligent pipeline that selects the optimal parsing strategy and AI-friendly format for your file.**

[![](https://jitpack.io/v/read4ai/read4ai-excel.svg)](https://jitpack.io/#read4ai/read4ai-excel)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-blue.svg)](https://kotlinlang.org)
[![Java](https://img.shields.io/badge/Java-17%2B-green.svg)](docs/guide.md)
[![GitHub](https://img.shields.io/badge/Contact-GitHub-181717?logo=github)](https://github.com/Hyune-c)
[![LinkedIn](https://img.shields.io/badge/Contact-LinkedIn-0A66C2?logo=linkedin)](https://www.linkedin.com/in/b30b971a0/)

<p align="center">
  <img src="docs/images/roadmap.svg" alt="Roadmap" width="100%">
</p>

- [Getting Started](docs/guide.md)
- [Benchmark](docs/benchmark/)
- [Full Roadmap](docs/roadmap.md)

## The problem with Excel

Spreadsheets mix data with layout. No semantic markup to guide a parser:

- Merged cells, multi-level headers, side-by-side tables, scattered text blocks
- Existing tools extract flat cell grids — fine for humans, but they lose the structure LLMs need
- Raw XLSX wastes tokens, hits context limits, and forces the model to guess

**The real goal isn't parsing — it's AI comprehension.**
Success is measured by whether an LLM can correctly answer questions about the data.

## Philosophy

### 1. Designed to be verified, not just claimed

| 2026-04-22        | read4ai-excel v0.2.0 | pandas 3.0 | SheetJS 0.20 | calamine 0.6 |
|-------------------|----------------------|------------|--------------|--------------|
| GPT-5.4 mini      | 🏆 **81.7%**        | 78.3%      | 77.8%        | 73.9%        |

The verification loop **parse → ask AI → measure → improve** runs on every change.

- Published [golden set](docs/benchmark/) with every evaluation result. [Try it yourself](https://huggingface.co/spaces/read4ai/read4ai)
- A private hidden holdout prevents overfitting
- Every fixture added makes the loop stronger

> Bug reports and fixture submissions directly strengthen the loop. [Submit your golden set](docs/benchmark/README.md)

### 2. Designed to be composable, not prescriptive

<p align="center">
  <img src="docs/images/pipeline.svg" alt="Pipeline" width="100%">
</p>

Excel doesn't have one right answer.
A financial report, a multi-table schedule, and a scattered data export each reward different heuristics.

Every axis — input pipeline *and* output format — is an interface you can swap.

- **Four pre-built strategies** — `balanced` (default) plus `complex` / `structural` / `scattered`.
- **Experimental axes** — marked `@ExperimentalRead4ai`; opt-in is explicit.

> Custom strategies plug in without forking the library. [Compose your own](docs/guide.md)

### 3. Designed to be dependable

- **Stable public API** — interfaces and models follow semantic versioning. Breaking changes bump the major version, not a silent release.
- **Deterministic output** — same file + same strategy → same bytes. No hidden global state, no runtime magic.
- **No framework lock-in** — plain Kotlin library. No Spring, no DI container, no annotation processors.
- **AI-friendly repo** — docs in Markdown, code organized so AI agents can reason about it as easily as humans.
