package io.github.nelurea.muninn.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ImageRecord::class,
        SessionEntity::class,
        PendingCaptureEntity::class,
        ResolvedCaptureEntity::class,
        AcquisitionQueueEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun imageRecordDao(): ImageRecordDao

    abstract fun sessionDao(): SessionDao

    abstract fun pendingCaptureDao(): PendingCaptureDao

    abstract fun resolvedCaptureDao(): ResolvedCaptureDao

    abstract fun acquisitionQueueDao(): AcquisitionQueueDao
}