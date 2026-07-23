/*
 * Copyright (c) 2012-2018 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.views

import android.content.Context
import android.util.AttributeSet
import androidx.preference.ListPreference
import com.mavodev.openvpnneo.core.ProfileManager

class DefaultVPNListPreference(context: Context, attrs: AttributeSet?) : ListPreference(context, attrs) {
    init {
        setVPNs(context)
    }

    private fun setVPNs(c: Context) {
        val pm = ProfileManager.getInstance(c)
        val profiles = pm.profiles
        entries = profiles.map { it.name as CharSequence }.toTypedArray()
        entryValues = profiles.map { it.getUUIDString() as CharSequence }.toTypedArray()
    }
}
