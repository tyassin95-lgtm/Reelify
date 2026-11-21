package app.reelify.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects", indices = [Index("updatedAt")])
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val language: String,
    val treatmentId: String,
    val durationMs: Long,
    val style: String,
    val status: String,
    val planApproved: Boolean,
    val thumbnailPath: String?,
    val conceptJson: String,
    val feedbackJson: String?,
    val planJson: String?,
    val editJson: String,
    val captionsJson: String?,
    val brandJson: String?,
    val exportJson: String?,
    // ---- added in schema v2 (Reelify 2.0). Old rows migrate as LEGACY with no template.
    @ColumnInfo(defaultValue = "LEGACY") val mode: String = "LEGACY",
    val templateId: String? = null,
    val templateVersion: Int? = null,
    val templateJson: String? = null,
    val overridesJson: String? = null,
    val scriptJson: String? = null,
)

@Entity(
    tableName = "sections",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId")],
)
data class SectionEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val sectionIndex: Int,
    val locked: Boolean,
    val updatedAt: Long,
    val dataJson: String,
)

@Entity(
    tableName = "section_revisions",
    foreignKeys = [ForeignKey(entity = SectionEntity::class, parentColumns = ["id"], childColumns = ["sectionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sectionId"), Index("projectId")],
)
data class SectionRevisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sectionId: String,
    val projectId: String,
    val createdAt: Long,
    val reason: String,
    val snapshotJson: String,
)

@Entity(
    tableName = "clips",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId"), Index("sectionId")],
)
data class ClipEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val sectionId: String,
    val role: String,
    val ordering: Int,
    val uri: String,
    val createdAt: Long,
    val dataJson: String,
)

@Entity(tableName = "treatments")
data class TreatmentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int,
    val builtIn: Boolean,
    val dataJson: String,
)

@Entity(tableName = "terminology")
data class TerminologyEntity(
    @PrimaryKey val id: String,
    val english: String,
    val dataJson: String,
)

/** Owner-customised production templates (v2). Built-in templates live in code. */
@Entity(tableName = "custom_templates")
data class CustomTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseTemplateId: String?,
    val version: Int,
    val updatedAt: Long,
    val dataJson: String,
)

/** Dialect / tone examples the owner can approve, correct or reject (v2). */
@Entity(tableName = "style_examples", indices = [Index("language")])
data class StyleExampleEntity(
    @PrimaryKey val id: String,
    val language: String,
    val category: String,
    val status: String,
    val updatedAt: Long,
    val dataJson: String,
)

/** Recently used topic/template combinations for variety-aware suggestions (v2). */
@Entity(tableName = "topic_history", indices = [Index("usedAt")])
data class TopicHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val treatmentId: String,
    val templateId: String,
    val topicKey: String,
    val hook: String,
    val usedAt: Long,
)
