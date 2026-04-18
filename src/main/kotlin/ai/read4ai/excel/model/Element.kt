package ai.read4ai.excel.model

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * A semantic element extracted from an Excel sheet.
 *
 * Each variant represents a different kind of content found during parsing:
 * - [Table] -- tabular data with rows, cells, and header information
 * - [Heading] -- a section title or label (e.g., bullet-prefixed or year-based titles)
 * - [Text] -- standalone text content (isolated cells, notes)
 * - [Image] -- an embedded image with optional base64 data and description
 * - [Note] -- supplementary annotation or footer text
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = Element.Table::class, name = "table"),
    JsonSubTypes.Type(value = Element.Heading::class, name = "heading"),
    JsonSubTypes.Type(value = Element.Text::class, name = "text"),
    JsonSubTypes.Type(value = Element.Image::class, name = "image"),
    JsonSubTypes.Type(value = Element.Note::class, name = "note"),
)
/**
 * A classified content element within a sheet.
 * Each element represents a semantically distinct region: table, heading, text, image, or note.
 */
sealed interface Element {

    /** Tabular data with rows and cells. */
    data class Table(
        val rows: List<Row>,
        val headerRowCount: Int = 1,
        /** Row offset in the original sheet grid (0-based). Used to compute absolute cell references. */
        val startRow: Int = 0,
        /** Column offset in the original sheet grid (0-based). Used to compute absolute cell references. */
        val startCol: Int = 0,
        /** Column index to hierarchy path from merged top headers (e.g., {0: ["대분류", "소분류"]}). */
        val columnPaths: Map<Int, List<String>> = emptyMap(),
        /** Row index (data area, 0-based after headers) to hierarchy path from left merged headers. */
        val rowPaths: Map<Int, List<String>> = emptyMap(),
    ) : Element

    /** A section heading or title. */
    data class Heading(
        val text: String,
        val level: Int = 2,
    ) : Element

    /** Standalone text content. */
    data class Text(
        val text: String,
    ) : Element

    /** An embedded image, optionally with base64 data or an AI-generated description. */
    data class Image(
        val base64: String?,
        val mimeType: String?,
        val description: String?,
    ) : Element

    /** A supplementary note or annotation. */
    data class Note(
        val text: String,
    ) : Element
}
