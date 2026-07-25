/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.views

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.use
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mavodev.openvpnneo.R

/**
 * A drop-in replacement for [android.widget.Spinner] in the profile editor.
 *
 * It shows a title and the current value on the left and an arrow selector button
 * on the right. Only the button is interactive (the label has no action); tapping
 * it opens a single-choice overlay dialog, matching the selectors used elsewhere
 * in the editor. This keeps every selection control looking and behaving the same.
 */
class SelectionField @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val titleView: TextView
    private val valueView: TextView

    private var entries: List<CharSequence> = emptyList()

    /** Invoked with the newly selected index when the user picks a different item. */
    var onSelectionChanged: ((Int) -> Unit)? = null

    /** The currently selected index (mirrors Spinner.getSelectedItemPosition()). */
    var selectedItemPosition: Int = 0
        private set

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        inflate(context, R.layout.selection_field, this)

        titleView = findViewById(R.id.selection_title)
        valueView = findViewById(R.id.selection_value)

        context.obtainStyledAttributes(attrs, R.styleable.SelectionField).use { ta ->
            ta.getText(R.styleable.SelectionField_selectionTitle)?.let { titleView.text = it }
            val entriesId = ta.getResourceId(R.styleable.SelectionField_android_entries, 0)
            if (entriesId != 0) {
                entries = resources.getTextArray(entriesId).toList()
            }
        }

        findViewById<android.view.View>(R.id.selection_button).setOnClickListener { showDialog() }
        updateValue()
    }

    fun setEntries(newEntries: List<CharSequence>) {
        entries = newEntries
        if (selectedItemPosition >= entries.size) selectedItemPosition = 0
        updateValue()
    }

    /** Sets the selection without notifying [onSelectionChanged] (mirrors Spinner.setSelection). */
    fun setSelection(position: Int) {
        selectedItemPosition = position
        updateValue()
    }

    private fun updateValue() {
        valueView.text = entries.getOrNull(selectedItemPosition) ?: ""
        valueView.visibility = if (valueView.text.isNullOrEmpty()) GONE else VISIBLE
    }

    private fun showDialog() {
        if (entries.isEmpty()) return
        MaterialAlertDialogBuilder(context)
            .setTitle(titleView.text)
            .setSingleChoiceItems(entries.toTypedArray(), selectedItemPosition) { dialog, which ->
                dialog.dismiss()
                if (which != selectedItemPosition) {
                    setSelection(which)
                    onSelectionChanged?.invoke(which)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
