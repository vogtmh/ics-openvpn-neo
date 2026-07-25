/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.views

import android.content.Context
import android.util.AttributeSet
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.mavodev.openvpnneo.R

/**
 * Makes a dialog-opening preference row behave like the other profile-editor
 * selectors: the title/summary label has no action, only the arrow "›" button on
 * the right opens the dialog. The button reuses the preference's own click
 * handling (so ListPreference/EditTextPreference/DialogPreference and plain
 * click-listener preferences all keep working), it is just the sole click target.
 */
internal object SelectorPreferenceBinder {
    fun bind(preference: Preference, holder: PreferenceViewHolder) {
        // The whole row must not react to taps any more.
        holder.itemView.setOnClickListener(null)
        holder.itemView.isClickable = false
        holder.itemView.isFocusable = false

        // Only the selector button opens the dialog.
        val button = holder.findViewById(R.id.ovpn_selector_button)
        if (button != null) {
            button.isClickable = true
            button.isFocusable = true
            button.setOnClickListener { preference.performClick() }
        }
    }
}

/** [ListPreference] whose dialog is opened only by the arrow selector button. */
class SelectorListPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ListPreference(context, attrs) {
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        SelectorPreferenceBinder.bind(this, holder)
    }
}

/** [EditTextPreference] whose dialog is opened only by the arrow selector button. */
class SelectorEditTextPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : EditTextPreference(context, attrs) {
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        SelectorPreferenceBinder.bind(this, holder)
    }
}

/**
 * Plain [Preference] (e.g. the file-picker rows) whose action is triggered only by
 * the arrow selector button; the row's own click listener is invoked via
 * [Preference.performClick].
 */
class SelectorPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        SelectorPreferenceBinder.bind(this, holder)
    }
}
