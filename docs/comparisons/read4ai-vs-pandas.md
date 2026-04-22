# read4ai-excel vs pandas for Excel parsing

If your goal is dataframe ingestion, `pandas` is often enough.
If your goal is **spreadsheet understanding for AI**, the tradeoff changes.

## When pandas is a good fit

- clean tabular sheets
- analytics and dataframe-first workflows
- numeric transformation after extraction
- pipelines where layout is not important

## When read4ai-excel is a better fit

- merged headers carry meaning
- one sheet contains multiple logical tables
- notes and text blocks matter
- the output goes to an LLM or another downstream reasoning system
- you want a composable parsing pipeline on the JVM

## The core difference

`pandas` mainly gives you cells arranged as dataframes.
`read4ai-excel` tries to preserve spreadsheet structure so an LLM can answer questions about the document more reliably.

That means the project cares about:

- header interpretation
- table boundaries
- merge semantics
- mixed text/table sheets
- output formats that are easier for AI models to consume

## Why not flat cell extraction for AI?

Flat cell extraction is often enough for spreadsheets that are already clean tables.
It becomes much weaker when the workbook behaves like a document.

### Where flat extraction works well

- one table per sheet
- single-row headers
- little or no merged structure
- no explanatory text that affects interpretation

### Where flat extraction breaks down

- merged header bands define column meaning
- multiple logical tables share one sheet
- text blocks and notes matter
- layout carries semantics that are not visible in raw cell values

### Why this matters for AI

LLMs do not just need values.
They need enough structure to answer questions correctly.

If the parser loses:

- table boundaries
- header hierarchy
- merge semantics
- nearby explanatory text

the model has to guess.

## Benchmark perspective

`read4ai-excel` evaluates parser quality by asking an LLM questions about parsed output.
That is a different success metric from "can I load this sheet into a dataframe?"

See the current benchmark here:

- [Benchmark overview](../benchmark/README.md)
- [Latest benchmark report](../benchmark/)

## Practical rule of thumb

Use `pandas` when the spreadsheet is already a clean table.

Use `read4ai-excel` when the workbook behaves like a document:

- irregular merges
- table sections
- explanatory text
- report-style layouts
- AI-facing extraction
