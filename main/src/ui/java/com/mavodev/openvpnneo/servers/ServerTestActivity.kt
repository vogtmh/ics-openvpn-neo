/*
 * Copyright (c) 2026 Maximilian Vogt
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.servers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mavodev.openvpnneo.LaunchVPN
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.VpnProfile
import com.mavodev.openvpnneo.activities.BaseActivity
import com.mavodev.openvpnneo.core.ConfigParser.ConfigParseError
import com.mavodev.openvpnneo.core.ConnectionStatus
import com.mavodev.openvpnneo.core.IOpenVPNServiceInternal
import com.mavodev.openvpnneo.core.LogItem
import com.mavodev.openvpnneo.core.OpenVPNService
import com.mavodev.openvpnneo.core.ProfileManager
import com.mavodev.openvpnneo.core.VpnStatus

/**
 * Full-screen connection tester for a single free VPNGate server. Builds a *temporary*
 * profile (single attempt, no retries), starts the connection via [LaunchVPN], and streams
 * the live OpenVPN log plus the connection state. If the test succeeds the user can promote
 * the temporary profile to a permanent one.
 */
class ServerTestActivity : BaseActivity(), VpnStatus.LogListener, VpnStatus.StateListener {

    private lateinit var statusView: TextView
    private lateinit var hostView: TextView
    private lateinit var header: LinearLayout
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var importButton: Button
    private lateinit var stopButton: Button

    private var profile: VpnProfile? = null
    private var configText: String = ""
    private var countryName: String = ""
    private var countryCode: String = ""
    private var host: String = ""
    /** True once the connection has entered an active phase, so a later NOTCONNECTED = failure. */
    private var hasStartedConnecting: Boolean = false
    /** True once the test has connected successfully; we then disconnect but keep the success UI. */
    private var testSucceeded: Boolean = false

    private var service: IOpenVPNServiceInternal? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IOpenVPNServiceInternal.Stub.asInterface(binder)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server_test)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.free_servers_test_title)

        statusView = findViewById(R.id.server_test_status)
        hostView = findViewById(R.id.server_test_host)
        header = findViewById(R.id.server_test_header)
        logView = findViewById(R.id.server_test_log)
        logScroll = findViewById(R.id.server_test_log_scroll)
        importButton = findViewById(R.id.server_test_import)
        stopButton = findViewById(R.id.server_test_stop)

        configText = intent.getStringExtra(EXTRA_CONFIG).orEmpty()
        countryName = intent.getStringExtra(EXTRA_COUNTRY).orEmpty()
        countryCode = intent.getStringExtra(EXTRA_COUNTRY_CODE).orEmpty()
        host = intent.getStringExtra(EXTRA_HOST).orEmpty()
        hostView.text = host

        stopButton.setOnClickListener { disconnect() }
        importButton.setOnClickListener { importPermanent() }

        setUpEdgeEdgeInsetsListener(window.decorView.rootView, R.id.server_test_content)

        startTest()
    }

    private fun startTest() {
        val built = try {
            FreeServerImport.buildProfile(
                this, configText, countryName, host, FreeServerImport.TEST_CONNECT_RETRY_MAX
            )
        } catch (e: ConfigParseError) {
            showFatal(e); return
        } catch (e: java.io.IOException) {
            showFatal(e); return
        }
        // Fail fast on unreachable servers ("no route to host") instead of the 120s default,
        // and let a spurious first-attempt AUTH_FAILED reconnect once (bounded by retry-max).
        built.mAuthRetry = VpnProfile.AUTH_RETRY_NOINTERACT
        built.mConnections.forEach { it.mConnectTimeout = FreeServerImport.TEST_CONNECT_TIMEOUT_SECONDS }
        profile = built
        // Register as a temporary profile so it is NOT added to the saved profile list.
        ProfileManager.setTemporaryProfile(this, built)

        VpnStatus.addLogListener(this)
        VpnStatus.addStateListener(this)

        // Bind to the service so we can stop the test connection later.
        bindService(
            Intent(this, OpenVPNService::class.java).setAction(OpenVPNService.START_SERVICE),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )

        statusView.text = getString(R.string.free_servers_test_connecting)
        startActivity(
            Intent(this, LaunchVPN::class.java)
                .putExtra(LaunchVPN.EXTRA_KEY, built.uuidString)
                .putExtra(LaunchVPN.EXTRA_HIDELOG, true)
                .putExtra(OpenVPNService.EXTRA_START_REASON, "free server test")
                .setAction(Intent.ACTION_MAIN)
        )
    }

    private fun disconnect() {
        try {
            service?.stopVPN(false)
        } catch (e: RemoteException) {
            VpnStatus.logException(e)
        }
    }

    /**
     * Save the tested server as a permanent profile. We build a FRESH profile (new UUID)
     * via the normal import path rather than promoting the temporary one: the temporary
     * profile is stored under a special filename and reusing it produces cross-process
     * file/version mismatches when it is later launched as a regular profile.
     */
    private fun importPermanent() {
        val permanent = try {
            FreeServerImport.buildProfile(
                this, configText, countryName, host, FreeServerImport.IMPORT_CONNECT_RETRY_MAX
            )
        } catch (e: ConfigParseError) {
            showFatal(e); return
        } catch (e: java.io.IOException) {
            showFatal(e); return
        }
        FreeServerImport.savePermanent(this, permanent, countryCode)
        Toast.makeText(
            this, getString(R.string.free_servers_imported, permanent.mName), Toast.LENGTH_LONG
        ).show()
        finish()
    }

    // region VpnStatus listeners

    override fun newLog(logItem: LogItem) {
        val line = logItem.getString(this)
        runOnUiThread { appendLog(line) }
    }

    override fun updateState(
        state: String?,
        logmessage: String?,
        localizedResId: Int,
        level: ConnectionStatus?,
        intent: Intent?
    ) {
        runOnUiThread {
            val label = if (localizedResId != 0) getString(localizedResId) else state.orEmpty()
            if (level != null && level != ConnectionStatus.LEVEL_NOTCONNECTED &&
                level != ConnectionStatus.LEVEL_AUTH_FAILED &&
                level != ConnectionStatus.UNKNOWN_LEVEL
            ) {
                hasStartedConnecting = true
            }

            // The test proved the server works: record success and drop the connection so
            // the test does not leave a tunnel up. The self-triggered NOTCONNECTED that
            // follows must NOT be treated as a failure.
            if (level == ConnectionStatus.LEVEL_CONNECTED && !testSucceeded) {
                testSucceeded = true
                disconnect()
            }

            // The initial NOTCONNECTED (before we ever start connecting) is not a failure.
            val settled = level == ConnectionStatus.LEVEL_AUTH_FAILED ||
                    (level == ConnectionStatus.LEVEL_NOTCONNECTED && hasStartedConnecting)

            statusView.text = when {
                testSucceeded -> getString(R.string.free_servers_test_connected)
                level == ConnectionStatus.LEVEL_AUTH_FAILED ->
                    getString(R.string.free_servers_test_failed)
                level == ConnectionStatus.LEVEL_NOTCONNECTED && hasStartedConnecting ->
                    getString(R.string.free_servers_test_not_connected)
                level == ConnectionStatus.LEVEL_NOTCONNECTED ->
                    getString(R.string.free_servers_test_connecting)
                else -> label
            }
            // Once the test succeeded keep Import available even after the self-disconnect.
            importButton.isEnabled = testSucceeded
            stopButton.isEnabled = !testSucceeded && level != ConnectionStatus.LEVEL_NOTCONNECTED
            applyResultColor(
                when {
                    testSucceeded -> true
                    settled -> false
                    else -> null
                }
            )
        }
    }

    /**
     * Tints the header green on success, red on a terminal failure, and clears the tint
     * while the test is still in progress.
     */
    private fun applyResultColor(result: Boolean?) {
        when (result) {
            true -> setHeaderColors(
                R.color.test_result_success_bg, R.color.test_result_success_text
            )
            false -> setHeaderColors(
                R.color.test_result_error_bg, R.color.test_result_error_text
            )
            null -> {
                header.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                statusView.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                hostView.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                hostView.alpha = 0.7f
            }
        }
    }

    private fun setHeaderColors(bgColor: Int, textColor: Int) {
        header.setBackgroundColor(ContextCompat.getColor(this, bgColor))
        val text = ContextCompat.getColor(this, textColor)
        statusView.setTextColor(text)
        hostView.setTextColor(text)
        hostView.alpha = 1f
    }

    override fun setConnectedVPN(uuid: String?) {
        // Not needed for the test screen.
    }

    // endregion

    private fun appendLog(line: String) {
        logView.append(line)
        logView.append("\n")
        logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun showFatal(e: Exception) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.free_servers_import_failed_title)
            .setMessage(getString(R.string.free_servers_import_failed, e.localizedMessage ?: ""))
            .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        VpnStatus.removeLogListener(this)
        VpnStatus.removeStateListener(this)
        try {
            unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
            // Never bound / already unbound; ignore.
        }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_CONFIG = "config"
        private const val EXTRA_COUNTRY = "country"
        private const val EXTRA_COUNTRY_CODE = "country_code"
        private const val EXTRA_HOST = "host"

        fun createIntent(
            context: Context,
            configText: String,
            countryName: String,
            countryCode: String,
            host: String,
        ): Intent = Intent(context, ServerTestActivity::class.java)
            .putExtra(EXTRA_CONFIG, configText)
            .putExtra(EXTRA_COUNTRY, countryName)
            .putExtra(EXTRA_COUNTRY_CODE, countryCode)
            .putExtra(EXTRA_HOST, host)
    }
}
