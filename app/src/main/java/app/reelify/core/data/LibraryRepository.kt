package app.reelify.core.data

import app.reelify.core.database.LibraryDao
import app.reelify.core.database.TerminologyEntity
import app.reelify.core.database.TreatmentEntity
import app.reelify.core.model.TerminologyEntry
import app.reelify.core.model.Treatment
import app.reelify.core.util.AppJson
import app.reelify.core.util.Clock
import app.reelify.core.util.decodeOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LibraryRepository(private val dao: LibraryDao, private val clock: Clock = Clock.System) {

    suspend fun seedIfNeeded() {
        dao.insertTreatmentsIfAbsent(TreatmentSeed.treatments.map { it.toEntity() })
        if (dao.terminologyCount() == 0) dao.upsertTerminology(TreatmentSeed.terminology.map { it.toEntity() })
    }

    fun observeTreatments(): Flow<List<Treatment>> = dao.observeTreatments().map { l -> l.mapNotNull { it.toDomain() } }

    suspend fun getTreatment(id: String): Treatment? = dao.getTreatment(id)?.toDomain()

    suspend fun saveTreatment(t: Treatment) = dao.upsertTreatment(t.copy(updatedAt = clock.now()).toEntity())

    suspend fun deleteTreatment(id: String) = dao.deleteCustomTreatment(id)

    /** Restores the shipped profile for a built-in treatment. */
    suspend fun resetTreatment(id: String) {
        TreatmentSeed.treatments.firstOrNull { it.id == id }?.let { dao.upsertTreatment(it.toEntity()) }
    }

    fun observeTerminology(): Flow<List<TerminologyEntry>> = dao.observeTerminology().map { l -> l.mapNotNull { it.toDomain() } }

    suspend fun terminology(): List<TerminologyEntry> = dao.getTerminology().mapNotNull { it.toDomain() }

    suspend fun saveTerm(t: TerminologyEntry) = dao.upsertTerminology(listOf(t.toEntity()))

    suspend fun deleteTerm(id: String) = dao.deleteTerminology(id)

    private fun Treatment.toEntity() = TreatmentEntity(id, name, sortOrder, builtIn, AppJson.encodeToString(Treatment.serializer(), this))
    private fun TreatmentEntity.toDomain() = AppJson.decodeOrNull<Treatment>(dataJson)
    private fun TerminologyEntry.toEntity() = TerminologyEntity(id, english, AppJson.encodeToString(TerminologyEntry.serializer(), this))
    private fun TerminologyEntity.toDomain() = AppJson.decodeOrNull<TerminologyEntry>(dataJson)
}
