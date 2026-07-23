/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.activities

import android.app.ListActivity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.TextView
import com.mavodev.openvpnneo.LaunchVPN
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.VpnProfile
import com.mavodev.openvpnneo.core.OpenVPNService.EXTRA_START_REASON
import com.mavodev.openvpnneo.core.ProfileManager
import java.util.Vector

/**
 * This Activity handles both stages of a launcher shortcut's life cycle: offering the shortcut
 * to the launcher (stage 1) and responding to a click on an installed shortcut (stage 2).
 */
@Suppress("DEPRECATION")
class CreateShortcuts : ListActivity(), OnItemClickListener {

    private lateinit var mPM: ProfileManager

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        mPM = ProfileManager.getInstance(this)
    }

    override fun onStart() {
        super.onStart()
        // Resolve the intent
        createListView()
    }

    private fun createListView() {
        val lv = listView

        val vpnList = mPM.profiles

        val vpnNames = Vector<String>()
        for (vpnProfile in vpnList) {
            vpnNames.add(vpnProfile.mName)
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, vpnNames)
        lv.adapter = adapter

        lv.onItemClickListener = this
    }

    /**
     * Creates a shortcut and returns it to the caller via [setResult].
     */
    private fun setupShortcut(profile: VpnProfile) {
        val shortcutIntent = Intent(Intent.ACTION_MAIN)
        shortcutIntent.setClass(this, LaunchVPN::class.java)
        shortcutIntent.putExtra(LaunchVPN.EXTRA_KEY, profile.getUUID().toString())
        shortcutIntent.putExtra(EXTRA_START_REASON, "shortcut")

        val intent = Intent()
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, profile.name)
        val iconResource = Intent.ShortcutIconResource.fromContext(this, R.mipmap.ic_launcher)
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource)

        setResult(RESULT_OK, intent)
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val profileName = (view as TextView).text.toString()

        val profile = mPM.getProfileByName(profileName)

        setupShortcut(profile)
        finish()
    }
}
