# FAQ

## What is read4ai-excel?

`read4ai-excel` is a Kotlin Excel parser built for AI and LLM workflows.
It converts spreadsheets into structured output that preserves layout signals such as merged cells, multi-row headers, and mixed text/table blocks.

## How is it different from pandas for Excel parsing?

`pandas` is strong for tabular data processing.
`read4ai-excel` is designed for spreadsheet understanding when layout matters:

- merged header interpretation
- multi-table sheet handling
- AI-friendly JSON and Markdown output
- composable parsing stages instead of a single extraction path

See also: [read4ai-excel vs pandas](comparisons/read4ai-vs-pandas.md)

## Can it parse merged cells?

Yes.
Merged regions are preserved in the parsed document and are reflected in formatter output so downstream LLM prompts do not have to infer them from raw cell positions alone.

## Can it handle multi-table Excel sheets?

Yes.
The default pipeline is designed to keep separate table blocks, and alternative strategies can be selected or composed when a workbook rewards different heuristics.

## Does it work for LLM pipelines?

Yes.
The project is explicitly benchmarked by asking an LLM questions about parsed output and measuring whether the answers are correct.
The goal is spreadsheet comprehension, not only data extraction.

## What output formats does it support?

The built-in formatters support:

- JSON
- Markdown

JSON supports multiple layout styles, including compact and row-object variants.

## Can I compose my own pipeline?

Yes.
The input pipeline is interface-based, so segmentation, header detection, block ordering, and element classification can be swapped without forking the whole library.

## Can I use it from Java?

Yes.
`read4ai-excel` is a plain JVM library and can be used from both Kotlin and Java.

## Is it tied to Spring or a framework?

No.
It is a plain JVM library with a stable public API and no framework lock-in.

## Why not use raw XLSX extraction?

Raw XLSX or flat cell extraction is often fine for simple tables.
It becomes weaker when merged headers, multi-table sheets, and nearby notes carry meaning that an LLM still needs.

See also: [read4ai-excel vs pandas](comparisons/read4ai-vs-pandas.md)

## How is parser quality measured?

The project evaluates parser quality by asking an LLM questions about parsed output and checking whether the answers are correct.
That means benchmark quality is tied to downstream comprehension, not only extraction coverage.

## Where can I see benchmark results?

See [Benchmark](benchmark/README.md) and the versioned benchmark documents in [docs/benchmark](benchmark/).
