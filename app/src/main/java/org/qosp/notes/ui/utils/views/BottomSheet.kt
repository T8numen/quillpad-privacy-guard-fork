package org.qosp.notes.ui.utils.views

import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.parcelize.Parcelize
import org.qosp.notes.R
import org.qosp.notes.ui.utils.resolveAttribute

class BottomSheet : BottomSheetDialogFragment() {
    @Suppress("UNCHECKED_CAST")
    private val actions: Set<Action>? by lazy {
        arguments?.get(MENU_ACTIONS) as? Set<Action>
    }
    private val header: String? by lazy { arguments?.getString(MENU_HEADER) }
    private val showPlaceHolderText by lazy { arguments?.getBoolean(SHOW_PLACEHOLDER) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return NestedScrollView(requireContext()).apply {
            addView(
                LinearLayout(context).apply {
                    context.resolveAttribute(R.attr.colorDrawerBackground)?.let { background = ColorDrawable(it) }
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 0, 0, 16)

                    addView(
                        LinearLayout(context).apply {
                            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                            orientation = LinearLayout.HORIZONTAL

                            addView(
                                AppCompatTextView(context).apply {
                                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                                        weight = 1F
                                    }
                                    TextViewCompat.setTextAppearance(this, R.style.BottomSheetHeader)

                                    text = when {
                                        header.isNullOrBlank() && showPlaceHolderText == true -> context.getString(R.string.indicator_untitled)
                                        else -> header
                                    }
                                }
                            )
                        }
                    )

                    actions?.forEach { action ->
                        addView(buildActionView(action))
                    }
                }
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.setOnShowListener { dialog ->
            val d = dialog as BottomSheetDialog
            val bottomSheet =
                d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return@setOnShowListener
            BottomSheetBehavior.from(bottomSheet).state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    @Parcelize
    class Action(
        val titleResId: Int?,
        val title: String?,
        val iconResId: Int?,
        val dismissAfterClick: Boolean = true,
        val actionState: ActionState = ActionState(),
        val onLongClick: (BottomSheet.() -> ActionState?)? = null,
        val onClick: BottomSheet.() -> Unit,
    ) : Parcelable

    @Parcelize
    data class ActionState(
        @ColorInt val highlightColor: Int? = null,
        val badgeText: String? = null,
        val badgeColors: List<Int> = emptyList(),
    ) : Parcelable

    class Builder {
        val items = mutableSetOf<Action>()

        fun action(
            @StringRes titleResId: Int,
            @DrawableRes iconResId: Int?,
            dismissAfterClick: Boolean = true,
            condition: Boolean = true,
            actionState: ActionState = ActionState(),
            onLongClick: (BottomSheet.() -> ActionState?)? = null,
            onClick: BottomSheet.() -> Unit,
        ) {
            if (condition) {
                items.add(Action(titleResId, null, iconResId, dismissAfterClick, actionState, onLongClick, onClick))
            }
        }

        fun action(
            title: String,
            @DrawableRes iconResId: Int?,
            dismissAfterClick: Boolean = true,
            condition: Boolean = true,
            actionState: ActionState = ActionState(),
            onLongClick: (BottomSheet.() -> ActionState?)? = null,
            onClick: BottomSheet.() -> Unit,
        ) {
            if (condition) {
                items.add(Action(null, title, iconResId, dismissAfterClick, actionState, onLongClick, onClick))
            }
        }
    }

    private fun buildActionView(action: Action): View {
        val density = resources.displayMetrics.density
        val verticalPadding = (12 * density).toInt()
        val horizontalPadding = (16 * density).toInt()
        val iconPadding = (16 * density).toInt()
        val dotSize = (8 * density).toInt()
        val dotMargin = (4 * density).toInt()

        val container = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            orientation = LinearLayout.HORIZONTAL
            background = AppCompatResources.getDrawable(context, R.drawable.state_drawer_item_background)
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val primaryTextView = AppCompatTextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1F)
            TextViewCompat.setTextAppearance(this, R.style.TextAppearance_MaterialComponents_Subtitle2)
            text = action.title ?: action.titleResId?.let(::getString) ?: ""
            compoundDrawablePadding = iconPadding
            action.iconResId?.let { iconResId ->
                setCompoundDrawablesRelativeWithIntrinsicBounds(iconResId, 0, 0, 0)
            }
        }

        val badgeContainer = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.END
        }

        val badgeTextView = AppCompatTextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            TextViewCompat.setTextAppearance(this, R.style.TextAppearance_MaterialComponents_Body2)
        }

        container.addView(primaryTextView)
        container.addView(badgeContainer)

        applyActionState(
            primaryTextView = primaryTextView,
            badgeContainer = badgeContainer,
            badgeTextView = badgeTextView,
            badgeText = action.actionState.badgeText,
            badgeColors = action.actionState.badgeColors,
            highlightColor = action.actionState.highlightColor,
            dotSize = dotSize,
            dotMargin = dotMargin,
        )

        container.setOnClickListener {
            action.onClick(this@BottomSheet)
            if (action.dismissAfterClick) dismiss()
        }
        action.onLongClick?.let { onLongClick ->
            container.setOnLongClickListener {
                onLongClick(this@BottomSheet)?.let { state ->
                    applyActionState(
                        primaryTextView = primaryTextView,
                        badgeContainer = badgeContainer,
                        badgeTextView = badgeTextView,
                        badgeText = state.badgeText,
                        badgeColors = state.badgeColors,
                        highlightColor = state.highlightColor,
                        dotSize = dotSize,
                        dotMargin = dotMargin,
                    )
                }
                true
            }
        }

        return container
    }

    private fun applyActionState(
        primaryTextView: AppCompatTextView,
        badgeContainer: LinearLayout,
        badgeTextView: AppCompatTextView,
        badgeText: String?,
        badgeColors: List<Int>,
        @ColorInt highlightColor: Int?,
        dotSize: Int,
        dotMargin: Int,
    ) {
        val defaultColor = requireContext().resolveAttribute(R.attr.colorControlNormal) ?: 0
        val resolvedColor = highlightColor ?: defaultColor

        primaryTextView.setTextColor(resolvedColor)
        TextViewCompat.setCompoundDrawableTintList(primaryTextView, ColorStateList.valueOf(resolvedColor))

        badgeContainer.removeAllViews()
        badgeContainer.isVisible = badgeText != null || badgeColors.isNotEmpty()

        badgeColors.take(3).forEachIndexed { index, color ->
            badgeContainer.addView(View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                    marginStart = if (index == 0) 0 else dotMargin
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                }
            })
        }

        if (!badgeText.isNullOrBlank()) {
            badgeTextView.text = badgeText
            badgeTextView.setTextColor(defaultColor)
            if (badgeTextView.parent != null) {
                (badgeTextView.parent as ViewGroup).removeView(badgeTextView)
            }
            badgeContainer.addView(badgeTextView.apply {
                (layoutParams as LinearLayout.LayoutParams).marginStart = if (badgeColors.isEmpty()) 0 else dotMargin * 2
            })
        }
    }

    companion object {
        const val MENU_HEADER = "SHEET_HEADER"
        const val MENU_ACTIONS = "SHEET_ACTIONS"
        const val SHOW_PLACEHOLDER = "SHOW_PLACEHOLDER"

        fun show(
            header: String?,
            fragmentManager: FragmentManager,
            showPlaceHolderText: Boolean = true,
            itemBuilder: (Builder.() -> Unit)?
        ) {
            val builder = Builder()
            itemBuilder?.invoke(builder)
            BottomSheet().apply {
                arguments = bundleOf(
                    MENU_HEADER to header,
                    MENU_ACTIONS to builder.items,
                    SHOW_PLACEHOLDER to showPlaceHolderText
                )
                show(fragmentManager, null)
            }
        }
    }
}
