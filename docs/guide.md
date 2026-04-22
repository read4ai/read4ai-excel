# Getting Started

## Installation

**Gradle (Kotlin DSL):**

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.read4ai:read4ai-excel:v0.2.0")
}
```

**Gradle (Groovy DSL):**

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.read4ai:read4ai-excel:v0.2.0'
}
```

**Maven:**

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.github.read4ai</groupId>
  <artifactId>read4ai-excel</artifactId>
  <version>v0.2.0</version>
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

## Advanced

### Why composable?

Excel doesn't have a single right parser. A financial report, a scattered data export, and a report with merged headers each reward different heuristics. Instead of forcing one algorithm, read4ai-excel exposes the parsing pipeline as **interfaces** you can swap.

`PipelineConfig` holds six input-stage interfaces and `DocumentFormatter` covers output. Strategies are just pre-built combinations — no magic, just convenience.

### Strategies (pre-built combinations)

```kotlin
// Use a built-in strategy
val doc = ExcelParser.parse(path, pipeline = PipelineConfig.Strategy.complex())

// Compose your own
val doc = ExcelParser.parse(path, pipeline = PipelineConfig(
    segmenter = ThreeLevelSegmenter(),
    headerDetector = SingleRowHeaderDetector(),
))
```

| Strategy | Segmenter | HeaderDetector | BlockOrderer | Best for |
|----------|-----------|----------------|--------------|----------|
| **balanced** (default) | Graph | MergeAware | Deferred | Most files |
| complex ⚠️ | Graph | HierarchyAware | Deferred | Multi-level merged headers |
| structural ⚠️ | ThreeLevel | MergeAware | Sequential | Simple structure |
| scattered ⚠️ | Graph (no merge) | SingleRow | Sequential | Scattered data islands |

⚠️ marked as `@ExperimentalRead4ai` — opt-in required, API may change:

```kotlin
@OptIn(ExperimentalRead4ai::class)
val doc = ExcelParser.parse(path, pipeline = PipelineConfig.Strategy.complex())
```

### Pipeline axes (6 interfaces)

All six input stages are interfaces. Implement one and pass it to `PipelineConfig`. **Bold** = balanced default.

| Axis | Interface | Options |
|------|-----------|---------|
| 1. Workbook read | `WorkbookReader` | **PoiWorkbookReader** |
| 2. Grid extraction | `GridExtractor` | **DefaultGridExtractor** |
| 3. Segmentation | `Segmenter` | **GraphSegmenter**, ThreeLevelSegmenter, SimpleSegmenter |
| 4. Header detection | `HeaderDetector` | **MergeAwareHeaderDetector**, HierarchyAwareHeaderDetector, SingleRowHeaderDetector |
| 5. Block ordering | `BlockOrderer` | **DeferredBlockOrderer**, SequentialBlockOrderer |
| 6. Element classification | `ElementClassifier` | **DefaultElementClassifier** |

### Output: DocumentFormatter

Output has three independent axes — **format** (JSON / Markdown), **layout** (`COMPACT` / `ROW_OBJECT`), and **assist** (`NONE` / `ON`):

```kotlin
val doc = ExcelParser.parse(path)

// JSON × layout (assist defaults to NONE)
val compact = JsonFormatter().format(doc)                       // COMPACT (default)
val rowObject = JsonFormatter(Layout.ROW_OBJECT).format(doc)    // ROW_OBJECT (experimental)
val md = MarkdownFormatter().format(doc)

// Assist ON — embeds a short system-prompt-like `prompt` field/block at
// the document root and inside every sheet so an LLM can interpret the
// payload without external instructions
val annotated = JsonFormatter(assist = Assist.ON).format(doc)
val annotatedMd = MarkdownFormatter(assist = Assist.ON).format(doc)

// Formatter facade (3-axis form)
val json = Formatter.toJson(doc, Layout.COMPACT, Assist.ON)
val mdAlt = Formatter.toMarkdown(doc, Layout.COMPACT, Assist.ON)
```

Supported combinations:

| Format × Layout × Assist | `COMPACT × NONE` | `COMPACT × ON` | `ROW_OBJECT × NONE` | `ROW_OBJECT × ON` |
|--------------------------|------------------|----------------|---------------------|-------------------|
| JSON                     | ✅ default        | ✅              | ⚠️ experimental      | ⚠️ experimental    |
| Markdown                 | ✅ default        | ✅              | ❌ unsupported       | ❌ unsupported     |

`Assist.ON` adds tokens (~100 per sheet) — enable it when you feed the output directly to an LLM and want the schema explained in-band. Use `Assist.NONE` for minimal-token pipelines where you control the prompt separately.

To add your own format, implement `DocumentFormatter`:

```kotlin
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

Implement any axis, plug it in:

```kotlin
class MySegmenter : Segmenter {
    override fun segment(grid: Grid): List<Segment> = /* ... */
}

val doc = ExcelParser.parse(path, pipeline = PipelineConfig(
    segmenter = MySegmenter(),
    // other axes keep their defaults
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
val config = Config(imageOutput = Config.ImageOutput.BASE64)
val doc = ExcelParser.parse(bytes, config = config)

// CSV with explicit delimiter (auto-detected by default)
val doc = CsvParser.parse(bytes, config = CsvConfig(delimiter = '\t'))
```
