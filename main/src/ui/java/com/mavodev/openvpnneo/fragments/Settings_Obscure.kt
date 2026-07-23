/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.fragments

import android.os.Bundle
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.VpnProfile
import java.util.Locale

class Settings_Obscure : OpenVpnPreferencesFragment(), Preference.OnPreferenceChangeListener {
    private lateinit var mUseRandomHostName: SwitchPreference
    private lateinit var mUseFloat: SwitchPreference
    private lateinit var mUseCustomConfig: SwitchPreference
    private lateinit var mCustomConfig: EditTextPreference
    private lateinit var mMssFixValue: EditTextPreference
    private lateinit var mMssFixCheckBox: SwitchPreference
    private lateinit var mPeerInfo: SwitchPreference

    private lateinit var mPersistent: SwitchPreference
    private lateinit var mConnectRetrymax: ListPreference
    private lateinit var mConnectRetry: EditTextPreference
    private lateinit var mConnectRetryMaxTime: EditTextPreference
    private lateinit var mTunMtu: EditTextPreference

    private fun onCreateBehaviour() {
        mPersistent = findPreference("usePersistTun")!!
        mConnectRetrymax = findPreference("connectretrymax")!!
        mConnectRetry = findPreference("connectretry")!!
        mConnectRetryMaxTime = findPreference("connectretrymaxtime")!!

        mPeerInfo = findPreference("peerInfo")!!

        mConnectRetrymax.onPreferenceChangeListener = this
        mConnectRetrymax.summary = "%s"

        mConnectRetry.onPreferenceChangeListener = this
        mConnectRetryMaxTime.onPreferenceChangeListener = this
    }

    private fun loadSettingsBehaviour() {
        mPersistent.isChecked = mProfile.mPersistTun
        mPeerInfo.isChecked = mProfile.mPushPeerInfo

        mConnectRetrymax.value = mProfile.mConnectRetryMax
        onPreferenceChange(mConnectRetrymax, mProfile.mConnectRetryMax)

        mConnectRetry.text = mProfile.mConnectRetry
        onPreferenceChange(mConnectRetry, mProfile.mConnectRetry)

        mConnectRetryMaxTime.text = mProfile.mConnectRetryMaxTime
        onPreferenceChange(mConnectRetryMaxTime, mProfile.mConnectRetryMaxTime)
    }

    private fun saveSettingsBehaviour() {
        mProfile.mConnectRetryMax = mConnectRetrymax.value
        mProfile.mPersistTun = mPersistent.isChecked
        mProfile.mConnectRetry = mConnectRetry.text
        mProfile.mPushPeerInfo = mPeerInfo.isChecked
        mProfile.mConnectRetryMaxTime = mConnectRetryMaxTime.text
    }

    private fun onPreferenceChangeBehaviour(preference: Preference, newValue: Any?): Boolean {
        var value = newValue
        if (preference === mConnectRetrymax) {
            if (value == null) {
                value = "5"
            }
            mConnectRetrymax.setDefaultValue(value)

            for (i in mConnectRetrymax.entryValues.indices) {
                if (mConnectRetrymax.entryValues == value) {
                    mConnectRetrymax.summary = mConnectRetrymax.entries[i]
                }
            }
        } else if (preference === mConnectRetry) {
            if (value == null || value == "") value = "2"
            mConnectRetry.summary = String.format("%s s", value)
        } else if (preference === mConnectRetryMaxTime) {
            if (value == null || value == "") value = "300"
            mConnectRetryMaxTime.summary = String.format("%s s", value)
        }

        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.vpn_obscure)

        mUseRandomHostName = findPreference("useRandomHostname")!!
        mUseFloat = findPreference("useFloat")!!
        mUseCustomConfig = findPreference("enableCustomOptions")!!
        mCustomConfig = findPreference("customOptions")!!
        mMssFixCheckBox = findPreference("mssFix")!!
        mMssFixValue = findPreference("mssFixValue")!!
        mMssFixValue.onPreferenceChangeListener = this
        mTunMtu = findPreference("tunmtu")!!
        mTunMtu.onPreferenceChangeListener = this

        onCreateBehaviour()
        loadSettings()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    }

    override fun loadSettings() {
        mUseRandomHostName.isChecked = mProfile.mUseRandomHostname
        mUseFloat.isChecked = mProfile.mUseFloat
        mUseCustomConfig.isChecked = mProfile.mUseCustomConfig
        mCustomConfig.text = mProfile.mCustomConfigOptions

        if (mProfile.mMssFix == 0) {
            mMssFixValue.text = VpnProfile.DEFAULT_MSSFIX_SIZE.toString()
            mMssFixCheckBox.isChecked = false
            setMssSummary(VpnProfile.DEFAULT_MSSFIX_SIZE)
        } else {
            mMssFixValue.text = mProfile.mMssFix.toString()
            mMssFixCheckBox.isChecked = true
            setMssSummary(mProfile.mMssFix)
        }

        var tunmtu = mProfile.mTunMtu
        if (mProfile.mTunMtu < 48) tunmtu = 1500

        mTunMtu.text = tunmtu.toString()
        setMtuSummary(tunmtu)

        loadSettingsBehaviour()
    }

    private fun setMssSummary(value: Int) {
        mMssFixValue.summary = String.format(Locale.getDefault(), "Configured MSS value: %d", value)
    }

    private fun setMtuSummary(value: Int) {
        mTunMtu.summary = if (value == 1500) {
            String.format(Locale.getDefault(), "Using default (1500) MTU")
        } else {
            String.format(Locale.getDefault(), "Configured MTU value: %d", value)
        }
    }

    override fun saveSettings() {
        mProfile.mUseRandomHostname = mUseRandomHostName.isChecked
        mProfile.mUseFloat = mUseFloat.isChecked
        mProfile.mUseCustomConfig = mUseCustomConfig.isChecked
        mProfile.mCustomConfigOptions = mCustomConfig.text
        mProfile.mMssFix = if (mMssFixCheckBox.isChecked) {
            mMssFixValue.text!!.toInt()
        } else {
            0
        }

        mProfile.mTunMtu = mTunMtu.text!!.toInt()
        saveSettingsBehaviour()
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        if (preference.key == "mssFixValue") {
            try {
                val v = (newValue as String).toInt()
                if (v < 0 || v > 9000) throw NumberFormatException("mssfix value")
                setMssSummary(v)
            } catch (e: NumberFormatException) {
                Toast.makeText(activity, R.string.mssfix_invalid_value, Toast.LENGTH_LONG).show()
                return false
            }
        } else if (preference.key == "tunmtu") {
            try {
                val v = (newValue as String).toInt()
                if (v < 48 || v > 9000) throw NumberFormatException("mtu value")
                setMtuSummary(v)
            } catch (e: NumberFormatException) {
                Toast.makeText(activity, R.string.mtu_invalid_value, Toast.LENGTH_LONG).show()
                return false
            }
        }
        return onPreferenceChangeBehaviour(preference, newValue)
    }
}
