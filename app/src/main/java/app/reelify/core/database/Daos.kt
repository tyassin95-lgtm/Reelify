package app.reelify.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observe(id: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: String): ProjectEntity?

    @Upsert
    suspend fun upsert(entity: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM sections WHERE projectId = :projectId ORDER BY sectionIndex")
    fun observeSections(projectId: String): Flow<List<SectionEntity>>

    @Query("SELECT * FROM sections WHERE projectId = :projectId ORDER BY sectionIndex")
    suspend fun getSections(projectId: String): List<SectionEntity>

    @Query("SELECT * FROM sections WHERE id = :id")
    suspend fun getSection(id: String): SectionEntity?

    @Upsert
    suspend fun upsertSections(entities: List<SectionEntity>)

    @Query("DELETE FROM sections WHERE projectId = :projectId AND id NOT IN (:keepIds)")
    suspend fun deleteSectionsExcept(projectId: String, keepIds: List<String>)

    @Insert
    suspend fun insertRevision(entity: SectionRevisionEntity): Long

    @Query("SELECT * FROM section_revisions WHERE sectionId = :sectionId ORDER BY id DESC")
    fun observeRevisions(sectionId: String): Flow<List<SectionRevisionEntity>>

    @Query("SELECT * FROM section_revisions WHERE sectionId = :sectionId ORDER BY id DESC LIMIT 1")
    suspend fun latestRevision(sectionId: String): SectionRevisionEntity?

    @Query("DELETE FROM section_revisions WHERE id = :id")
    suspend fun deleteRevision(id: Long)

    @Query("SELECT COUNT(*) FROM section_revisions WHERE sectionId = :sectionId")
    suspend fun revisionCount(sectionId: String): Int

    @Query("SELECT * FROM clips WHERE projectId = :projectId ORDER BY sectionId, role, ordering")
    fun observeClips(projectId: String): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE projectId = :projectId ORDER BY sectionId, role, ordering")
    suspend fun getClips(projectId: String): List<ClipEntity>

    @Query("SELECT * FROM clips")
    suspend fun getAllClips(): List<ClipEntity>

    @Query("SELECT * FROM clips WHERE id = :id")
    suspend fun getClip(id: String): ClipEntity?

    @Upsert
    suspend fun upsertClips(entities: List<ClipEntity>)

    @Query("DELETE FROM clips WHERE id = :id")
    suspend fun deleteClip(id: String)

    @Query("DELETE FROM clips WHERE projectId = :projectId AND id NOT IN (:keepIds)")
    suspend fun deleteClipsExcept(projectId: String, keepIds: List<String>)

    @Transaction
    suspend fun replaceClips(projectId: String, clips: List<ClipEntity>) {
        if (clips.isEmpty()) deleteAllClips(projectId) else deleteClipsExcept(projectId, clips.map { it.id })
        if (clips.isNotEmpty()) upsertClips(clips)
    }

    @Query("DELETE FROM clips WHERE projectId = :projectId")
    suspend fun deleteAllClips(projectId: String)
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM treatments ORDER BY sortOrder, name")
    fun observeTreatments(): Flow<List<TreatmentEntity>>

    @Query("SELECT * FROM treatments WHERE id = :id")
    suspend fun getTreatment(id: String): TreatmentEntity?

    @Query("SELECT COUNT(*) FROM treatments")
    suspend fun treatmentCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTreatmentsIfAbsent(entities: List<TreatmentEntity>)

    @Upsert
    suspend fun upsertTreatment(entity: TreatmentEntity)

    @Query("DELETE FROM treatments WHERE id = :id AND builtIn = 0")
    suspend fun deleteCustomTreatment(id: String)

    @Query("SELECT * FROM terminology ORDER BY english")
    fun observeTerminology(): Flow<List<TerminologyEntity>>

    @Query("SELECT * FROM terminology ORDER BY english")
    suspend fun getTerminology(): List<TerminologyEntity>

    @Query("SELECT COUNT(*) FROM terminology")
    suspend fun terminologyCount(): Int

    @Upsert
    suspend fun upsertTerminology(entities: List<TerminologyEntity>)

    @Query("DELETE FROM terminology WHERE id = :id")
    suspend fun deleteTerminology(id: String)
}
