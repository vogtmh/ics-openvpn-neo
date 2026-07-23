/*
 * Copyright (c) 2012-2015 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.VpnProfile
import com.mavodev.openvpnneo.core.ProfileManager

abstract class Settings_Fragment : Fragment() {

    protected lateinit var mProfile: VpnProfile

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profileUuid = requireArguments().getString(requireActivity().packageName + ".profileUUID")
        mProfile = ProfileManager.get(requireActivity(), profileUuid)
        requireActivity().setTitle(getString(R.string.edit_profile_title, mProfile.name))
    }

    override fun onPause() {
        super.onPause()
        savePreferences()
    }

    protected abstract fun savePreferences()
}
