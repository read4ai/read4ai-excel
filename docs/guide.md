# Getting Started

## Installation

**Gradle (Kotlin DSL):**

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.hyune-c:read4ai-excel:0.3.2")
}
```

**Gradle (Groovy DSL):**

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation 'io.github.hyune-c:read4ai-excel:0.3.2'
}
```

**Maven:**

```xml
<dependency>
  <groupId>io.github.hyune-c</groupId>
  <artifactId>read4ai-excel</artifactId>
  <version>0.3.2</version>
</dependency>
```

**Requirements:** Java 17+, Kotlin 2.3+ (for Kotlin projects)

## Parse

```kotlin
// Excel and CSV auto-detected by extension. CSV delimiter auto-detected from content.
val doc = ExcelParser.parse(Path("data.xlsx"))
val doc = ExcelParser.parse(Path("data.csv"))

val json = Formatter.toJson(doc)
val md = Formatter.toMarkdown(doc)
```

## Output examples

**JSON (compact):**

```json
{
  "language": "KO",
  "sheets": [{
    "sheetName": "Budget",
    "elements": [{
      "type": "table",
      "headerRowCount": 2,
      "rows": [
        ["Category", "Sales", "", "Note"],
        ["", "Q1", "Q2", ""],
        ["Revenue", "1000", "1200", "Good"],
        ["Cost", "800", "750", ""]
      ],
      "merges": [
        {"cell": "A1", "rowSpan": 2, "colSpan": 2},
        {"cell": "C1", "colSpan": 2},
        {"cell": "D2", "rowSpan": 2}
      ]
    }],
    "mergeRegions": ["A1:B2", "C1:D1", "D2:D3"]
  }]
}
```

Rows are 2D string arrays. Merge info is a sparse list at the table level — only cells with merges are listed.

**Markdown:**

```
_Language: KO_

## Budget

_Merged: A1:B2, C1:D1, D2:D3_
_[merged RxC] = spans R rows × C columns_

| 1 | Category [merged 2x2] | Sales [merged 1x2] |              | Note          |
| --- | --- | --- | --- | --- |
| 2 |                       | Q1                  | Q2           |  [merged 2x1] |
| 3 | Revenue               | 1000                | 1200         | Good          |
| 4 | Cost                  | 800                 | 750          |               |
```

---

## Use cases

### Excel to JSON for LLM workflows

Use `read4ai-excel` when spreadsheet output is going to an LLM and layout still matters.
Typical cases:

- merged headers that define column meaning
- multiple tables in one sheet
- notes and text blocks that explain table values
- report-style spreadsheets that behave more like documents than dataframes

### Merged-cell-heavy reports

Financial reports, schedules, and operational workbooks often rely on merged regions to express grouping.
The parser preserves those regions so downstream systems do not have to recover structure from flat cell coordinates alone.

### Multi-table sheets

Some workbooks place several logical tables in the same sheet.
The zero-config `balanced` strategy is designed to keep those blocks separate instead of flattening them into one ambiguous grid.

### JVM apps that need composability

If you need to swap segmentation, header detection, block ordering, or output formatting, the parser is interface-based and can be composed without rewriting the whole library.

---

## Advanced

### Why composable?

Excel doesn't have a single right parser. A financial report, a scattered data export, and a report with merged headers each reward different heuristics. Instead of forcing one algorithm, read4ai-excel exposes parsing strategy stages as **interfaces** you can swap.

`StrategyConfig` carries the strategy-side composition. `DocumentFormatter` covers output. A recipe is the user-facing composition of **strategy + output**, with `Assist` as an optional output modifier.

### Strategies (pre-built combinations)

```kotlin
// Use a built-in strategy
val doc = ExcelParser.parse(path, strategy = StrategyConfig.complex())

// Compose your own
val doc = ExcelParser.parse(path, strategy = StrategyConfig(
    segmenter = ThreeLevelSegmenter(),
    headerDetector = SingleRowHeaderDetector(),
))
```

| Strategy | Segmenter | HeaderDetector | BlockOrderer | Best for |
|----------|-----------|----------------|--------------|----------|
| **balanced** | Graph | MergeAware | Deferred | Most files |
| complex ⚠️ | Graph | HierarchyAware | Deferred | Multi-level merged headers |
| structural ⚠️ | ThreeLevel | MergeAware | Sequential | Simple structure |
| scattered ⚠️ | Graph (no merge) | SingleRow | Sequential | Scattered data islands |

⚠️ marked as `@ExperimentalRead4ai` — opt-in required, API may change:

```kotlin
@OptIn(ExperimentalRead4ai::class)
val doc = ExcelParser.parse(path, strategy = StrategyConfig.complex())
```

### Strategy axes

The meaningful strategy choices are the stages that usually change benchmark behavior. Implement one and pass it to `StrategyConfig`. **Bold** = `balanced`.

| Axis | Interface | Options |
|------|-----------|---------|
| Segmentation | `Segmenter` | **GraphSegmenter**, ThreeLevelSegmenter, SimpleSegmenter |
| Header detection | `HeaderDetector` | **MergeAwareHeaderDetector**, HierarchyAwareHeaderDetector, SingleRowHeaderDetector |
| Block ordering | `BlockOrderer` | **DeferredBlockOrderer**, SequentialBlockOrderer |

Supporting input stages are also interfaces, but they are infrastructure hooks rather than strategy axes in the current recipe model:

| Stage | Interface | Current option |
|-------|-----------|----------------|
| Workbook read | `WorkbookReader` | PoiWorkbookReader |
| Grid extraction | `GridExtractor` | DefaultGridExtractor |
| Element classification | `ElementClassifier` | DefaultElementClassifier |

### Output recipes

Output recipes mainly combine **type** (JSON / Markdown) and **layout** (`COMPACT` / `ROW_OBJECT`).
`Assist` (`NONE` / `ON`) is an optional guidance modifier, not a separate data format:

```kotlin
val doc = ExcelParser.parse(path)

// JSON × layout (assist starts as NONE)
val compact = JsonFormatter().format(doc)                       // COMPACT
val rowObject = JsonFormatter(Layout.ROW_OBJECT).format(doc)    // ROW_OBJECT (experimental)
val md = MarkdownFormatter().format(doc)

// Assist ON — embeds short output guidance at
// the document root and inside every sheet so an LLM can interpret the
// payload without external instructions
val annotated = JsonFormatter(assist = Assist.ON).format(doc)
val annotatedMd = MarkdownFormatter(assist = Assist.ON).format(doc)

// Formatter facade
val json = Formatter.toJson(doc, Layout.COMPACT, Assist.ON)
val mdAlt = Formatter.toMarkdown(doc, Layout.COMPACT, Assist.ON)
```

Supported output recipes:

| Type | Layout | Assist | Status |
|------|--------|--------|--------|
| JSON | COMPACT | NONE / ON | stable |
| JSON | ROW_OBJECT | NONE / ON | experimental |
| Markdown | COMPACT | NONE / ON | stable |
| Markdown | ROW_OBJECT | NONE / ON | unsupported |

`Assist.ON` adds tokens (~100 per sheet) — enable it when you feed the output directly to an LLM and want output-reading guidance in-band. Use `Assist.NONE` for minimal-token workflows where you control the prompt separately.

There is no built-in CSV formatter right now. If you want one, implement `DocumentFormatter` yourself:

```kotlin
// Example custom formatter
class CsvRowsFormatter : DocumentFormatter {
    override fun format(document: ExcelDocument): String = buildString {
        document.sheets.flatMap { it.elements }
            .filterIsInstance<Element.Table>()
            .forEach { table ->
                table.rows.forEach { row ->
                    appendLine(row.cells.joinToString(",") { it.value })
                }
            }
    }
}

val csv = CsvRowsFormatter().format(doc)
```

### Building your own strategy

Implement a strategy axis, plug it in:

```kotlin
class MySegmenter : Segmenter {
    override fun segment(grid: Grid): List<Segment> = /* ... */
}

val doc = ExcelParser.parse(path, strategy = StrategyConfig(
    segmenter = MySegmenter(),
    // other stages keep their configured defaults
))
```

### Language detection

```kotlin
doc.language  // "KO", "EN", or "JA"

val prompt = AssistPrompt.from(doc)
// "This document is written in Korean."
```

### Configuration

```kotlin
// image handling
val config = ExcelConfig(imageOutput = ExcelConfig.ImageOutput.BASE64)
val doc = ExcelParser.parse(bytes, config = config)

// CSV with explicit delimiter (auto-detected by default)
val doc = CsvParser.parse(bytes, config = CsvConfig(delimiter = '\t'))
```
