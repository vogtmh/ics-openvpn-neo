/*
 * Copyright (c) 2012-2015 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.fragments

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.VpnProfile
import com.mavodev.openvpnneo.api.AppRestrictions

internal class Settings_UserEditable : KeyChainSettingsFragment() {

    private lateinit var mView: View

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        mView = inflater.inflate(R.layout.settings_usereditable, container, false)
        val messageView = mView.findViewById<TextView>(R.id.messageUserEdit)
        messageView.text = getString(R.string.message_no_user_edit, getPackageString(mProfile.mProfileCreator))
        initKeychainViews(mView)
        return mView
    }

    private fun getPackageString(packageName: String?): String {
        if (AppRestrictions.PROFILE_CREATOR == packageName) {
            return "Android Enterprise Management"
        }

        val pm = requireActivity().packageManager
        val ai = try {
            pm.getApplicationInfo(packageName ?: "", 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        val applicationName: CharSequence = if (ai != null) pm.getApplicationLabel(ai) else "(unknown)"
        return String.format("%s (%s)", applicationName, packageName)
    }

    override fun savePreferences() {
    }

    override fun onResume() {
        super.onResume()
        mView.findViewById<View>(R.id.keystore).visibility = View.GONE
        if (mProfile.mAuthenticationType == VpnProfile.TYPE_USERPASS_KEYSTORE ||
            mProfile.mAuthenticationType == VpnProfile.TYPE_KEYSTORE
        ) {
            mView.findViewById<View>(R.id.keystore).visibility = View.VISIBLE
        }
    }
}
