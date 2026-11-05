package io.github.nelurea.muninn.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ImageRecord::class,
        SessionEntity::class,
        PendingCaptureEntity::class,
        ResolvedCaptureEntity::class,
        AcquisitionQueueEntity::class,
        CaptureEventEntity::class,
        CapturedWorkEntity::class,
        CapturedMediaEntity::class,
        CapturedTagEntity::class,
        StateVocabularyEntity::class,
        SessionStateEntity::class,
        PurposeVocabularyEntity::class,
        SessionPurposeEntity::class,
        CapturedWorkPurposeEntity::class,
        AttractionVocabularyEntity::class,
        CapturedWorkAttractionEntity::class,
        CapturedMediaAttractionEntity::class,
        AestheticResponseVocabularyEntity::class,
        CapturedWorkResponseEntity::class,
        MediaFocusEntity::class,
        MediaMoveJournalEntity::class,
        SaveEventEntity::class,
        SaveEventMediaEntity::class,
        DuplicateNormalizationJournalEntity::class,
        DuplicateCleanupJournalEntity::class,
        DurableIdentityEntity::class,
    ],
    version = 23,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase()
