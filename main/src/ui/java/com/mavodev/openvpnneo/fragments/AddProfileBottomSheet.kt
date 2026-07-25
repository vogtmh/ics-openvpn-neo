/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.fragments

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mavodev.openvpnneo.R

/**
 * Bottom sheet shown when the user taps the FAB (+) button on the profile list.
 * Offers up to four paths: create manually, import from file, import from remote, and —
 * only when the "free servers" feature is enabled in Settings — browse free VPNGate servers.
 */
class AddProfileBottomSheet : BottomSheetDialogFragment() {

    override fun getTheme(): Int = R.style.ThemeOverlay_OpenVPNNeo_BottomSheet

    interface Listener {
        fun onCreateManually()
        fun onImportFromFile()
        fun onImportFromRemote()
        fun onBrowseFreeServers()
    }

    var listener: Listener? = null

    /** Whether the "Browse free servers" option should be offered (opt-in feature). */
    var freeServersEnabled: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_add_profile, container, false)

    // Open fully expanded so the (potentially tall) list isn't clipped at the default
    // collapsed peek height — especially in landscape / on short screens.
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener
            BottomSheetBehavior.from(sheet).apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
            }
        }
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<LinearLayout>(R.id.option_create_manually).setOnClickListener {
            dismiss()
            listener?.onCreateManually()
        }

        view.findViewById<LinearLayout>(R.id.option_import_file).setOnClickListener {
            dismiss()
            listener?.onImportFromFile()
        }

        view.findViewById<LinearLayout>(R.id.option_import_remote).setOnClickListener {
            dismiss()
            listener?.onImportFromRemote()
        }

        val freeServersOption = view.findViewById<LinearLayout>(R.id.option_free_servers)
        freeServersOption.visibility = if (freeServersEnabled) View.VISIBLE else View.GONE
        freeServersOption.setOnClickListener {
            dismiss()
            listener?.onBrowseFreeServers()
        }
    }

    companion object {
        const val TAG = "AddProfileBottomSheet"
    }
}
