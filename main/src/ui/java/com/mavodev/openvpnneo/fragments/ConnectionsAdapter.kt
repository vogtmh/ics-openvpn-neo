/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.fragments

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.SeekBar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.recyclerview.widget.RecyclerView
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.VpnProfile
import com.mavodev.openvpnneo.core.Connection

class ConnectionsAdapter internal constructor(
    private val mContext: Context,
    private val mConnectionFragment: Settings_Connections,
    private val mProfile: VpnProfile
) : RecyclerView.Adapter<ConnectionsAdapter.ConnectionsHolder>() {

    private var mConnections: Array<Connection> = mProfile.mConnections

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ConnectionsHolder {
        val li = LayoutInflater.from(mContext)
        val card = if (viewType == TYPE_NORMAL) {
            li.inflate(R.layout.server_card, viewGroup, false)
        } else { // TYPE_FOOTER
            li.inflate(R.layout.server_footer, viewGroup, false)
        }
        return ConnectionsHolder(card, this, viewType)
    }

    override fun onBindViewHolder(cH: ConnectionsHolder, position: Int) {
        if (position == mConnections.size) {
            // Footer
            return
        }
        val connection = mConnections[position]

        cH.mConnection = null

        cH.mPortNumberView.setText(connection.mServerPort)
        cH.mServerNameView.setText(connection.mServerName)
        cH.mPortNumberView.setText(connection.mServerPort)
        cH.mRemoteSwitch.isChecked = connection.mEnabled

        cH.mProxyNameView.setText(connection.mProxyName)
        cH.mProxyPortNumberView.setText(connection.mProxyPort)

        cH.mConnectText.setText(connection.timeout.toString())

        cH.mConnectSlider.progress = connection.timeout

        cH.mProtoGroup.check(if (connection.mUseUdp) R.id.udp_proto else R.id.tcp_proto)

        when (connection.mProxyType) {
            Connection.ProxyType.NONE -> cH.mProxyGroup.check(R.id.proxy_none)
            Connection.ProxyType.HTTP -> cH.mProxyGroup.check(R.id.proxy_http)
            Connection.ProxyType.SOCKS5 -> cH.mProxyGroup.check(R.id.proxy_socks)
            Connection.ProxyType.ORBOT -> cH.mProxyGroup.check(R.id.proxy_orbot)
            else -> {}
        }

        cH.mProxyAuthCb.isChecked = connection.mUseProxyAuth
        cH.mProxyAuthUser.setText(connection.mProxyAuthUser)
        cH.mProxyAuthPassword.setText(connection.mProxyAuthPassword)

        cH.mCustomOptionsLayout.visibility = if (connection.mUseCustomConfig) View.VISIBLE else View.GONE
        cH.mCustomOptionText.setText(connection.mCustomConfiguration)

        cH.mCustomOptionCB.isChecked = connection.mUseCustomConfig
        cH.mConnection = connection

        setVisibilityProxyServer(cH, connection)
    }

    private fun setVisibilityProxyServer(cH: ConnectionsHolder, connection: Connection) {
        val visible = if (connection.mProxyType == Connection.ProxyType.HTTP ||
            connection.mProxyType == Connection.ProxyType.SOCKS5
        ) View.VISIBLE else View.GONE
        val authVisible = if (connection.mProxyType == Connection.ProxyType.HTTP) View.VISIBLE else View.GONE

        cH.mProxyNameView.visibility = visible
        cH.mProxyPortNumberView.visibility = visible
        cH.mProxyPortNameView.visibility = visible
        cH.mProxyNameLabel.visibility = visible

        cH.mProxyAuthLayout.visibility = authVisible
    }

    private fun removeRemote(idx: Int) {
        mConnections = mConnections.filterIndexed { i, _ -> i != idx }.toTypedArray()
    }

    override fun getItemCount(): Int = mConnections.size + 1 //for footer

    override fun getItemViewType(position: Int): Int =
        if (position == mConnections.size) TYPE_FOOTER else TYPE_NORMAL

    fun addRemote() {
        mConnections += Connection()
        notifyItemInserted(mConnections.size - 1)
        displayWarningIfNoneEnabled()
    }

    fun displayWarningIfNoneEnabled() {
        var showWarning = View.VISIBLE
        for (conn in mConnections) {
            if (conn.mEnabled) showWarning = View.GONE
        }
        mConnectionFragment.setWarningVisible(showWarning)
    }

    fun saveProfile() {
        mProfile.mConnections = mConnections
    }

    abstract class OnTextChangedWatcher : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }

    inner class ConnectionsHolder(
        card: View,
        private val mConnectionsAdapter: ConnectionsAdapter,
        viewType: Int
    ) : RecyclerView.ViewHolder(card) {
        // These views only exist in the server_card (TYPE_NORMAL) layout, not in the
        // server_footer layout, so they are only looked up for normal rows. Looking them
        // up unconditionally would NPE when the footer view holder is created.
        lateinit var mServerNameView: EditText
        lateinit var mPortNumberView: EditText
        lateinit var mRemoteSwitch: CompoundButton
        lateinit var mProtoGroup: RadioGroup
        lateinit var mCustomOptionText: EditText
        lateinit var mCustomOptionCB: CompoundButton
        lateinit var mCustomOptionsLayout: View
        private lateinit var mDeleteButton: ImageButton
        lateinit var mConnectText: EditText
        lateinit var mConnectSlider: SeekBar
        lateinit var mProxyGroup: RadioGroup
        lateinit var mProxyNameView: EditText
        lateinit var mProxyPortNumberView: EditText
        lateinit var mProxyPortNameView: View
        lateinit var mProxyNameLabel: View
        lateinit var mProxyAuthLayout: View
        lateinit var mProxyAuthUser: EditText
        lateinit var mProxyAuthPassword: EditText
        lateinit var mProxyAuthCb: CompoundButton

        var mConnection: Connection? = null // Set to null on update

        init {
            if (viewType == TYPE_NORMAL) {
                mServerNameView = card.findViewById(R.id.servername)
                mPortNumberView = card.findViewById(R.id.portnumber)
                mRemoteSwitch = card.findViewById(R.id.remoteSwitch)
                mProtoGroup = card.findViewById(R.id.udptcpradiogroup)
                mCustomOptionText = card.findViewById(R.id.customoptions)
                mCustomOptionCB = card.findViewById(R.id.use_customoptions)
                mCustomOptionsLayout = card.findViewById(R.id.custom_options_layout)
                mDeleteButton = card.findViewById(R.id.remove_connection)
                mConnectText = card.findViewById(R.id.connect_timeout)
                mConnectSlider = card.findViewById(R.id.connect_silder)
                mProxyGroup = card.findViewById(R.id.proxyradiogroup)
                mProxyNameView = card.findViewById(R.id.proxyname)
                mProxyPortNumberView = card.findViewById(R.id.proxyport)
                mProxyPortNameView = card.findViewById(R.id.proxyport_layout)
                mProxyNameLabel = card.findViewById(R.id.proxyname_layout)
                mProxyAuthLayout = card.findViewById(R.id.proxyauthlayout)
                mProxyAuthUser = card.findViewById(R.id.proxyuser)
                mProxyAuthPassword = card.findViewById(R.id.proxypassword)
                mProxyAuthCb = card.findViewById(R.id.enable_proxy_auth)
                addListeners()
            }
        }

        private fun addListeners() {
            mRemoteSwitch.setOnCheckedChangeListener { _, isChecked ->
                mConnection?.let {
                    it.mEnabled = isChecked
                    mConnectionsAdapter.displayWarningIfNoneEnabled()
                }
            }

            mProtoGroup.setOnCheckedChangeListener { _, checkedId ->
                mConnection?.let {
                    if (checkedId == R.id.udp_proto) it.mUseUdp = true
                    else if (checkedId == R.id.tcp_proto) it.mUseUdp = false
                }
            }

            mProxyGroup.setOnCheckedChangeListener { _, checkedId ->
                mConnection?.let {
                    when (checkedId) {
                        R.id.proxy_none -> it.mProxyType = Connection.ProxyType.NONE
                        R.id.proxy_http -> it.mProxyType = Connection.ProxyType.HTTP
                        R.id.proxy_socks -> it.mProxyType = Connection.ProxyType.SOCKS5
                        R.id.proxy_orbot -> it.mProxyType = Connection.ProxyType.ORBOT
                    }
                    setVisibilityProxyServer(this@ConnectionsHolder, it)
                }
            }

            mProxyAuthCb.setOnCheckedChangeListener { _, isChecked ->
                mConnection?.let {
                    it.mUseProxyAuth = isChecked
                    setVisibilityProxyServer(this@ConnectionsHolder, it)
                }
            }

            mCustomOptionText.addTextChangedListener(object : OnTextChangedWatcher() {
                override fun afterTextChanged(s: Editable?) {
                    mConnection?.mCustomConfiguration = s.toString()
                }
            })

            mCustomOptionCB.setOnCheckedChangeListener { _, isChecked ->
                mConnection?.let {
                    it.mUseCustomConfig = isChecked
                    mCustomOptionsLayout.visibility = if (it.mUseCustomConfig) View.VISIBLE else View.GONE
                }
            }

            mServerNameView.addTextChangedListener(object : OnTextChangedWatcher() {
                override fun afterTextChanged(s: Editable?) {
                    mConnection?.mServerName = s.toString()
                }
            })

            mPortNumberView.addTextChangedListener(object : OnTextChangedWatcher() {
                override fun afterTextChanged(s: Editable?) {
                    mConnection?.mServerPort = s.toString()
                }
            })

            mProxyNameView.addTextChangedListener(object : OnTextChangedWatcher() {
                override fun afterTextChanged(s: Editable?) {
                    mConnection?.mProxyName = s.toString()
                }
            })

            mProxyPortNumberView.addTextChangedListener(object : OnTextChangedWatcher() {
                override fun afterTextChanged(s: Editable?) {
                    mConnection?.mProxyPort = s.toString()
                }
            })

            mProxyAuthPassword.addTextChangedListener(object : OnTextChangedWatcher() {
                override fun afterTextChanged(s: Editable?) {
                    mConnection?.mProxyAuthPassword = s.toString()
                }
            })

            mProxyAuthUser.addTextChangedListener(object : OnTextChangedWatcher() {
                override fun afterTextChanged(s: Editable?) {
                    mConnection?.mProxyAuthUser = s.toString()
                }
            })

            mCustomOptionText.addTextChangedListener(object : OnTextChangedWatcher() {
                override fun afterTextChanged(s: Editable?) {
                    mConnection?.mCustomConfiguration = s.toString()
                }
            })

            mConnectSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        mConnection?.let {
                            mConnectText.setText(progress.toString())
                            it.mConnectTimeout = progress
                        }
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })

            mConnectText.addTextChangedListener(object : OnTextChangedWatcher() {
                override fun afterTextChanged(s: Editable?) {
                    mConnection?.let {
                        try {
                            val t = s.toString().toInt()
                            mConnectSlider.progress = t
                            it.mConnectTimeout = t
                        } catch (ignored: Exception) {
                        }
                    }
                }
            })

            mDeleteButton.setOnClickListener {
                val ab = MaterialAlertDialogBuilder(mContext)
                ab.setTitle(R.string.query_delete_remote)
                ab.setPositiveButton(R.string.keep, null)
                ab.setNegativeButton(R.string.delete) { _, _ ->
                    removeRemote(bindingAdapterPosition)
                    notifyItemRemoved(bindingAdapterPosition)
                }
                ab.create().show()
            }
        }
    }

    companion object {
        private const val TYPE_NORMAL = 0
        private const val TYPE_FOOTER = TYPE_NORMAL + 1
    }
}
