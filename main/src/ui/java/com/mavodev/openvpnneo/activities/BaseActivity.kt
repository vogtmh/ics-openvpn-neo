/*
 * Copyright (c) 2012-2015 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.activities

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.core.GlobalPreferences
import com.mavodev.openvpnneo.core.LocaleHelper

abstract class BaseActivity : AppCompatActivity() {
    val isAndroidTV: Boolean
        get() {
            val uiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
            return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        }

    protected fun checkMinimalUIDisabled() {
        if (GlobalPreferences.getMinimalUi()) {
            Toast.makeText(this, R.string.minimal_ui_not_available, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (isAndroidTV) {
            requestWindowFeature(Window.FEATURE_OPTIONS_PANEL)
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK)
        )
        super.onCreate(savedInstanceState)

        // Single source of truth for the opaque black status bar: draw a black strip
        // behind the transparent (edge-to-edge) status bar, sized to the status bar inset.
        // Keeps black bars in both light and dark mode without per-activity hacks.
        window.decorView.post {
            val decorView = window.decorView as? ViewGroup ?: return@post
            val statusBarBg = View(this@BaseActivity).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
            }
            // Seed the height from the current insets so it is opaque immediately;
            // the listener keeps it correct across rotation / inset changes.
            val initialTop = ViewCompat.getRootWindowInsets(decorView)
                ?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
            decorView.addView(
                statusBarBg,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, initialTop)
            )
            ViewCompat.setOnApplyWindowInsetsListener(statusBarBg) { v, windowInsets ->
                v.layoutParams.height =
                    windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                v.requestLayout()
                windowInsets
            }
            ViewCompat.requestApplyInsets(decorView)
        }
    }

    fun setUpEdgeEdgeInsetsListener(
        rootView: View,
        contentViewId: Int = R.id.root_linear_layout,
        setupBottom: Boolean = true
    ) {
        val contentView = rootView.findViewById<View>(contentViewId)

        ViewCompat.setOnApplyWindowInsetsListener(contentView) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.top
            }

            v.updatePadding(
                left = insets.left,
                right = insets.right,
            )
            if (setupBottom) {
                v.updatePadding(bottom = insets.bottom)
                WindowInsetsCompat.CONSUMED
            } else {
                windowInsets
            }
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.updateResources(base))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleHelper.onConfigurationChange(this)
    }
}
