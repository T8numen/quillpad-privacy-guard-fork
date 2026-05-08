package org.qosp.notes.data.repo

import kotlinx.coroutines.flow.Flow
import org.qosp.notes.data.dao.NoteQuickSwitchBindingDao
import org.qosp.notes.data.model.NoteQuickSwitchBinding

class NoteQuickSwitchRepository(
    private val dao: NoteQuickSwitchBindingDao,
) {
    fun observeAll(): Flow<List<NoteQuickSwitchBinding>> = dao.getAll()

    suspend fun getCount(): Int = dao.getCount()

    suspend fun getBySourceId(sourceNoteId: Long): NoteQuickSwitchBinding? = dao.getBySourceId(sourceNoteId)

    suspend fun upsert(binding: NoteQuickSwitchBinding) = dao.upsert(binding)

    suspend fun deleteBySourceId(sourceNoteId: Long) = dao.deleteBySourceId(sourceNoteId)
}
