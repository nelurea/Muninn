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
    ],
    version = 18,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun imageRecordDao(): ImageRecordDao

    abstract fun sessionDao(): SessionDao

    abstract fun pendingCaptureDao(): PendingCaptureDao

    abstract fun resolvedCaptureDao(): ResolvedCaptureDao

    abstract fun acquisitionQueueDao(): AcquisitionQueueDao

    abstract fun captureEventDao(): CaptureEventDao

    abstract fun capturedWorkDao(): CapturedWorkDao

    abstract fun mediaMoveDao(): MediaMoveDao

    abstract fun saveEventDao(): SaveEventDao

    abstract fun duplicateNormalizationDao(): DuplicateNormalizationDao
}
