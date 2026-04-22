<p align="center">
  <img src="docs/images/banner.jpg" alt="read4ai-excel" width="100%">
</p>

# read4ai-excel

> **An AI-friendly Excel parser for merged cells, multi-table sheets, and structured JSON output.** — [Try the demo](https://huggingface.co/spaces/read4ai/read4ai)  
> **Built for spreadsheet understanding: predictable parsing, composable pipelines, and benchmarked LLM-facing output.**

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
- [FAQ](docs/faq.md)
- [read4ai-excel vs pandas](docs/comparisons/read4ai-vs-pandas.md)
- [Benchmark](docs/benchmark/)
- [Full Roadmap](docs/roadmap.md)

`read4ai-excel` is a Kotlin Excel parser for AI and LLM workflows.
It is designed for spreadsheets that behave like documents, not just tables:

- merged cells and merged header bands
- multi-row headers
- side-by-side or stacked tables
- text blocks, notes, and mixed table/text sheets

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
AI is part of the workflow, but the benchmark harness is what makes improvements measurable, repeatable, and falsifiable.

- Published [golden set](docs/benchmark/) with every evaluation result. [Try it yourself](https://huggingface.co/spaces/read4ai/read4ai)
- A private hidden holdout prevents overfitting
- Every fixture added makes the loop stronger
- Harness engineering turns parser iteration into something you can verify, not just claim

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
- Experimental methods can be applied without rewriting the whole parser, then benchmarked and kept only when they actually help
- The long-term goal is not one rigid parser, but a system that can choose the right pipeline for each spreadsheet type

> Custom strategies plug in without forking the library. [Compose your own](docs/guide.md)

### 3. Designed to be dependable

- **Stable public API** — interfaces and models follow semantic versioning. Breaking changes bump the major version, not a silent release.
- **Deterministic output** — same file + same strategy → same bytes. No hidden global state, no runtime magic.
- **No framework lock-in** — plain JVM library for Kotlin and Java. No Spring, no DI container, no annotation processors.
- **AI-friendly repo** — docs in Markdown, code organized so AI agents can reason about it as easily as humans.
