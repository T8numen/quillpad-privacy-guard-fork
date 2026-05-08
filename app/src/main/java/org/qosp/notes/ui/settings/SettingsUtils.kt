package org.qosp.notes.ui.settings

import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.msoul.datastore.EnumPreference
import org.qosp.notes.R
import org.qosp.notes.preferences.HasNameResource
import org.qosp.notes.preferences.HasSupportRequirement

inline fun <reified T> Fragment.showPreferenceDialog(
    titleRes: Int,
    selected: T,
    dismiss: Boolean = true,
    items: Array<String>? = null,
    crossinline onClick: (T) -> Unit,
) where T : Enum<T>, T : EnumPreference {
    val supportedValues = enumValues<T>()
        .filter {
            it !is HasSupportRequirement || it.isSupported()
        }
    val selectedIndex = supportedValues.indexOf(selected)
    val items = items ?: supportedValues
        .map {
            if (it is HasNameResource) requireContext().getString(it.nameResource) else ""
        }
        .toTypedArray()

    MaterialAlertDialogBuilder(requireContext())
        .setTitle(requireContext().getString(titleRes))
        .setSingleChoiceItems(items, selectedIndex) { dialogInterface, which ->
            if (dismiss) dialogInterface.dismiss()
            onClick(supportedValues[which])
        }
        .setPositiveButton(getString(R.string.action_done)) { dialogInterface, i ->
            dialogInterface.dismiss()
        }
        .show()
}
