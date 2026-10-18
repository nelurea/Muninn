package io.github.nelurea.muninn.ui.capture

import io.github.nelurea.muninn.data.db.PurposeUsageRow
import io.github.nelurea.muninn.data.db.PurposeVocabularyEntity

/** Presentation policy; last-used times come from explicit selection events. */
internal object PurposeSuggestionPolicy {
    fun recent(
        vocabulary: List<PurposeVocabularyEntity>,
        usage: List<PurposeUsageRow>,
        excludeIds: Set<Long>,
        limit: Int = 6
    ): List<PurposeVocabularyEntity> {
        val byId = vocabulary.associateBy { it.id }
        return usage.filter { it.lastUsedAt > 0 }.sortedWith(
            compareByDescending<PurposeUsageRow> { it.lastUsedAt }
                .thenBy { it.purposeId }
        ).mapNotNull { byId[it.purposeId] }
            .filterNot { it.id in excludeIds }
            .distinctBy { it.id }
            .take(limit)
    }

    fun frequent(
        vocabulary: List<PurposeVocabularyEntity>,
        usage: List<PurposeUsageRow>,
        excludeIds: Set<Long>,
        limit: Int = 6
    ): List<PurposeVocabularyEntity> {
        val byId = vocabulary.associateBy { it.id }
        return usage.filter { it.useCount > 0 }.sortedWith(
            compareByDescending<PurposeUsageRow> { it.useCount }
                .thenByDescending { it.lastUsedAt }
                .thenBy { it.purposeId }
        ).mapNotNull { byId[it.purposeId] }
            .filterNot { it.id in excludeIds }
            .distinctBy { it.id }
            .take(limit)
    }

    fun search(
        vocabulary: List<PurposeVocabularyEntity>,
        query: String,
        excludeIds: Set<Long>
    ): List<PurposeVocabularyEntity> =
        vocabulary.filterNot { it.id in excludeIds }
            .filter { it.label.contains(query.trim(), ignoreCase = true) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}
