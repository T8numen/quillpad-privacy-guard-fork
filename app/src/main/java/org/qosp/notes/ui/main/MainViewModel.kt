package org.qosp.notes.ui.main

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import org.qosp.notes.components.security.AppLockManager
import org.qosp.notes.R
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.data.sync.core.BackendProvider
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.preferences.SortMethod
import org.qosp.notes.ui.common.AbstractNotesViewModel

class MainViewModel(
    private val noteRepository: NoteRepository,
    private val notebookRepository: NotebookRepository,
    preferenceRepository: PreferenceRepository,
    backendProvider: BackendProvider,
    appLockManager: AppLockManager,
) : AbstractNotesViewModel(preferenceRepository, backendProvider, appLockManager) {

    private val notebookIdFlow: MutableStateFlow<Long?> = MutableStateFlow(null)
    private val isPrivatePageFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)

    override val provideNotes = { sortMethod: SortMethod ->
        combine(notebookIdFlow, isPrivatePageFlow) { notebookId, isPrivatePage ->
            notebookId to isPrivatePage
        }.flatMapLatest { (id, isPrivatePage) ->
            when {
                isPrivatePage -> noteRepository.getPrivateNonDeletedOrArchived(sortMethod)
                id == null -> noteRepository.getNonDeletedOrArchived(sortMethod)
                id == R.id.nav_default_notebook.toLong() -> noteRepository.getNotesWithoutNotebook(sortMethod)
                else -> noteRepository.getByNotebook(id, sortMethod)
            }
        }
    }

    suspend fun notebookExists(notebookId: Long) = notebookRepository.getById(notebookId).firstOrNull() != null

    fun initialize(notebookId: Long?, isPrivatePage: Boolean) {
        notebookIdFlow.value = notebookId
        isPrivatePageFlow.value = isPrivatePage
    }
}
