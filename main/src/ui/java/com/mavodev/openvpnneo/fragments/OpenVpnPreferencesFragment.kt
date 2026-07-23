/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.fragments

import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.VpnProfile
import com.mavodev.openvpnneo.core.ProfileManager

abstract class OpenVpnPreferencesFragment : PreferenceFragmentCompat() {

    protected lateinit var mProfile: VpnProfile

    protected abstract fun loadSettings()
    protected abstract fun saveSettings()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val profileUUID = requireArguments().getString(requireActivity().packageName + ".profileUUID")
        mProfile = ProfileManager.get(requireActivity(), profileUUID)
        requireActivity().setTitle(getString(R.string.edit_profile_title, mProfile.name))
    }

    override fun onPause() {
        super.onPause()
        saveSettings()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState != null) {
            val profileUUID = savedInstanceState.getString(VpnProfile.EXTRA_PROFILEUUID)
            mProfile = ProfileManager.get(requireActivity(), profileUUID)
            loadSettings()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (view != null) {
            //if we have no view, there is no point in trying to save anything.
            saveSettings()
        }
        outState.putString(VpnProfile.EXTRA_PROFILEUUID, mProfile.getUUIDString())
    }
}
