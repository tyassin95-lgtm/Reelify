package app.reelify.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.reelify.core.data.ProjectRepository
import app.reelify.core.database.ReelifyDatabase
import app.reelify.core.model.CaptionSegment
import app.reelify.core.model.CaptionSource
import app.reelify.core.model.CaptionTrack
import app.reelify.core.model.CaptionWord
import app.reelify.core.model.ClipRole
import app.reelify.core.model.EditSettings
import app.reelify.core.model.MediaClip
import app.reelify.core.model.PlanMeta
import app.reelify.core.model.ReelConcept
import app.reelify.core.model.ReelLanguage
import app.reelify.core.model.ReelSection
import app.reelify.core.model.SectionPurpose
import app.reelify.core.model.ShotType
import app.reelify.core.model.TransitionType
import app.reelify.core.template.ProductionMode
import app.reelify.core.util.AppJson
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Builds a REAL schema-v1 database (from the exported 1.json), fills it the way Reelify 1.0 did,
 * then opens it with the v2 Room database + MIGRATION_1_2. Room validates the migrated schema
 * against the v2 entities, so a mismatch fails this test.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MigrationTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"

    private fun v1Sql(): List<String> {
        val f = listOf(File("schemas/app.reelify.core.database.ReelifyDatabase/1.json"), File("app/schemas/app.reelify.core.database.ReelifyDatabase/1.json")).first { it.exists() }
        val root = Json.parseToJsonElement(f.readText()).jsonObject["database"]!!.jsonObject
        val out = mutableListOf<String>()
        root["entities"]!!.jsonArray.forEach { e ->
            val o = e.jsonObject
            val table = o["tableName"]!!.jsonPrimitive.content
            out += o["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)
            o["indices"]?.jsonArray?.forEach { i -> out += i.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table) }
        }
        root["setupQueries"]!!.jsonArray.forEach { out += it.jsonPrimitive.content }
        return out
    }

    private fun createV1(populate: (SupportSQLiteDatabase) -> Unit) {
        ctx.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx).name(dbName).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) { v1Sql().forEach(db::execSQL) }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            }).build(),
        )
        helper.writableDatabase.also(populate)
        helper.close()
    }

    private fun esc(s: String) = s.replace("'", "''")

    @Test fun `v1 projects, sections, revisions, clips, captions and library survive the migration unchanged`() = runTest {
        val concept = ReelConcept(summary = "Aerolase for acne", audience = "Adults")
        val plan = PlanMeta(title = "Legacy plan", hook = "Is laser right for you?")
        val edit = EditSettings().let { it.copy(music = it.music.copy(trackId = "calm_clinical"), editVersion = 7) }
        val captions = CaptionTrack(ReelLanguage.IRAQI_ARABIC, listOf(CaptionSegment("seg1", 0, 1200, listOf(CaptionWord("شنو", 0, 400), CaptionWord("Aerolase", 400, 1200)))), CaptionSource.DEEPGRAM, 1000)
        val sections = (1..3).map { i ->
            ReelSection("sec-$i", i, listOf(SectionPurpose.HOOK, SectionPurpose.EXPLANATION, SectionPurpose.CALL_TO_ACTION)[i - 1], "S$i", "Legacy script $i",
                shotType = if (i == 2) ShotType.TREATMENT_BROLL else ShotType.TALKING_HEAD, cameraDirection = "Legacy camera $i", transitionOut = TransitionType.DISSOLVE, locked = i == 1)
        }
        val clip = MediaClip("clip-1", "p-legacy", "sec-1", ClipRole.PRIMARY, 0, uri = "/data/user/0/app.reelify/files/projects/p-legacy/rec_1.mp4", sourceDurationMs = 12_000, trimStartMs = 500, trimEndMs = 10_500, hasAudio = true)

        createV1 { db ->
            db.execSQL("INSERT INTO projects (id,title,createdAt,updatedAt,language,treatmentId,durationMs,style,status,planApproved,thumbnailPath,conceptJson,feedbackJson,planJson,editJson,captionsJson,brandJson,exportJson) VALUES " +
                "('p-legacy','My legacy reel',100,200,'ar-IQ','aerolase',30000,'educational','EDITING',1,NULL," +
                "'${esc(AppJson.encodeToString(ReelConcept.serializer(), concept))}',NULL,'${esc(AppJson.encodeToString(PlanMeta.serializer(), plan))}'," +
                "'${esc(AppJson.encodeToString(EditSettings.serializer(), edit))}','${esc(AppJson.encodeToString(CaptionTrack.serializer(), captions))}',NULL,NULL)")
            sections.forEach { s ->
                db.execSQL("INSERT INTO sections (id,projectId,sectionIndex,locked,updatedAt,dataJson) VALUES ('${s.id}','p-legacy',${s.index},${if (s.locked) 1 else 0},200,'${esc(AppJson.encodeToString(ReelSection.serializer(), s))}')")
            }
            db.execSQL("INSERT INTO section_revisions (sectionId,projectId,createdAt,reason,snapshotJson) VALUES ('sec-2','p-legacy',150,'manual edit','${esc(AppJson.encodeToString(ReelSection.serializer(), sections[1].copy(spokenScript = "older")))}')")
            db.execSQL("INSERT INTO clips (id,projectId,sectionId,role,ordering,uri,createdAt,dataJson) VALUES ('clip-1','p-legacy','sec-1','PRIMARY',0,'${clip.uri}',120,'${esc(AppJson.encodeToString(MediaClip.serializer(), clip))}')")
            db.execSQL("INSERT INTO treatments (id,name,sortOrder,builtIn,dataJson) VALUES ('custom-t','My device',50,0,'{\"id\":\"custom-t\",\"name\":\"My device\",\"builtIn\":false}')")
            db.execSQL("INSERT INTO terminology (id,english,dataJson) VALUES ('t1','Aerolase','{\"id\":\"t1\",\"english\":\"Aerolase\",\"arabic\":\"إيروليز\"}')")
        }

        val room = Room.databaseBuilder(ctx, ReelifyDatabase::class.java, dbName).addMigrations(ReelifyDatabase.MIGRATION_1_2).allowMainThreadQueries().build()
        try {
            val repo = ProjectRepository(room.projectDao())
            val b = repo.getBundle("p-legacy")!!
            assertThat(b.project.title).isEqualTo("My legacy reel")
            assertThat(b.project.mode).isEqualTo(ProductionMode.LEGACY)
            assertThat(b.project.template).isNull()
            assertThat(b.project.planApproved).isTrue()
            assertThat(b.project.concept).isEqualTo(concept)
            assertThat(b.project.plan).isEqualTo(plan)
            assertThat(b.project.edit.music.trackId).isEqualTo("calm_clinical")
            assertThat(b.project.edit.editVersion).isEqualTo(7)
            assertThat(b.project.edit.userOverrides).isEmpty()
            assertThat(b.project.captions).isEqualTo(captions)
            assertThat(b.sections.map { it.spokenScript }).containsExactly("Legacy script 1", "Legacy script 2", "Legacy script 3").inOrder()
            assertThat(b.sections.map { it.cameraDirection }).containsExactly("Legacy camera 1", "Legacy camera 2", "Legacy camera 3").inOrder()
            assertThat(b.sections[0].locked).isTrue()
            assertThat(b.sections.all { it.role == null }).isTrue()
            assertThat(b.clips.single().trimStartMs).isEqualTo(500)
            assertThat(b.clips.single().uri).isEqualTo(clip.uri)
            assertThat(repo.revisionCount("sec-2")).isEqualTo(1)
            assertThat(room.libraryDao().getTreatment("custom-t")).isNotNull()
            assertThat(room.libraryDao().getTerminology().map { it.english }).contains("Aerolase")
            // v2 tables exist and work
            room.contentDao().insertTopicUse(app.reelify.core.database.TopicHistoryEntity(treatmentId = "aerolase", templateId = "x", topicKey = "k", hook = "h", usedAt = 1))
            assertThat(room.contentDao().recentTopics("aerolase", 5)).hasSize(1)
            // and the legacy project can still be edited and undone
            repo.undoSection("p-legacy", "sec-2")
            assertThat(repo.getBundle("p-legacy")!!.sections[1].spokenScript).isEqualTo("older")
        } finally {
            room.close(); ctx.deleteDatabase(dbName)
        }
    }
}
