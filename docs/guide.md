# Getting Started

## Installation

**Gradle (Kotlin DSL):**

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.read4ai:read4ai-excel:v0.3.4")
}
```

**Gradle (Groovy DSL):**

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.read4ai:read4ai-excel:v0.3.4'
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
  <version>v0.3.4</version>
</dependency>
```

**Requirements:** Java 17+, Kotlin 2.3+ (for Kotlin projects)  
Developed against the latest JDK. The latest version is recommended for best performance.

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

### Strategies

A **strategy** is a tested combination of pipeline options. The default (`balanced`) works well for most files.

```kotlin
val doc = ExcelParser.parse(path, pipeline = PipelineConfig.Strategy.complex())

// compose your own
val doc = ExcelParser.parse(path, pipeline = PipelineConfig(
    segmenter = ThreeLevelSegmenter(),
    headerDetector = SingleRowHeaderDetector(),
))
```

| Strategy | Segmenter | Header Detector | Block Orderer | Best for |
|----------|-----------|-----------------|---------------|----------|
| **balanced** (default) | Graph | MergeAware | Deferred | Most files |
| **complex** (experimental) | Graph | HierarchyAware | Deferred | Multi-level merged headers |
| structural | ThreeLevel | MergeAware | Sequential | Simple structure |
| scattered | Graph (no merge) | SingleRow | Sequential | Scattered data islands |

### Pipeline axes

Each axis has pluggable options. **Bold** = balanced default.

| Axis | Options |
|------|---------|
| Segmenter | **GraphSegmenter**, ThreeLevelSegmenter, SimpleSegmenter |
| Header Detector | **MergeAwareHeaderDetector**, HierarchyAwareHeaderDetector, SingleRowHeaderDetector |
| Block Orderer | **DeferredBlockOrderer**, SequentialBlockOrderer |
| Element Classifier | **DefaultElementClassifier** |

### Language Detection

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
