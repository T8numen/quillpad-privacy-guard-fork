package org.qosp.notes.ui.archive

import org.qosp.notes.components.security.AppLockManager
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.sync.core.BackendProvider
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.ui.common.AbstractNotesViewModel

class ArchiveViewModel(
    noteRepository: NoteRepository,
    preferenceRepository: PreferenceRepository,
    backendProvider: BackendProvider,
    appLockManager: AppLockManager,
) : AbstractNotesViewModel(preferenceRepository, backendProvider, appLockManager) {
    override val provideNotes = noteRepository::getArchived
}
