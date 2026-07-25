/*
 * Copyright (c) 2026 Maximilian Vogt
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.servers

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.activities.BaseActivity
import com.mavodev.openvpnneo.core.ConfigParser.ConfigParseError
import com.mavodev.openvpnneo.country.CountryInfoRepository
import kotlinx.coroutines.launch

/**
 * Browses free public OpenVPN servers from VPNGate. The user filters by country and sorts
 * by score / speed / uptime; picking a server imports its embedded .ovpn as a new profile.
 *
 * Only reachable when the "free servers" feature is explicitly enabled (and consented to)
 * in Settings — the entry point in [com.mavodev.openvpnneo.fragments.AddProfileBottomSheet]
 * is hidden otherwise.
 */
class ServerBrowserActivity : BaseActivity() {

    private enum class SortMode { SCORE, SPEED, UPTIME }

    private val repository = VpnGateRepository()
    private lateinit var countryInfo: CountryInfoRepository
    private lateinit var adapter: ServerListAdapter

    private lateinit var countrySpinner: Spinner
    private lateinit var recyclerView: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var statusMessage: TextView
    private lateinit var resultCount: TextView

    /** Full unfiltered list from the last successful fetch. */
    private var allServers: List<VpnGateServer> = emptyList()
    /** Distinct country codes present in [allServers], in spinner order (index 0 = "all"). */
    private var countryCodes: List<String?> = listOf(null)
    private var selectedCountry: String? = null
    private var sortMode: SortMode = SortMode.SCORE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server_browser)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.free_servers_title)

        countryInfo = CountryInfoRepository(this)
        countrySpinner = findViewById(R.id.country_spinner)
        recyclerView = findViewById(R.id.server_recycler)
        progress = findViewById(R.id.server_progress)
        statusMessage = findViewById(R.id.server_status_message)
        resultCount = findViewById(R.id.result_count)

        adapter = ServerListAdapter(countryInfo) { server -> confirmAndImport(server) }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        setupCountrySpinner()
        setUpEdgeEdgeInsetsListener(window.decorView.rootView, R.id.server_browser_content)

        loadServers()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.server_browser_menu, menu)
        // Reflect the current sort mode in the dropdown's checkable group.
        val checkedId = when (sortMode) {
            SortMode.SCORE -> R.id.sort_score
            SortMode.SPEED -> R.id.sort_speed
            SortMode.UPTIME -> R.id.sort_uptime
        }
        menu.findItem(checkedId)?.isChecked = true
        return true
    }

    private fun setupCountrySpinner() {
        countrySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedCountry = countryCodes.getOrNull(position)
                applyFilters()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadServers(forceRefresh: Boolean = false) {
        showLoading()
        lifecycleScope.launch {
            when (val result = repository.fetchServers(this@ServerBrowserActivity, forceRefresh)) {
                is VpnGateRepository.Result.Success -> {
                    if (forceRefresh) {
                        android.widget.Toast.makeText(
                            this@ServerBrowserActivity,
                            getString(R.string.free_servers_updated),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    onServersLoaded(result.servers)
                }
                is VpnGateRepository.Result.Failure ->
                    showMessage(getString(R.string.free_servers_error))
            }
        }
    }

    private fun onServersLoaded(servers: List<VpnGateServer>) {
        allServers = servers
        if (servers.isEmpty()) {
            showMessage(getString(R.string.free_servers_empty))
            return
        }
        rebuildCountrySpinner(servers)
        applyFilters()
    }

    private fun rebuildCountrySpinner(servers: List<VpnGateServer>) {
        val distinct = servers.map { it.countryShort }
            .distinct()
            .sortedBy { countryInfo.countryName(it) }

        countryCodes = listOf<String?>(null) + distinct
        val labels = listOf(getString(R.string.free_servers_all_countries)) +
                distinct.map { countryInfo.countryName(it) }

        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        countrySpinner.adapter = spinnerAdapter

        // Keep the previous country selection if it still exists after a refresh.
        val restoreIndex = countryCodes.indexOf(selectedCountry).takeIf { it >= 0 } ?: 0
        countrySpinner.setSelection(restoreIndex)
    }

    private fun applyFilters() {
        val filtered = allServers
            .filter { selectedCountry == null || it.countryShort.equals(selectedCountry, true) }
            .let { list ->
                when (sortMode) {
                    SortMode.SCORE -> list.sortedByDescending { it.score }
                    SortMode.SPEED -> list.sortedByDescending { it.speedBps }
                    SortMode.UPTIME -> list.sortedByDescending { it.uptimeMs }
                }
            }

        adapter.submitList(filtered)
        resultCount.text = resources.getQuantityString(
            R.plurals.free_servers_count, filtered.size, filtered.size
        )
        if (filtered.isEmpty()) {
            showMessage(getString(R.string.free_servers_empty))
        } else {
            showList()
        }
    }

    private fun confirmAndImport(server: VpnGateServer) {
        MaterialAlertDialogBuilder(this)
            .setTitle(countryInfo.countryName(server.countryShort))
            .setMessage(getString(R.string.free_servers_import_confirm, server.hostName))
            .setPositiveButton(R.string.free_servers_import) { _, _ -> importServer(server) }
            .setNeutralButton(R.string.free_servers_test) { _, _ -> testServer(server) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun testServer(server: VpnGateServer) {
        startActivity(
            ServerTestActivity.createIntent(
                this,
                server.decodeConfig(),
                countryInfo.countryName(server.countryShort),
                server.countryShort,
                server.hostName
            )
        )
    }

    private fun importServer(server: VpnGateServer) {
        try {
            val profile = FreeServerImport.buildProfile(
                this,
                server.decodeConfig(),
                countryInfo.countryName(server.countryShort),
                server.hostName,
                FreeServerImport.IMPORT_CONNECT_RETRY_MAX
            )
            FreeServerImport.savePermanent(this, profile, server.countryShort)

            android.widget.Toast.makeText(
                this,
                getString(R.string.free_servers_imported, profile.mName),
                android.widget.Toast.LENGTH_LONG
            ).show()
            finish()
        } catch (e: ConfigParseError) {
            showImportError(e)
        } catch (e: java.io.IOException) {
            showImportError(e)
        }
    }

    private fun showImportError(e: Exception) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.free_servers_import_failed_title)
            .setMessage(getString(R.string.free_servers_import_failed, e.localizedMessage ?: ""))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showLoading() {
        progress.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        statusMessage.visibility = View.GONE
    }

    private fun showList() {
        progress.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        statusMessage.visibility = View.GONE
    }

    private fun showMessage(message: String) {
        progress.visibility = View.GONE
        recyclerView.visibility = View.GONE
        statusMessage.visibility = View.VISIBLE
        statusMessage.text = message
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_refresh -> {
                loadServers(forceRefresh = true)
                true
            }
            R.id.sort_score -> {
                item.isChecked = true
                sortMode = SortMode.SCORE
                applyFilters()
                true
            }
            R.id.sort_speed -> {
                item.isChecked = true
                sortMode = SortMode.SPEED
                applyFilters()
                true
            }
            R.id.sort_uptime -> {
                item.isChecked = true
                sortMode = SortMode.UPTIME
                applyFilters()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
