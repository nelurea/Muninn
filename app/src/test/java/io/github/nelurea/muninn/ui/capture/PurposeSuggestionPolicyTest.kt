package io.github.nelurea.muninn.ui.capture

import io.github.nelurea.muninn.data.db.PurposeUsageRow
import io.github.nelurea.muninn.data.db.PurposeVocabularyEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PurposeSuggestionPolicyTest {
    private val all = listOf(
        PurposeVocabularyEntity(1, "Reference"),
        PurposeVocabularyEntity(2, "Outfit"),
        PurposeVocabularyEntity(3, "Archive"),
        PurposeVocabularyEntity(4, "Comfort")
    )
    private val usage = listOf(
        PurposeUsageRow(1, 15, 20),
        PurposeUsageRow(2, 3, 80),
        PurposeUsageRow(3, 8, 50),
        PurposeUsageRow(999, 50, 100)
    )

    @Test fun recentUsesActualLastSelectionTimeRatherThanFrequency() {
        assertEquals(listOf(2L, 3L, 1L),
            PurposeSuggestionPolicy.recent(all, usage, emptySet()).map { it.id })
    }

    @Test fun frequentExcludesPinnedRecentAndUnknownIds() {
        assertEquals(listOf(1L, 3L),
            PurposeSuggestionPolicy.frequent(all, usage, setOf(2L)).map { it.id })
    }

    @Test fun searchIgnoresCaseAndExcludesSelections() {
        assertEquals(listOf("Outfit"),
            PurposeSuggestionPolicy.search(all, "OUT", setOf(3L)).map { it.label })
        assertEquals(emptyList<String>(),
            PurposeSuggestionPolicy.search(all, "OUT", setOf(2L)).map { it.label })
    }
}
