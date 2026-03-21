/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.activities

import android.animation.ValueAnimator
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.AccelerateDecelerateInterpolator
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
import com.mavodev.openvpnneo.fragments.VPNProfileList
import com.mavodev.openvpnneo.core.VpnStatus
import com.mavodev.openvpnneo.core.ConnectionStatus
import com.mavodev.openvpnneo.core.OpenVPNService
import com.mavodev.openvpnneo.core.OpenVPNManagement
import com.mavodev.openvpnneo.core.TrafficHistory
import com.mavodev.openvpnneo.core.Preferences
import com.mavodev.openvpnneo.core.GlobalPreferences
import com.mavodev.openvpnneo.fragments.*
import com.mavodev.openvpnneo.fragments.ImportRemoteConfig.Companion.newInstance
import com.mavodev.openvpnneo.views.ScreenSlidePagerAdapter
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import java.util.*
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.util.Timer
import java.util.TimerTask

// Extension function to convert dp to pixels
fun Int.dpToPx(): Int {
    return (this * Resources.getSystem().displayMetrics.density).toInt()
}

class MainActivity : BaseActivity(), VpnStatus.StateListener, VpnStatus.ByteCountListener, SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var mPager: ViewPager2
    private lateinit var mPagerAdapter: ScreenSlidePagerAdapter
    private lateinit var sharedPreferences: SharedPreferences
    
    // Country display views
    private lateinit var countryBar: LinearLayout
    private lateinit var countryFlag: ImageView
    private lateinit var countryName: TextView
    private lateinit var countryIp: TextView
    
    // Action bar custom views
    private lateinit var actionBarCountryFlag: ImageView
    private lateinit var actionBarCountryInfo: LinearLayout
    private lateinit var actionBarCountryName: TextView
    private lateinit var actionBarCountryIp: TextView
    private lateinit var actionBarTitle: TextView
    
    // Mini chart views (only initialized when VPN connects)
    private var miniChartContainer: LinearLayout? = null
    private var miniChart: LineChart? = null
    
    // Chart data (only used when chart is initialized)
    private var firstTs = 0L
    private var trafficHistory: TrafficHistory? = null
    private var chartInitialized = false
    private var colourIn = 0
    private var colourOut = 0
    private var colourPoint = 0
    private var textColour = 0
    
    // Periodic country refresh timer
    private var countryRefreshTimer: Timer? = null
    
    // Track last VPN level to detect real state transitions
    private var lastKnownLevel: ConnectionStatus? = null
    
    // Network connectivity callback for WiFi connect/disconnect
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback


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

        // Initialize country display views (moved to action bar)
        // countryBar = view.findViewById(R.id.country_bar)
        // countryFlag = view.findViewById(R.id.country_flag)
        // countryName = view.findViewById(R.id.country_name)
        // countryIp = view.findViewById(R.id.country_ip)
        
        // Set click listener for manual country update (removed - old country bar)
        // countryBar.setOnClickListener {
        //     Log.d("MainActivity", "Country bar clicked - manual update triggered")
        //     updateCountryDisplay()
        // }
        
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
        }.attach()

        // Set initial position to Profiles (position 0)
        mPager.currentItem = 0
        
        // Add ViewPager change listener (no longer needed for action bar updates)
        mPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                // Action bar is now handled by updateActionBarDisplay()
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
                
                // Set padding: only bottom for navigation bar (no top padding)
                rootLayout.setPadding(0, 0, 0, navBarHeight)
            }
        }
        
        // Register network connectivity listener
        registerNetworkCallback()
        
        // Start periodic country refresh (every 5 minutes)
        startPeriodicUpdates()
        
        // Add VPN state listener
        VpnStatus.addStateListener(this)
        
        // Register preference change listener
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        
        // Fetch country info once on launch
        updateCountryDisplay()
    }

    private fun registerNetworkCallback() {
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d("MainActivity", "Network available - scheduling country refresh")
                // Delay to let DHCP/routing settle before querying
                Handler(Looper.getMainLooper()).postDelayed({
                    val displayCountry = sharedPreferences.getBoolean("display_vpn_country", true)
                    if (displayCountry) fetchCountryInfo()
                }, 1500)
            }
            override fun onLost(network: Network) {
                Log.d("MainActivity", "Network lost - refreshing country info")
                Handler(Looper.getMainLooper()).post {
                    val displayCountry = sharedPreferences.getBoolean("display_vpn_country", true)
                    if (displayCountry) fetchCountryInfo()
                }
            }
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
        val cm = getSystemService(ConnectivityManager::class.java)
        cm.registerNetworkCallback(request, networkCallback)
    }


    private fun disableToolbarElevation() {
        supportActionBar?.elevation = 0f
        
        // Set up custom action bar
        supportActionBar?.setDisplayShowCustomEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        
        // Inflate custom action bar layout
        val customView = layoutInflater.inflate(R.layout.action_bar_custom, null)
        actionBarCountryFlag = customView.findViewById(R.id.action_bar_country_flag)
        actionBarCountryInfo = customView.findViewById(R.id.action_bar_country_info)
        actionBarCountryName = customView.findViewById(R.id.action_bar_country_name)
        actionBarCountryIp = customView.findViewById(R.id.action_bar_country_ip)
        actionBarTitle = customView.findViewById(R.id.action_bar_title)
        
        supportActionBar?.customView = customView
        
        // Initialize action bar display
        updateActionBarDisplay()
    }
    
    private fun initializeMiniChart() {
        Log.d("MainActivity", "initializeMiniChart called")
        
        // Find chart views (they exist in layout but weren't initialized before)
        val rootLayout = findViewById<LinearLayout>(R.id.root_linear_layout)
        miniChartContainer = rootLayout.findViewById(R.id.mini_chart_container)
        miniChart = rootLayout.findViewById(R.id.mini_chart)
        
        // Set click listener to open full GraphActivity
        miniChartContainer?.setOnClickListener {
            Log.d("MainActivity", "Mini chart clicked - opening GraphActivity")
            val intent = Intent(this, GraphActivity::class.java)
            startActivity(intent)
        }
        
        // Set up chart colors
        colourIn = resources.getColor(R.color.dataIn)
        colourOut = resources.getColor(R.color.dataOut)
        colourPoint = resources.getColor(android.R.color.white) // White points for visibility
        
        // Force white text for dark background
        textColour = resources.getColor(android.R.color.white)
        
        // Configure chart appearance - less aggressive scaling
        miniChart?.description?.isEnabled = false
        miniChart?.setDrawGridBackground(false)
        miniChart?.legend?.textColor = textColour
        miniChart?.setNoDataTextColor(resources.getColor(R.color.accent))
        
        // Less aggressive axis configuration
        val xAxis = miniChart?.xAxis
        xAxis?.position = XAxis.XAxisPosition.BOTTOM
        xAxis?.setDrawGridLines(false)
        xAxis?.setDrawAxisLine(true)
        xAxis?.textColor = textColour
        xAxis?.labelCount = 3
        xAxis?.setGranularity(1f)  // Less frequent labels
        xAxis?.setAvoidFirstLastClipping(true)  // Prevent cutoff at edges
        
        val yAxis = miniChart?.axisLeft
        yAxis?.labelCount = 3
        yAxis?.setLabelCount(3, false)
        yAxis?.textColor = textColour
        miniChart?.axisRight?.isEnabled = false
        
        yAxis?.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                if (value < 2.1f)
                    return "< 100\u2009bit/s"
                val scaledValue = Math.pow(10.0, value.toDouble()) / 8.0
                return OpenVPNService.humanReadableByteCount(scaledValue.toLong(), true, resources)
            }
        }
        
        // Set initial no data text to "Initializing.." for mini chart
        miniChart?.setNoDataText(getString(R.string.initializing))
        
        // Set initial empty data
        miniChart?.data = LineData()
        miniChart?.invalidate()
        
        Log.d("MainActivity", "Mini chart initialization complete")
    }
    
    private fun cleanupMiniChart() {
        Log.d("MainActivity", "cleanupMiniChart called")
        
        // Clear chart data
        miniChart?.data = null
        miniChart?.invalidate()
        
        // Reset initialization flag
        chartInitialized = false
        
        // Reset traffic history so the next session starts with a fresh baseline.
        // Without this, session-2 datapoints get plotted relative to session-1's
        // timestamps, causing a massively skewed X-axis.
        VpnStatus.setTrafficHistory(TrafficHistory())
        firstTs = 0L
        
        Log.d("MainActivity", "Mini chart cleanup complete")
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
        // Country display is handled by updateState() on LEVEL_CONNECTED/LEVEL_NOTCONNECTED
    }
    
    private fun updateMiniChart() {
        if (!chartInitialized || miniChart == null) {
            Log.d("MainActivity", "updateMiniChart called but chart not initialized")
            return
        }
        
        Log.d("MainActivity", "updateMiniChart called")
        
        val list = VpnStatus.trafficHistory.seconds
        Log.d("MainActivity", "Traffic history list size: ${list.size}")
        
        val workingList = if (list.size == 0) {
            Log.d("MainActivity", "Using dummy list")
            TrafficHistory.getDummyList()
        } else {
            Log.d("MainActivity", "Using real traffic data")
            list
        }
        
        Log.d("MainActivity", "Working list size: ${workingList.size}")
        
        val dataIn = LinkedList<Entry>()
        val dataOut = LinkedList<Entry>()
        
        val interval = OpenVPNManagement.mBytecountInterval * 1000L
        val now = System.currentTimeMillis()
        
        var firstTimestamp = 0L
        var lastBytecountOut = 0L
        var lastBytecountIn = 0L
        
        // Initialize first timestamp from the first item
        if (workingList.isNotEmpty()) {
            val firstItem = workingList.first
            firstTimestamp = firstItem.timestamp
            lastBytecountIn = firstItem.`in`
            lastBytecountOut = firstItem.`out`
            Log.d("MainActivity", "First timestamp: $firstTimestamp, in: $lastBytecountIn, out: $lastBytecountOut")
        }
        
        for (tdp in workingList) {
            val t = (tdp.timestamp - firstTimestamp) / 100f
            
            val `in` = (tdp.`in` - lastBytecountIn) / (interval / 1000f).toFloat()
            val out = (tdp.`out` - lastBytecountOut) / (interval / 1000f).toFloat()
            
            lastBytecountIn = tdp.`in`
            lastBytecountOut = tdp.`out`
            
            val processedIn = max(2f, Math.log10(`in`.toDouble() * 8.0).toFloat())
            val processedOut = max(2f, Math.log10(out.toDouble() * 8.0).toFloat())
            
            dataIn.add(Entry(t, processedIn))
            dataOut.add(Entry(t, processedOut))
        }
        
        Log.d("MainActivity", "Data points created: in=${dataIn.size}, out=${dataOut.size}")
        
        val dataSets = ArrayList<ILineDataSet>()
        
        val indata = LineDataSet(dataIn, getString(R.string.data_in))
        val outdata = LineDataSet(dataOut, getString(R.string.data_out))
        
        setLineDataAttributes(indata, colourIn)
        setLineDataAttributes(outdata, colourOut)
        
        dataSets.add(indata)
        dataSets.add(outdata)
        
        val lineData = LineData(dataSets)
        
        if (lineData.getDataSetByIndex(0).entryCount < 3) {
            Log.d("MainActivity", "Not enough data points, setting null data")
            miniChart?.data = null
        } else {
            Log.d("MainActivity", "Setting chart data with ${lineData.getDataSetByIndex(0).entryCount} points")
            miniChart?.data = lineData
            
            val ymax = lineData.yMax
            val yAxis = miniChart?.axisLeft
            yAxis?.axisMinimum = 2f
            yAxis?.axisMaximum = Math.ceil(ymax.toDouble()).toFloat()
            yAxis?.labelCount = Math.ceil(ymax.toDouble() - 2.0).toInt()
        }
        
        miniChart?.setNoDataText(getString(R.string.initializing))
        miniChart?.invalidate()
        
        Log.d("MainActivity", "Mini chart updated and invalidated - dataPoints: ${lineData.getDataSetByIndex(0).entryCount}")
    }
    
    private fun setLineDataAttributes(dataSet: LineDataSet, colour: Int) {
        dataSet.lineWidth = 3f
        dataSet.setDrawCircles(false)
        dataSet.setDrawFilled(true)
        dataSet.fillAlpha = 30  // Slightly more transparent fill
        dataSet.fillColor = colour
        dataSet.color = colour
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER  // Smooth curved lines
        dataSet.setDrawValues(false)
        dataSet.valueTextColor = textColour
    }
    
    override fun updateByteCount(inBytes: Long, outBytes: Long, diffIn: Long, diffOut: Long) {
        if (firstTs == 0L)
            firstTs = System.currentTimeMillis() / 100L
        
        runOnUiThread {
            updateMiniChart()
        }
    }
    
    override fun updateState(
        state: String?,
        logmessage: String?,
        localizedResId: Int,
        level: ConnectionStatus,
        intent: Intent?
    ) {
        Log.d("MainActivity", "VPN State Changed - Level: $level, State: $state")
        
        // Only act on stable terminal states, and only when the level actually changed
        val levelChanged = level != lastKnownLevel
        lastKnownLevel = level
        
        // Fetch country info only on real connect/disconnect transitions
        if (levelChanged && (level == ConnectionStatus.LEVEL_CONNECTED || level == ConnectionStatus.LEVEL_NOTCONNECTED)) {
            Log.d("MainActivity", "Stable VPN state reached ($level) - refreshing country info")
            runOnUiThread {
                val displayCountry = sharedPreferences.getBoolean("display_vpn_country", true)
                if (displayCountry) {
                    // Old country bar removed - no visibility control needed
                    if (level == ConnectionStatus.LEVEL_CONNECTED) {
                        // Delay the fetch so VPN routing has time to fully establish.
                        // Without this, the request goes out on the old route and returns
                        // the pre-VPN IP/country.
                        Log.d("MainActivity", "Delaying country fetch 2s to let VPN routing settle")
                        Handler(Looper.getMainLooper()).postDelayed({ fetchCountryInfo(ConnectionStatus.LEVEL_CONNECTED) }, 2000)
                    } else {
                        // Disconnect is immediate — no routing change to wait for
                        fetchCountryInfo()
                    }
                } else {
                    // Old country bar removed - no visibility control needed
                    Log.d("MainActivity", "Country bar set to GONE")
                }
            }
        }
        
        // Handle mini chart visibility (always runs, not just on change)
        runOnUiThread {
            when (level) {
                ConnectionStatus.LEVEL_CONNECTED -> {
                    if (!chartInitialized) {
                        Log.d("MainActivity", "Scheduling mini chart initialization after VPN connection")
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (!chartInitialized) {
                                Log.d("MainActivity", "Initializing mini chart after delay")
                                initializeMiniChart()
                                chartInitialized = true
                                animateMiniChartShow()
                                VpnStatus.addByteCountListener(this)
                                // Don't call updateMiniChart() here - let it be called when data arrives
                                Log.d("MainActivity", "Mini chart shown and listener registered")
                            }
                        }, 2000)
                    } else {
                        animateMiniChartShow()
                        VpnStatus.addByteCountListener(this)
                        // Don't call updateMiniChart() here either - let it be called when data arrives
                        Log.d("MainActivity", "Mini chart shown (already initialized)")
                    }
                }
                ConnectionStatus.LEVEL_NOTCONNECTED -> {
                    animateMiniChartHide()
                    VpnStatus.removeByteCountListener(this)
                    Log.d("MainActivity", "Mini chart hidden and listener unregistered")
                    cleanupMiniChart()
                }
                ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET,
                ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED,
                ConnectionStatus.LEVEL_AUTH_FAILED,
                ConnectionStatus.LEVEL_NONETWORK -> {
                    // Don't hide mini chart or stop animations during connection attempts
                    // Only hide when actually disconnected or failed
                    if (level == ConnectionStatus.LEVEL_AUTH_FAILED || level == ConnectionStatus.LEVEL_NONETWORK) {
                        animateMiniChartHide()
                    }
                }
                ConnectionStatus.LEVEL_VPNPAUSED,
                ConnectionStatus.LEVEL_START,
                ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT,
                ConnectionStatus.UNKNOWN_LEVEL -> {
                    // For other states, don't do anything with the mini chart
                }
            }
        }
    }

    private fun startPeriodicUpdates() {
        countryRefreshTimer?.cancel()
        countryRefreshTimer = Timer()
        val periodMs = 5 * 60 * 1000L // 5 minutes
        countryRefreshTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                Log.d("MainActivity", "Periodic country refresh triggered")
                // Only fetch if the feature is enabled; no need to touch visibility
                val displayCountry = sharedPreferences.getBoolean("display_vpn_country", false)
                if (displayCountry) {
                    runOnUiThread { fetchCountryInfo() }
                }
            }
        }, periodMs, periodMs)
    }

    private fun stopPeriodicUpdates() {
        countryRefreshTimer?.cancel()
        countryRefreshTimer = null
    }

    private fun updateCountryDisplay() {
        val displayCountry = sharedPreferences.getBoolean("display_vpn_country", true)
        Log.d("MainActivity", "updateCountryDisplay called - display_vpn_country setting: $displayCountry")
        
        // Old country bar is removed - only update action bar now
        // if (displayCountry) {
        //     countryBar.visibility = View.VISIBLE
        //     Log.d("MainActivity", "Country bar set to VISIBLE, fetching country info")
        //     fetchCountryInfo()
        // } else {
        //     countryBar.visibility = View.GONE
        //     Log.d("MainActivity", "Country bar set to GONE")
        // }
        
        // Update action bar display
        updateActionBarDisplay()
        
        // Fetch country info if display is enabled
        if (displayCountry) {
            fetchCountryInfo()
        }
    }
    
    private fun updateActionBarDisplay() {
        val displayCountry = sharedPreferences.getBoolean("display_vpn_country", true)
        Log.d("MainActivity", "updateActionBarDisplay called - display_vpn_country setting: $displayCountry")
        
        if (displayCountry && actionBarCountryName.text.isNotEmpty()) {
            // Show country info in action bar
            actionBarCountryFlag.visibility = View.VISIBLE
            actionBarCountryInfo.visibility = View.VISIBLE
            actionBarTitle.visibility = View.GONE
        } else {
            // Show app name in action bar
            actionBarCountryFlag.visibility = View.GONE
            actionBarCountryInfo.visibility = View.GONE
            actionBarTitle.visibility = View.VISIBLE
            actionBarTitle.text = getString(R.string.app)
        }
    }
    
    private fun fetchCountryInfo(connectionLevel: ConnectionStatus? = null, retryCount: Int = 0) {
        Log.d("MainActivity", "fetchCountryInfo called - starting API request (retry $retryCount, connectionLevel: $connectionLevel)")
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

                                // Update UI with country info (action bar only)
                                // countryIp.text = ip
                                // countryName.text = getCountryName(country)
                                
                                // Update action bar with country info
                                actionBarCountryIp.text = ip
                                actionBarCountryName.text = getCountryName(country)
                                
                                // Update action bar visibility
                                updateActionBarDisplay()

                                Log.d("MainActivity", "UI updated - calling loadCountryFlag for: $country")

                                // Load country flag
                                loadCountryFlag(country)
                                
                                // Save country for current profile (only when VPN is actually connected)
                                val currentProfileUUID = VpnStatus.getLastConnectedVPNProfile()
                                if (currentProfileUUID != null && connectionLevel == ConnectionStatus.LEVEL_CONNECTED) {
                                    Log.d("MainActivity", "VPN is connected, saving country $country for profile $currentProfileUUID")
                                    saveProfileCountry(currentProfileUUID, country)
                                    
                                    // Notify VPNProfileList to refresh flags
                                    refreshVPNProfileList()
                                } else {
                                    Log.d("MainActivity", "Not saving country - VPN not connected (connectionLevel: $connectionLevel)")
                                }

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
                                    fetchCountryInfo(connectionLevel, retryCount + 1)
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
                            fetchCountryInfo(connectionLevel, retryCount + 1)
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
                // countryFlag.setImageResource(resourceId)
                actionBarCountryFlag.setImageResource(resourceId)
                // Scale the flag to proper size (24dp x 16dp)
                // countryFlag.scaleType = ImageView.ScaleType.FIT_CENTER
                actionBarCountryFlag.scaleType = ImageView.ScaleType.FIT_CENTER
                Log.d("MainActivity", "Flag loaded successfully: $flagResourceName")
            } else {
                // Use a generic flag or placeholder if specific flag not found
                Log.w("MainActivity", "Flag not found for: $flagResourceName, using fallback")
                // countryFlag.setImageResource(R.mipmap.ic_launcher_foreground)
                actionBarCountryFlag.setImageResource(R.mipmap.ic_launcher_foreground)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading country flag for $countryCode", e)
            // countryFlag.setImageResource(R.mipmap.ic_launcher_foreground)
            actionBarCountryFlag.setImageResource(R.mipmap.ic_launcher_foreground)
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

    // Animation methods for smooth mini chart transitions
    private fun animateMiniChartShow() {
        miniChartContainer?.let { container ->
            // Set initial state
            container.visibility = View.VISIBLE
            container.layoutParams?.height = 0
            container.requestLayout()
            
            // Animate to full height
            val targetHeight = 160.dpToPx()
            val animator = ValueAnimator.ofInt(0, targetHeight)
            animator.duration = 300 // 300ms animation
            animator.interpolator = AccelerateDecelerateInterpolator()
            
            animator.addUpdateListener { animation ->
                val animatedValue = animation.animatedValue as Int
                container.layoutParams?.height = animatedValue
                container.requestLayout()
            }
            
            animator.start()
            
            // Stop VPN profile list animations when connection is established
            stopVPNProfileAnimations()
            
            Log.d("MainActivity", "Animating mini chart show")
        }
    }
    
    private fun animateMiniChartHide() {
        miniChartContainer?.let { container ->
            val currentHeight = container.height
            
            val animator = ValueAnimator.ofInt(currentHeight, 0)
            animator.duration = 300 // 300ms animation
            animator.interpolator = AccelerateDecelerateInterpolator()
            
            animator.addUpdateListener { animation ->
                val animatedValue = animation.animatedValue as Int
                container.layoutParams?.height = animatedValue
                container.requestLayout()
            }
            
            animator.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    container.visibility = View.GONE
                }
            })
            
            animator.start()
            
            // Stop VPN profile list animations when connection fails/disconnects
            stopVPNProfileAnimations()
            
            Log.d("MainActivity", "Animating mini chart hide")
        }
    }

    // Stop VPN profile list animations when connection state changes
    private fun stopVPNProfileAnimations() {
        Log.d("MainActivity", "stopVPNProfileAnimations() called")
        // Find the VPNProfileList fragment and stop animations
        supportFragmentManager.fragments.forEach { fragment ->
            if (fragment is VPNProfileList) {
                Log.d("MainActivity", "Found VPNProfileList fragment, stopping animations")
                fragment.stopAllAnimations()
                return@forEach
            }
        }
        Log.d("MainActivity", "stopVPNProfileAnimations() completed - no VPNProfileList fragment found")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up
        stopPeriodicUpdates()
        VpnStatus.removeStateListener(this)
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        val cm = getSystemService(ConnectivityManager::class.java)
        cm.unregisterNetworkCallback(networkCallback)
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
    
    private fun saveProfileCountry(profileUUID: String, countryCode: String) {
        val prefs = getSharedPreferences("profile_countries", Context.MODE_PRIVATE)
        prefs.edit().putString(profileUUID, countryCode).apply()
    }
    
    private fun refreshVPNProfileList() {
        // Find the VPNProfileList fragment and refresh it
        supportFragmentManager.fragments.forEach { fragment ->
            if (fragment is VPNProfileList) {
                Log.d("MainActivity", "Refreshing VPNProfileList to update flags")
                fragment.refreshFlags()
                return@forEach
            }
        }
    }
}