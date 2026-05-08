package org.qosp.notes.ui.main

import androidx.navigation.NavDirections
import org.qosp.notes.R
import org.qosp.notes.data.model.Attachment

class PrivacyFragment : MainFragment() {
    override val currentDestinationId: Int = R.id.fragment_privacy
    override val toolbarTitle: String
        get() = getString(R.string.nav_notes)
    override val isPrivatePage: Boolean = true

    override fun actionToEditor(
        transitionName: String,
        noteId: Long,
        attachments: List<Attachment>,
        isList: Boolean,
    ): NavDirections =
        PrivacyFragmentDirections.actionPrivacyToEditor(transitionName)
            .setNoteId(noteId)
            .setNewNoteAttachments(attachments.toTypedArray())
            .setNewNoteIsList(isList)
            .setNewNoteIsPrivate(true)

    override fun actionToSearch(searchQuery: String): NavDirections =
        PrivacyFragmentDirections.actionPrivacyToSearch()
            .setSearchQuery(searchQuery)
            .setIsPrivatePage(true)
}
