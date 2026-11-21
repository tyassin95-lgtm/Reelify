package app.reelify.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProjectEntity::class, SectionEntity::class, SectionRevisionEntity::class, ClipEntity::class,
        TreatmentEntity::class, TerminologyEntity::class,
        CustomTemplateEntity::class, StyleExampleEntity::class, TopicHistoryEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class ReelifyDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun libraryDao(): LibraryDao
    abstract fun contentDao(): ContentDao

    companion object {
        /**
         * v1 → v2 (Reelify 2.0). Purely additive: every existing project, section, revision, clip,
         * caption track, treatment and terminology row is preserved byte-for-byte. Existing projects
         * are marked LEGACY so they keep their own section-level production settings.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `projects` ADD COLUMN `mode` TEXT NOT NULL DEFAULT 'LEGACY'")
                db.execSQL("ALTER TABLE `projects` ADD COLUMN `templateId` TEXT")
                db.execSQL("ALTER TABLE `projects` ADD COLUMN `templateVersion` INTEGER")
                db.execSQL("ALTER TABLE `projects` ADD COLUMN `templateJson` TEXT")
                db.execSQL("ALTER TABLE `projects` ADD COLUMN `overridesJson` TEXT")
                db.execSQL("ALTER TABLE `projects` ADD COLUMN `scriptJson` TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS `custom_templates` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `baseTemplateId` TEXT, `version` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `dataJson` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `style_examples` (`id` TEXT NOT NULL, `language` TEXT NOT NULL, `category` TEXT NOT NULL, `status` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `dataJson` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_style_examples_language` ON `style_examples` (`language`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `topic_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `treatmentId` TEXT NOT NULL, `templateId` TEXT NOT NULL, `topicKey` TEXT NOT NULL, `hook` TEXT NOT NULL, `usedAt` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_topic_history_usedAt` ON `topic_history` (`usedAt`)")
            }
        }

        fun create(context: Context): ReelifyDatabase =
            Room.databaseBuilder(context, ReelifyDatabase::class.java, "reelify.db")
                .addMigrations(MIGRATION_1_2)
                // No fallbackToDestructiveMigration: a missing migration must fail loudly, never wipe projects.
                .build()

        fun inMemory(context: Context): ReelifyDatabase =
            Room.inMemoryDatabaseBuilder(context, ReelifyDatabase::class.java).allowMainThreadQueries().build()
    }
}
