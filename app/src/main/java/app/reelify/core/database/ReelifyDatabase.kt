package app.reelify.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProjectEntity::class, SectionEntity::class, SectionRevisionEntity::class, ClipEntity::class,
        TreatmentEntity::class, TerminologyEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ReelifyDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun libraryDao(): LibraryDao

    companion object {
        fun create(context: Context): ReelifyDatabase =
            Room.databaseBuilder(context, ReelifyDatabase::class.java, "reelify.db").build()

        fun inMemory(context: Context): ReelifyDatabase =
            Room.inMemoryDatabaseBuilder(context, ReelifyDatabase::class.java).allowMainThreadQueries().build()
    }
}
