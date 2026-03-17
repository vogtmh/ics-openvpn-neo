/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.activities

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.Window
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.core.GlobalPreferences
import com.mavodev.openvpnneo.core.Preferences
import com.mavodev.openvpnneo.core.VpnStatus
import com.mavodev.openvpnneo.core.ConnectionStatus
import com.mavodev.openvpnneo.fragments.*
import com.mavodev.openvpnneo.fragments.ImportRemoteConfig.Companion.newInstance
import com.mavodev.openvpnneo.views.ScreenSlidePagerAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.util.Timer
import java.util.TimerTask

class MainActivity : BaseActivity(), VpnStatus.StateListener, SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var mPager: ViewPager2
    private lateinit var mPagerAdapter: ScreenSlidePagerAdapter
    private lateinit var sharedPreferences: SharedPreferences
    
    // Country display views
    private lateinit var countryBar: LinearLayout
    private lateinit var countryFlag: ImageView
    private lateinit var countryName: TextView
    private lateinit var countryIp: TextView
    
    // Periodic update timer
    private var updateTimer: Timer? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        if (isAndroidTV) {
            requestWindowFeature(android.view.Window.FEATURE_OPTIONS_PANEL)
        }
        // Override BaseActivity edge-to-edge with black status bar
        enableEdgeToEdge(androidx.activity.SystemBarStyle.dark(android.graphics.Color.BLACK))
        super.onCreate(savedInstanceState)
        
        // Additional window flags to ensure status bar color
        window.apply {
            clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = android.graphics.Color.BLACK
            navigationBarColor = android.graphics.Color.BLACK
        }
        
        val view = layoutInflater.inflate(R.layout.main_activity, null)

        // Force status bar color after layout is ready
        view.post {
            window.statusBarColor = android.graphics.Color.BLACK
            window.navigationBarColor = android.graphics.Color.BLACK
        }

        // Instantiate a ViewPager and a PagerAdapter.
        mPager = view.findViewById(R.id.pager)
        val tablayout: TabLayout = view.findViewById(R.id.tab_layout)

        // Initialize country display views
        countryBar = view.findViewById(R.id.country_bar)
        countryFlag = view.findViewById(R.id.country_flag)
        countryName = view.findViewById(R.id.country_name)
        countryIp = view.findViewById(R.id.country_ip)
        
        // Set click listener for manual country update
        countryBar.setOnClickListener {
            Log.d("MainActivity", "Country bar clicked - manual update triggered")
            updateCountryDisplay()
        }
        
        // Initialize SharedPreferences
        sharedPreferences = Preferences.getDefaultSharedPreferences(this)

        mPagerAdapter = ScreenSlidePagerAdapter(supportFragmentManager, lifecycle, this)

        /* Toolbar and slider should have the same elevation */
        disableToolbarElevation()

        val minimalUi = GlobalPreferences.getMinimalUi();
        if (isAndroidTV || minimalUi) {
            mPagerAdapter.addTab(R.string.minimal_ui, MinimalUI::class.java)
        }
        if (!minimalUi) {

            mPagerAdapter.addTab(R.string.vpn_list_title, VPNProfileList::class.java)
            mPagerAdapter.addTab(R.string.graph, GraphFragment::class.java)
            if (SendDumpFragment.getLastestDump(this) != null) {
                mPagerAdapter.addTab(R.string.crashdump, SendDumpFragment::class.java)
            }

        }
        if (isAndroidTV || minimalUi)
            mPagerAdapter.addTab(R.string.openvpn_log, LogFragment::class.java)

        mPager.setAdapter(mPagerAdapter)

        // Debug: Log actual tab positions and titles
        for (i in 0 until mPagerAdapter.itemCount) {
            Log.d("MainActivity", "Tab $i: ${mPagerAdapter.getPageTitle(i)}")
        }

        TabLayoutMediator(tablayout, mPager) { tab, position ->
            tab.text = mPagerAdapter.getPageTitle(position)
            // Update action bar title to match current tab
            supportActionBar?.title = mPagerAdapter.getPageTitle(position)
        }.attach()

        // Set initial position to Profiles (position 0) and update title
        mPager.currentItem = 0
        supportActionBar?.title = mPagerAdapter.getPageTitle(0)

        // Add icon button click listeners for bottom navigation
        val rootLayout = view.findViewById<LinearLayout>(R.id.root_linear_layout)
        val profilesBtn = rootLayout.findViewById<ImageButton>(R.id.tab_profiles)
        val graphBtn = rootLayout.findViewById<ImageButton>(R.id.tab_graph)
        
        Log.d("MainActivity", "Profiles button found: ${profilesBtn != null}")
        Log.d("MainActivity", "Graph button found: ${graphBtn != null}")
        
        profilesBtn?.setOnClickListener {
            Log.d("MainActivity", "PROFILES BUTTON CLICKED - going to position 0")
            mPager.currentItem = 0  // Profiles is position 0
            supportActionBar?.title = mPagerAdapter.getPageTitle(0)
            updateButtonStates(0)
        }
        graphBtn?.setOnClickListener {
            Log.d("MainActivity", "GRAPH BUTTON CLICKED - going to position 1")
            mPager.currentItem = 1  // Graph is position 1
            supportActionBar?.title = mPagerAdapter.getPageTitle(1)
            updateButtonStates(1)
        }
        
        // Add ViewPager change listener to update button states when swiping
        mPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                supportActionBar?.title = mPagerAdapter.getPageTitle(position)
                updateButtonStates(position)
            }
        })

        setUpEdgeEdgeInsetsListener(view, R.id.root_linear_layout)
        setContentView(view)
        
        // Manually add top padding for status bar since we removed fitsSystemWindows
        view.post {
            val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resourceId > 0) {
                val statusBarHeight = resources.getDimensionPixelSize(resourceId) / 2  // Reduce from full height to half
                val rootLayout = findViewById<LinearLayout>(R.id.root_linear_layout)
                
                // Get navigation bar height
                val navBarResourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
                val navBarHeight = if (navBarResourceId > 0) {
                    resources.getDimensionPixelSize(navBarResourceId)
                } else 0
                
                // Set padding: top for status bar (reduced), bottom for navigation bar
                rootLayout.setPadding(0, statusBarHeight, 0, navBarHeight)
            }
        }
        
        // Set initial button states after layout is set
        updateButtonStates(0)
        
        // Start periodic updates
        startPeriodicUpdates()
        
        // Add VPN state listener
        VpnStatus.addStateListener(this)
        
        // Register preference change listener
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }


    private fun disableToolbarElevation() {
        supportActionBar?.elevation = 0f
    }
    
    private fun updateButtonStates(selectedPosition: Int) {
        val rootLayout = findViewById<LinearLayout>(R.id.root_linear_layout)
        val profilesBtn = rootLayout.findViewById<ImageButton>(R.id.tab_profiles)
        val graphBtn = rootLayout.findViewById<ImageButton>(R.id.tab_graph)
        
        profilesBtn?.isSelected = selectedPosition == 0
        graphBtn?.isSelected = selectedPosition == 1
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences?,
        key: String?
    ) {
        // Update country display when setting changes
        if (key == "display_vpn_country") {
            updateCountryDisplay()
        }
    }

    override fun setConnectedVPN(uuid: String?) {
        // Update country display when connection changes
        updateCountryDisplay()
    }

    override fun updateState(
        state: String?,
        logmessage: String?,
        localizedResId: Int,
        level: ConnectionStatus,
        intent: Intent?
    ) {
        // Log VPN state changes for debugging
        Log.d("MainActivity", "VPN State Changed - Level: $level, State: $state")
        
        // Update country display when VPN state changes
        // Handle: connect, disconnect, pause, resume, auth failures, network issues
        if (level == ConnectionStatus.LEVEL_CONNECTED || 
            level == ConnectionStatus.LEVEL_NOTCONNECTED ||
            level == ConnectionStatus.LEVEL_AUTH_FAILED ||
            level == ConnectionStatus.LEVEL_NONETWORK ||
            level == ConnectionStatus.LEVEL_VPNPAUSED ||
            level == ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET ||
            level == ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED ||
            level == ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT) {
            Log.d("MainActivity", "Triggering country display update for level: $level")
            updateCountryDisplay()
        }
    }

    private fun startPeriodicUpdates() {
        // Update every 5 minutes
        updateTimer = Timer()
        updateTimer?.schedule(object : TimerTask() {
            override fun run() {
                updateCountryDisplay()
            }
        }, 5 * 60 * 1000L) // 5 minutes in milliseconds
    }

    private fun stopPeriodicUpdates() {
        updateTimer?.cancel()
        updateTimer = null
    }

    private fun updateCountryDisplay() {
        val displayCountry = sharedPreferences.getBoolean("display_vpn_country", false)
        Log.d("MainActivity", "updateCountryDisplay called - display_vpn_country setting: $displayCountry")
        
        if (displayCountry) {
            countryBar.visibility = View.VISIBLE
            Log.d("MainActivity", "Country bar set to VISIBLE, fetching country info")
            // Add 100ms delay to give VPN routing a moment to establish
            Handler(Looper.getMainLooper()).postDelayed({
                fetchCountryInfo()
            }, 100)
        } else {
            countryBar.visibility = View.GONE
            Log.d("MainActivity", "Country bar set to GONE")
        }
    }
    
    private fun fetchCountryInfo(retryCount: Int = 0) {
        Log.d("MainActivity", "fetchCountryInfo called - starting API request (retry $retryCount)")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://api.country.is/")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.setRequestProperty("User-Agent", "OpenVPN-Neo/1.0")
                connection.connectTimeout = 5000 // 5 seconds timeout
                connection.readTimeout = 5000 // 5 seconds timeout
                
                // Tag the socket for traffic stats to avoid StrictMode warning
                try {
                    android.net.TrafficStats.setThreadStatsTag(0x12345678)
                    val responseCode = connection.responseCode
                    Log.d("MainActivity", "API response code: $responseCode")
                    
                    if (responseCode == 200) {
                        val response = connection.getInputStream()
                        val jsonResponse = response.bufferedReader().use { it.readText() }
                        Log.d("MainActivity", "API response received: $jsonResponse")

                        withContext(Dispatchers.Main) {
                            try {
                                val json = JSONObject(jsonResponse)
                                val ip = json.getString("ip")
                                val country = json.getString("country")

                                Log.d("MainActivity", "Parsed response - IP: $ip, Country: $country")

                                // Update UI with country info
                                countryIp.text = ip
                                countryName.text = getCountryName(country)

                                Log.d("MainActivity", "UI updated - calling loadCountryFlag for: $country")

                                // Load country flag
                                loadCountryFlag(country)

                            } catch (e: Exception) {
                                Log.e("MainActivity", "Error parsing country response", e)
                                showFallbackInfo()
                            }
                        }
                    } else {
                        Log.e("MainActivity", "API returned non-200 response: $responseCode")
                        withContext(Dispatchers.Main) {
                            showFallbackInfo()
                            // Retry after 2 seconds if this was triggered by VPN connection and we haven't retried too many times
                            if (retryCount < 3) {
                                Log.d("MainActivity", "Scheduling retry ${retryCount + 1} in 2 seconds")
                                Handler(Looper.getMainLooper()).postDelayed({
                                    fetchCountryInfo(retryCount + 1)
                                }, 2000)
                            }
                        }
                    }
                } finally {
                    android.net.TrafficStats.clearThreadStatsTag()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error fetching country info", e)
                withContext(Dispatchers.Main) {
                    showFallbackInfo()
                    // Retry after 2 seconds if this was triggered by VPN connection and we haven't retried too many times
                    if (retryCount < 3) {
                        Log.d("MainActivity", "Scheduling retry ${retryCount + 1} in 2 seconds due to exception")
                        Handler(Looper.getMainLooper()).postDelayed({
                            fetchCountryInfo(retryCount + 1)
                        }, 2000)
                    }
                }
            }
        }
    }
    
    private fun showFallbackInfo() {
        Log.w("MainActivity", "showFallbackInfo called - API likely failed")
        // Show fallback information when API fails
        countryName.text = "VPN Connected"
        countryIp.text = "Checking..."
        // Try to load a generic VPN flag or use fallback
        countryFlag.setImageResource(R.mipmap.ic_launcher_foreground)
    }
    
    private fun getCountryName(countryCode: String): String {
        // Simple mapping of country codes to full names
        return when (countryCode.uppercase()) {
            "US" -> "United States"
            "GB" -> "United Kingdom" 
            "DE" -> "Germany"
            "FR" -> "France"
            "IT" -> "Italy"
            "ES" -> "Spain"
            "NL" -> "Netherlands"
            "CA" -> "Canada"
            "AU" -> "Australia"
            "JP" -> "Japan"
            "KR" -> "South Korea"
            "IN" -> "India"
            "BR" -> "Brazil"
            "MX" -> "Mexico"
            "RU" -> "Russia"
            "CN" -> "China"
            "TH" -> "Thailand"
            "SG" -> "Singapore"
            "HK" -> "Hong Kong"
            else -> countryCode.uppercase()
        }
    }
    
    private fun loadCountryFlag(countryCode: String) {
        try {
            // Load flag from resources or use a placeholder
            val flagResourceName = "flag_${countryCode.lowercase()}"
            val resourceId = resources.getIdentifier(flagResourceName, "drawable", packageName)
            
            Log.d("MainActivity", "Loading flag - Country: $countryCode, Resource name: $flagResourceName, Resource ID: $resourceId")
            
            if (resourceId != 0) {
                countryFlag.setImageResource(resourceId)
                // Scale the flag to proper size (24dp x 16dp)
                countryFlag.scaleType = ImageView.ScaleType.FIT_CENTER
                Log.d("MainActivity", "Flag loaded successfully: $flagResourceName")
            } else {
                // Use a generic flag or placeholder if specific flag not found
                Log.w("MainActivity", "Flag not found for: $flagResourceName, using fallback")
                countryFlag.setImageResource(R.mipmap.ic_launcher_foreground)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading country flag for $countryCode", e)
            countryFlag.setImageResource(R.mipmap.ic_launcher_foreground)
        }
    }

    override fun onResume() {
        super.onResume()
        val intent = intent
        if (intent != null) {
            val action = intent.action
            if (Intent.ACTION_VIEW == action) {
                val uri = intent.data
                uri?.let { checkUriForProfileImport(it) }
            }
            val page = intent.getStringExtra("PAGE")
            if ("graph" == page) {
                mPager.currentItem = 1
            }
            setIntent(null)
        }
        
        // Restart periodic updates
        startPeriodicUpdates()
    }

    override fun onPause() {
        super.onPause()
        // Stop periodic updates
        stopPeriodicUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up
        stopPeriodicUpdates()
        VpnStatus.removeStateListener(this)
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun checkUriForProfileImport(uri: Uri) {
        if ("openvpn" == uri.scheme && "import-profile" == uri.host) {
            var realUrl = uri.encodedPath + "?" + uri.encodedQuery
            if (!realUrl.startsWith("/https://")) {
                Toast.makeText(
                    this,
                    "Cannot use openvpn://import-profile/ URL that does not use https://",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            realUrl = realUrl.substring(1)
            startOpenVPNUrlImport(realUrl)
        }
    }

    private fun startOpenVPNUrlImport(url: String) {
        val asImportFrag = newInstance(url)
        asImportFrag.show(supportFragmentManager, "dialog")
    }
}