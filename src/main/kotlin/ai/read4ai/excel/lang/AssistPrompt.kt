package ai.read4ai.excel.lang

import ai.read4ai.excel.model.ExcelDocument

/** Generates language-aware assist prompts for AI interactions. */
object AssistPrompt {

    private val PROMPTS = mapOf(
        "KO" to "This document is written in Korean.",
        "JA" to "This document is written in Japanese.",
        "EN" to "This document is written in English.",
    )

    private val LANGUAGE_NAMES = mapOf(
        "KO" to "Korean",
        "JA" to "Japanese",
        "EN" to "English",
    )

    fun from(document: ExcelDocument): String {
        return PROMPTS[document.language] ?: PROMPTS["EN"]!!
    }

    fun from(documents: List<ExcelDocument>): String {
        val languages = documents.map { it.language }.distinct().sorted()

        if (languages.size == 1) {
            return from(documents.first())
        }

        val names = languages.mapNotNull { LANGUAGE_NAMES[it] }
        val langList = names.joinToString(" and ")
        return "This data was extracted from $langList Excel documents."
    }
}
