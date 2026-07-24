/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.fragments

import android.os.Bundle
import android.preference.PreferenceManager
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.widget.doAfterTextChanged
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.activities.VPNPreferences
import com.mavodev.openvpnneo.core.Connection

/**
 * A user friendly "Basic" tab exposing the most commonly used profile settings:
 * profile name, password, default profile, a read-only server overview and the
 * reconnection settings.
 */
class Settings_Overview : Settings_Fragment() {

    private lateinit var mProfileName: EditText
    private lateinit var mKeyPassword: EditText
    private lateinit var mKeyPassLayout: View
    private lateinit var mMakeDefaultProfile: CompoundButton
    private lateinit var mConnectRetryMax: Spinner
    private lateinit var mConnectRetry: EditText
    private lateinit var mConnectRetryMaxTime: EditText
    private lateinit var mServerRecyclerView: RecyclerView
    private var mServerAdapter: ServerOverviewAdapter? = null
    private var viewInitialized = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.basic_overview, container, false)

        mProfileName = v.findViewById(R.id.profilename)
        mKeyPassword = v.findViewById(R.id.key_password)
        mKeyPassLayout = v.findViewById(R.id.basic_keypass_layout)
        mMakeDefaultProfile = v.findViewById(R.id.make_default_profile)
        mConnectRetryMax = v.findViewById(R.id.connectretrymax)
        mConnectRetry = v.findViewById(R.id.connectretry)
        mConnectRetryMaxTime = v.findViewById(R.id.connectretrymaxtime)

        mServerRecyclerView = v.findViewById(R.id.server_recycler_view)
        mServerRecyclerView.layoutManager = LinearLayoutManager(activity)
        mServerRecyclerView.isNestedScrollingEnabled = false
        mServerAdapter = ServerOverviewAdapter()
        mServerRecyclerView.adapter = mServerAdapter

        viewInitialized = true
        loadPreferences()
        attachLiveSyncListeners()

        return v
    }

    override fun onResume() {
        super.onResume()
        // The server list may have been edited in the Server List tab
        mServerAdapter?.notifyDataSetChanged()
        // Reload the fields that are shared with the Connection tab so this tab always
        // reflects the latest profile state (e.g. a password entered on the other tab).
        if (viewInitialized) loadPreferences()
    }

    private fun loadPreferences() {
        mProfileName.setText(mProfile.mName)
        // The Basic tab exposes the private key password — the secret needed for
        // certificate auth with an encrypted client key. Only show it when the
        // selected client key actually requires a password.
        mKeyPassword.setText(mProfile.mKeyPassword)
        mKeyPassLayout.visibility = if (mProfile.requireTLSKeyPassword()) View.VISIBLE else View.GONE

        mConnectRetry.setText(mProfile.mConnectRetry)
        mConnectRetryMaxTime.setText(mProfile.mConnectRetryMaxTime)
        mConnectRetryMax.setSelection(getConnectRetryMaxIndex(mProfile.mConnectRetryMax))

        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(activity)
        val currentDefaultUUID = defaultPrefs.getString("alwaysOnVpn", "")
        mMakeDefaultProfile.isChecked = mProfile.getUUIDString() == currentDefaultUUID
    }

    override fun savePreferences() {
        if (!viewInitialized) return

        // Profile name, password and the default-profile toggle are shared with the
        // Connection tab and are written live (see attachLiveSyncListeners) to avoid a
        // stale tab overwriting them on save. Only the fields unique to this tab are
        // persisted here.
        mProfile.mConnectRetry = mConnectRetry.text.toString()
        mProfile.mConnectRetryMaxTime = mConnectRetryMaxTime.text.toString()
        mProfile.mConnectRetryMax = CRM_VALUES[mConnectRetryMax.selectedItemPosition]
    }

    /**
     * Profile name, private key password and the "make default" toggle are also editable
     * on the Connection tab. Writing them to the profile live (instead of only in
     * savePreferences) keeps both tabs in sync and prevents an out-of-view tab from
     * clobbering the value when the editor is closed.
     */
    private fun attachLiveSyncListeners() {
        mProfileName.doAfterTextChanged { mProfile.mName = it.toString() }
        mKeyPassword.doAfterTextChanged { mProfile.mKeyPassword = it.toString() }
        mMakeDefaultProfile.setOnCheckedChangeListener { _, isChecked -> setDefaultProfile(isChecked) }
    }

    private fun setDefaultProfile(makeDefault: Boolean) {
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(activity)
        val editor = defaultPrefs.edit()
        if (makeDefault) {
            editor.putString("alwaysOnVpn", mProfile.getUUIDString())
        } else if (mProfile.getUUIDString() == defaultPrefs.getString("alwaysOnVpn", "")) {
            editor.putString("alwaysOnVpn", "")
        }
        editor.apply()
    }

    private fun getConnectRetryMaxIndex(value: String?): Int {
        for (i in CRM_VALUES.indices) {
            if (CRM_VALUES[i] == value) return i
        }
        // default in the app is "5" reconnection retries
        return 2
    }

    private fun openServerListTab() {
        (activity as? VPNPreferences)?.showServerListTab()
    }

    private inner class ServerOverviewAdapter : RecyclerView.Adapter<ServerOverviewAdapter.ServerHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerHolder {
            val row = LayoutInflater.from(parent.context)
                .inflate(R.layout.server_row_simple, parent, false)
            return ServerHolder(row)
        }

        override fun onBindViewHolder(holder: ServerHolder, position: Int) {
            val connection = mProfile.mConnections[position]
            var name = connection.mServerName
            if (TextUtils.isEmpty(name)) name = getString(R.string.no_remote_defined)
            holder.mServerName.text = name
            holder.mPort.text = connection.mServerPort
            holder.mServerName.isEnabled = connection.mEnabled
            holder.mPort.isEnabled = connection.mEnabled
            holder.itemView.setOnClickListener { openServerListTab() }
        }

        override fun getItemCount(): Int = mProfile.mConnections?.size ?: 0

        inner class ServerHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val mServerName: TextView = itemView.findViewById(R.id.servername)
            val mPort: TextView = itemView.findViewById(R.id.portnumber)
        }
    }

    companion object {
        /* keep in sync with @array/crm_values */
        private val CRM_VALUES = arrayOf("1", "2", "5", "50", "-1")
    }
}
