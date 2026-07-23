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
 * Bottom sheet shown when the user taps the main menu button on the profile list.
 * Replaces the former top-right overflow menu.
 */
class MainMenuBottomSheet : BottomSheetDialogFragment() {

    override fun getTheme(): Int = R.style.blinkt_BottomSheet

    interface Listener {
        fun onMenuChangeSorting()
        fun onMenuSettings()
        fun onMenuShowLog()
        fun onMenuGraph()
        fun onMenuOpenSSLSpeed()
        fun onMenuFAQ()
        fun onMenuAbout()
    }

    var listener: Listener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_main_menu, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<LinearLayout>(R.id.menu_change_sorting).setOnClickListener {
            dismiss()
            listener?.onMenuChangeSorting()
        }

        view.findViewById<LinearLayout>(R.id.menu_settings).setOnClickListener {
            dismiss()
            listener?.onMenuSettings()
        }

        view.findViewById<LinearLayout>(R.id.menu_show_log).setOnClickListener {
            dismiss()
            listener?.onMenuShowLog()
        }

        view.findViewById<LinearLayout>(R.id.menu_graph).setOnClickListener {
            dismiss()
            listener?.onMenuGraph()
        }

        view.findViewById<LinearLayout>(R.id.menu_openssl_speed).setOnClickListener {
            dismiss()
            listener?.onMenuOpenSSLSpeed()
        }

        view.findViewById<LinearLayout>(R.id.menu_faq).setOnClickListener {
            dismiss()
            listener?.onMenuFAQ()
        }

        view.findViewById<LinearLayout>(R.id.menu_about).setOnClickListener {
            dismiss()
            listener?.onMenuAbout()
        }
    }

    companion object {
        const val TAG = "MainMenuBottomSheet"
    }
}
