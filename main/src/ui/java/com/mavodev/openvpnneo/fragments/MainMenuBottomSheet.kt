/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.fragments

import android.content.DialogInterface
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

    override fun getTheme(): Int = R.style.ThemeOverlay_OpenVPNNeo_BottomSheet

    interface Listener {
        fun onMenuSettings()
        fun onMenuShowLog()
        fun onMenuGraph()
        fun onMenuFAQ()
        fun onMenuAbout()
    }

    var listener: Listener? = null

    /**
     * Action to run once the sheet has dismissed. The sheet is dismissed without its
     * exit animation (see [dismissWithAction]) so its window is gone instantly; the
     * action then runs from [onDismiss]. Otherwise the fading sheet window lingers
     * behind the next screen and is pre-rendered by the predictive back gesture,
     * visibly fading out when the user returns.
     */
    private var pendingAction: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_main_menu, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<LinearLayout>(R.id.menu_settings).setOnClickListener {
            dismissWithAction { listener?.onMenuSettings() }
        }

        view.findViewById<LinearLayout>(R.id.menu_show_log).setOnClickListener {
            dismissWithAction { listener?.onMenuShowLog() }
        }

        view.findViewById<LinearLayout>(R.id.menu_graph).setOnClickListener {
            dismissWithAction { listener?.onMenuGraph() }
        }

        view.findViewById<LinearLayout>(R.id.menu_faq).setOnClickListener {
            dismissWithAction { listener?.onMenuFAQ() }
        }

        view.findViewById<LinearLayout>(R.id.menu_about).setOnClickListener {
            dismissWithAction { listener?.onMenuAbout() }
        }
    }

    private fun dismissWithAction(action: () -> Unit) {
        pendingAction = action
        // Disable the window exit animation so the sheet disappears instantly instead
        // of fading; a fading window would linger behind the next screen.
        dialog?.window?.setWindowAnimations(0)
        dismiss()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        val action = pendingAction
        pendingAction = null
        action?.invoke()
    }

    companion object {
        const val TAG = "MainMenuBottomSheet"
    }
}
