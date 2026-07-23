/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.fragments

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import com.mavodev.openvpnneo.R

class Settings_Routing : OpenVpnPreferencesFragment(), Preference.OnPreferenceChangeListener {
    private lateinit var mCustomRoutes: EditTextPreference
    private lateinit var mUseDefaultRoute: SwitchPreference
    private lateinit var mCustomRoutesv6: EditTextPreference
    private lateinit var mUseDefaultRoutev6: SwitchPreference
    private lateinit var mRouteNoPull: SwitchPreference
    private lateinit var mLocalVPNAccess: SwitchPreference
    private lateinit var mExcludedRoutes: EditTextPreference
    private lateinit var mExcludedRoutesv6: EditTextPreference
    private lateinit var mBlockUnusedAF: SwitchPreference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.vpn_routing)
        mCustomRoutes = findPreference("customRoutes")!!
        mUseDefaultRoute = findPreference("useDefaultRoute")!!
        mCustomRoutesv6 = findPreference("customRoutesv6")!!
        mUseDefaultRoutev6 = findPreference("useDefaultRoutev6")!!
        mExcludedRoutes = findPreference("excludedRoutes")!!
        mExcludedRoutesv6 = findPreference("excludedRoutesv6")!!

        mRouteNoPull = findPreference("routenopull")!!
        mLocalVPNAccess = findPreference("unblockLocal")!!

        mBlockUnusedAF = findPreference("blockUnusedAF")!!

        mCustomRoutes.onPreferenceChangeListener = this
        mCustomRoutesv6.onPreferenceChangeListener = this
        mExcludedRoutes.onPreferenceChangeListener = this
        mExcludedRoutesv6.onPreferenceChangeListener = this
        mBlockUnusedAF.onPreferenceChangeListener = this

        loadSettings()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    }

    override fun loadSettings() {
        mUseDefaultRoute.isChecked = mProfile.mUseDefaultRoute
        mUseDefaultRoutev6.isChecked = mProfile.mUseDefaultRoutev6

        mCustomRoutes.text = mProfile.mCustomRoutes
        mCustomRoutesv6.text = mProfile.mCustomRoutesv6

        mExcludedRoutes.text = mProfile.mExcludedRoutes
        mExcludedRoutesv6.text = mProfile.mExcludedRoutesv6

        mRouteNoPull.isChecked = mProfile.mRoutenopull
        mLocalVPNAccess.isChecked = mProfile.mAllowLocalLAN

        mBlockUnusedAF.isChecked = mProfile.mBlockUnusedAddressFamilies

        // Sets Summary
        onPreferenceChange(mCustomRoutes, mCustomRoutes.text)
        onPreferenceChange(mCustomRoutesv6, mCustomRoutesv6.text)
        onPreferenceChange(mExcludedRoutes, mExcludedRoutes.text)
        onPreferenceChange(mExcludedRoutesv6, mExcludedRoutesv6.text)
    }

    override fun saveSettings() {
        mProfile.mUseDefaultRoute = mUseDefaultRoute.isChecked
        mProfile.mUseDefaultRoutev6 = mUseDefaultRoutev6.isChecked
        mProfile.mCustomRoutes = mCustomRoutes.text
        mProfile.mCustomRoutesv6 = mCustomRoutesv6.text
        mProfile.mRoutenopull = mRouteNoPull.isChecked
        mProfile.mAllowLocalLAN = mLocalVPNAccess.isChecked
        mProfile.mExcludedRoutes = mExcludedRoutes.text
        mProfile.mExcludedRoutesv6 = mExcludedRoutesv6.text
        mProfile.mBlockUnusedAddressFamilies = mBlockUnusedAF.isChecked
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        if (preference === mCustomRoutes || preference === mCustomRoutesv6 ||
            preference === mExcludedRoutes || preference === mExcludedRoutesv6
        ) {
            preference.summary = newValue as String?
        }

        saveSettings()
        return true
    }
}
