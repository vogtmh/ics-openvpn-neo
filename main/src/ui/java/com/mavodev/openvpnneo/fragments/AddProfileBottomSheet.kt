/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mavodev.openvpnneo.R

/**
 * Bottom sheet shown when the user taps the FAB (+) button on the profile list.
 * Offers three paths: create manually, import from file, import from remote.
 */
class AddProfileBottomSheet : BottomSheetDialogFragment() {

    override fun getTheme(): Int = R.style.blinkt_BottomSheet

    interface Listener {
        fun onCreateManually()
        fun onImportFromFile()
        fun onImportFromRemote()
    }

    var listener: Listener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_add_profile, container, false)

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
    }

    companion object {
        const val TAG = "AddProfileBottomSheet"
    }
}
