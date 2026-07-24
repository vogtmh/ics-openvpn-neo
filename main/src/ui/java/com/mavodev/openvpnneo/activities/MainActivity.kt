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
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.content.ContextCompat
import android.widget.Toast
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.fragments.VPNProfileList
import com.mavodev.openvpnneo.core.VpnStatus
import com.mavodev.openvpnneo.core.ConnectionStatus
import com.mavodev.openvpnneo.core.OpenVPNService
import com.mavodev.openvpnneo.core.OpenVPNManagement
import com.mavodev.openvpnneo.core.TrafficHistory
import com.mavodev.openvpnneo.core.Preferences
import com.mavodev.openvpnneo.core.GlobalPreferences
import com.mavodev.openvpnneo.country.CountryInfoRepository
import com.mavodev.openvpnneo.fragments.*
import com.mavodev.openvpnneo.fragments.ImportRemoteConfig.Companion.newInstance
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
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import java.util.Timer
import java.util.TimerTask

// Extension function to convert dp to pixels
fun Int.dpToPx(): Int {
    return (this * Resources.getSystem().displayMetrics.density).toInt()
}

class MainActivity : BaseActivity(), VpnStatus.StateListener, VpnStatus.ByteCountListener, SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var countryInfoRepository: CountryInfoRepository
    
    // Country display views - only action bar variables used
    
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
    private var byteCountListenerRegistered = false
    private var colourIn = 0
    private var colourOut = 0
    private var colourPoint = 0
    private var textColour = 0
    
    // Periodic country refresh timer
    private var countryRefreshTimer: Timer? = null
    
    // Track last VPN level to detect real state transitions
    private var lastKnownLevel: ConnectionStatus? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val view = layoutInflater.inflate(R.layout.main_activity, null)

        // Initialize SharedPreferences
        sharedPreferences = Preferences.getDefaultSharedPreferences(this)
        countryInfoRepository = CountryInfoRepository(this)

        /* Toolbar and slider should have the same elevation */
        disableToolbarElevation()

        // Place VPNProfileList directly into the fragment container (no pager/tabs needed).
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, VPNProfileList())
                .commit()
        }

        setUpEdgeEdgeInsetsListener(view, R.id.root_linear_layout)
        setContentView(view)

        // Register network connectivity listener
        registerNetworkMonitoring()
        
        // Start periodic country refresh (every 5 minutes)
        startPeriodicUpdates()
        
        // Add VPN state listener
        VpnStatus.addStateListener(this)
        
        // Register preference change listener
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        
        // Fetch country info once on launch
        updateCountryDisplay()

        // On first launch, ask whether to enable the country API
        showCountryApiConsentDialogIfNeeded()
    }

    private fun showCountryApiConsentDialogIfNeeded() {
        // Skip if already asked, or if the user has explicitly configured the setting before
        if (sharedPreferences.getBoolean("country_api_asked", false) ||
            sharedPreferences.contains("display_vpn_country")) {
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.country_api_dialog_title))
            .setMessage(getString(R.string.country_api_dialog_message))
            .setPositiveButton(getString(R.string.country_api_dialog_enable)) { _, _ ->
                sharedPreferences.edit()
                    .putBoolean("display_vpn_country", true)
                    .putBoolean("country_api_asked", true)
                    .apply()
                updateCountryDisplay()
            }
            .setNegativeButton(getString(R.string.country_api_dialog_decline)) { _, _ ->
                sharedPreferences.edit()
                    .putBoolean("country_api_asked", true)
                    .apply()
            }
            .setCancelable(false)
            .show()
    }

    private fun registerNetworkMonitoring() {
        countryInfoRepository.startNetworkMonitoring {
            val displayCountry = sharedPreferences.getBoolean("display_vpn_country", false)
            if (displayCountry) fetchCountryInfo()
        }
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

        // Tapping the flag or country info manually refreshes the country API
        val manualRefresh = android.view.View.OnClickListener {
            val displayCountry = Preferences.getDefaultSharedPreferences(this)
                .getBoolean("display_vpn_country", false)
            if (displayCountry) fetchCountryInfo()
        }
        actionBarCountryFlag.setOnClickListener(manualRefresh)
        actionBarCountryInfo.setOnClickListener(manualRefresh)

        // Change-sorting button on the right side of the header
        customView.findViewById<ImageButton>(R.id.action_bar_change_sorting).setOnClickListener {
            (supportFragmentManager.findFragmentById(R.id.fragment_container) as? VPNProfileList)
                ?.changeSorting()
        }

        supportActionBar?.setCustomView(
            customView,
            androidx.appcompat.app.ActionBar.LayoutParams(
                androidx.appcompat.app.ActionBar.LayoutParams.MATCH_PARENT,
                androidx.appcompat.app.ActionBar.LayoutParams.WRAP_CONTENT
            )
        )
        
        // Initialize action bar display
        updateActionBarDisplay()
    }
    
    private fun initializeMiniChart() {
        // Find chart views (they exist in layout but weren't initialized before)
        val rootLayout = findViewById<LinearLayout>(R.id.root_linear_layout)
        miniChartContainer = rootLayout.findViewById(R.id.mini_chart_container)
        miniChart = rootLayout.findViewById(R.id.mini_chart)
        
        // Set click listener to open full GraphActivity
        miniChartContainer?.setOnClickListener {
            val intent = Intent(this, GraphActivity::class.java)
            startActivity(intent)
        }
        
        // Set up chart colors
        colourIn = ContextCompat.getColor(this, R.color.dataIn)
        colourOut = ContextCompat.getColor(this, R.color.dataOut)
        colourPoint = ContextCompat.getColor(this, android.R.color.white) // White points for visibility
        
        // Force white text for dark background
        textColour = ContextCompat.getColor(this, android.R.color.white)
        
        // Configure chart appearance - less aggressive scaling
        miniChart?.description?.isEnabled = false
        miniChart?.setDrawGridBackground(false)
        miniChart?.legend?.textColor = textColour
        miniChart?.setNoDataTextColor(ContextCompat.getColor(this, R.color.accent))
        
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
    }
    
    private fun cleanupMiniChart() {
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
    }

    /**
     * Resets the traffic baseline for a new session while keeping the (visible) chart and
     * its byte-count listener in place. Used when a new connection starts (e.g. the user
     * switches profiles while still connected) so the new session is plotted from zero
     * instead of against the previous session's baseline, which would otherwise leave the
     * still-visible chart empty.
     */
    private fun resetMiniChartSession() {
        VpnStatus.setTrafficHistory(TrafficHistory())
        firstTs = 0L
        miniChart?.data = LineData()
        miniChart?.setNoDataText(getString(R.string.initializing))
        miniChart?.invalidate()
    }

    private fun registerByteCountListener() {
        if (!byteCountListenerRegistered) {
            VpnStatus.addByteCountListener(this)
            byteCountListenerRegistered = true
        }
    }

    private fun unregisterByteCountListener() {
        if (byteCountListenerRegistered) {
            VpnStatus.removeByteCountListener(this)
            byteCountListenerRegistered = false
        }
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
            return
        }
        
        val list = VpnStatus.trafficHistory.seconds
        
        val workingList = if (list.size == 0) {
            TrafficHistory.getDummyList()
        } else {
            list
        }
        
        val dataIn = LinkedList<Entry>()
        val dataOut = LinkedList<Entry>()
        
        val interval = OpenVPNManagement.mBytecountInterval * 1000L
        val now = System.currentTimeMillis()
        
        var firstTimestamp = 0L
        var lastBytecountOut = 0L
        var lastBytecountIn = 0L
        
        // Initialize first timestamp from the first item
        if (workingList.isNotEmpty()) {
            val firstItem = workingList[0]
            firstTimestamp = firstItem.timestamp
            lastBytecountIn = firstItem.`in`
            lastBytecountOut = firstItem.`out`
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
        
        val dataSets = ArrayList<ILineDataSet>()
        
        val indata = LineDataSet(dataIn, getString(R.string.data_in))
        val outdata = LineDataSet(dataOut, getString(R.string.data_out))
        
        setLineDataAttributes(indata, colourIn)
        setLineDataAttributes(outdata, colourOut)
        
        dataSets.add(indata)
        dataSets.add(outdata)
        
        val lineData = LineData(dataSets)
        
        if (lineData.getDataSetByIndex(0).entryCount < 3) {
            miniChart?.data = null
        } else {
            miniChart?.data = lineData
            
            val ymax = lineData.yMax
            val yAxis = miniChart?.axisLeft
            yAxis?.axisMinimum = 2f
            // Add headroom above the peak so the smoothed (bezier) curve is not clipped at the top
            yAxis?.axisMaximum = Math.ceil((ymax + 0.5f).toDouble()).toFloat()
            yAxis?.labelCount = Math.max(2, Math.ceil(ymax.toDouble() - 2.0).toInt())
        }
        
        miniChart?.setNoDataText(getString(R.string.initializing))
        miniChart?.invalidate()
    }
    
    private fun setLineDataAttributes(dataSet: LineDataSet, colour: Int) {
        dataSet.lineWidth = 3f
        dataSet.setDrawCircles(false)
        dataSet.setDrawFilled(true)
        dataSet.fillAlpha = 30  // Slightly more transparent fill
        dataSet.fillColor = colour
        dataSet.color = colour
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER  // Smooth curved lines
        dataSet.cubicIntensity = 0.15f  // Reduce overshoot so peaks are not clipped
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
        // Only act on stable terminal states, and only when the level actually changed
        val levelChanged = level != lastKnownLevel
        lastKnownLevel = level
        
        // Fetch country info only on real connect/disconnect transitions
        if (levelChanged && (level == ConnectionStatus.LEVEL_CONNECTED || level == ConnectionStatus.LEVEL_NOTCONNECTED)) {
            runOnUiThread {
                val displayCountry = sharedPreferences.getBoolean("display_vpn_country", false)
                if (displayCountry) {
                    // Old country bar removed - no visibility control needed
                    if (level == ConnectionStatus.LEVEL_CONNECTED) {
                        // Delay the fetch so VPN routing has time to fully establish.
                        // Without this, the request goes out on the old route and returns
                        // the pre-VPN IP/country.
                        Handler(Looper.getMainLooper()).postDelayed({
                            // Re-check: the VPN may have been disconnected during the delay.
                            // Without this, a quick connect/disconnect would fetch the
                            // pre-VPN country and save it to the profile's flag.
                            if (lastKnownLevel == ConnectionStatus.LEVEL_CONNECTED) {
                                fetchCountryInfo(ConnectionStatus.LEVEL_CONNECTED)
                            } else {
                                fetchCountryInfo()
                            }
                        }, 2000)
                    } else {
                        // Disconnect is immediate — no routing change to wait for
                        fetchCountryInfo()
                    }
                }
            }
        }
        
        // Handle mini chart visibility (always runs, not just on change)
        runOnUiThread {
            when (level) {
                ConnectionStatus.LEVEL_CONNECTED -> {
                    if (!chartInitialized) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (!chartInitialized) {
                                initializeMiniChart()
                                chartInitialized = true
                                animateMiniChartShow()
                                registerByteCountListener()
                                // Don't call updateMiniChart() here - let it be called when data arrives
                            }
                        }, 2000)
                    } else {
                        animateMiniChartShow()
                        registerByteCountListener()
                        // Don't call updateMiniChart() here either - let it be called when data arrives
                    }
                }
                ConnectionStatus.LEVEL_NOTCONNECTED -> {
                    animateMiniChartHide()
                    unregisterByteCountListener()
                    cleanupMiniChart()
                }
                ConnectionStatus.LEVEL_START -> {
                    // A new connection is starting (e.g. the user switched profiles while
                    // still connected). Reset the traffic baseline so the new session is
                    // plotted from zero; otherwise the still-visible chart is drawn against
                    // the previous session's baseline and appears empty. A mid-session
                    // network reconnect emits CONNECTING/RECONNECTING (not START), so this
                    // does not wipe the graph during transient drops.
                    if (chartInitialized) {
                        resetMiniChartSession()
                    }
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
        val displayCountry = sharedPreferences.getBoolean("display_vpn_country", false)

        // Update action bar display
        updateActionBarDisplay()
        
        // Fetch country info if display is enabled
        if (displayCountry) {
            fetchCountryInfo()
        }
    }
    
    private fun updateActionBarDisplay() {
        val displayCountry = sharedPreferences.getBoolean("display_vpn_country", false)
        
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
            actionBarTitle.text = getString(R.string.app_launcher)
        }
    }
    
    private fun fetchCountryInfo(connectionLevel: ConnectionStatus? = null) {
        lifecycleScope.launch {
            val info = countryInfoRepository.fetchCountryInfo()
            if (info == null) {
                showFallbackInfo()
                return@launch
            }

            actionBarCountryIp.text = info.ip
            actionBarCountryName.text = countryInfoRepository.countryName(info.countryCode)
            updateActionBarDisplay()
            loadCountryFlag(info.countryCode)

            // Save country for current profile (only when VPN is actually connected).
            // connectionLevel can be stale (captured when the fetch/retry was scheduled),
            // so also verify the VPN is STILL connected right now.
            val currentProfileUUID = VpnStatus.getLastConnectedVPNProfile()
            if (currentProfileUUID != null && connectionLevel == ConnectionStatus.LEVEL_CONNECTED &&
                    lastKnownLevel == ConnectionStatus.LEVEL_CONNECTED) {
                countryInfoRepository.saveProfileCountry(currentProfileUUID, info.countryCode)
                refreshVPNProfileList()
            }
        }
    }
    
    private fun showFallbackInfo() {
        // Show fallback information when API fails
        // Old country bar removed - no fallback UI needed
        // countryName.text = "VPN Connected"
        // countryIp.text = "Checking..."
        // Try to load a generic VPN flag or use fallback
        // countryFlag.setImageResource(R.mipmap.ic_launcher_foreground)
    }
    
    private fun getCountryName(countryCode: String): String {
        return when (countryCode.uppercase()) {
            "AD" -> "Andorra"
            "AE" -> "United Arab Emirates"
            "AF" -> "Afghanistan"
            "AG" -> "Antigua and Barbuda"
            "AI" -> "Anguilla"
            "AL" -> "Albania"
            "AM" -> "Armenia"
            "AO" -> "Angola"
            "AQ" -> "Antarctica"
            "AR" -> "Argentina"
            "AS" -> "American Samoa"
            "AT" -> "Austria"
            "AU" -> "Australia"
            "AW" -> "Aruba"
            "AX" -> "Åland Islands"
            "AZ" -> "Azerbaijan"
            "BA" -> "Bosnia and Herzegovina"
            "BB" -> "Barbados"
            "BD" -> "Bangladesh"
            "BE" -> "Belgium"
            "BF" -> "Burkina Faso"
            "BG" -> "Bulgaria"
            "BH" -> "Bahrain"
            "BI" -> "Burundi"
            "BJ" -> "Benin"
            "BL" -> "Saint Barthélemy"
            "BM" -> "Bermuda"
            "BN" -> "Brunei"
            "BO" -> "Bolivia"
            "BQ" -> "Caribbean Netherlands"
            "BR" -> "Brazil"
            "BS" -> "Bahamas"
            "BT" -> "Bhutan"
            "BV" -> "Bouvet Island"
            "BW" -> "Botswana"
            "BY" -> "Belarus"
            "BZ" -> "Belize"
            "CA" -> "Canada"
            "CC" -> "Cocos Islands"
            "CD" -> "DR Congo"
            "CF" -> "Central African Republic"
            "CG" -> "Republic of the Congo"
            "CH" -> "Switzerland"
            "CI" -> "Côte d'Ivoire"
            "CK" -> "Cook Islands"
            "CL" -> "Chile"
            "CM" -> "Cameroon"
            "CN" -> "China"
            "CO" -> "Colombia"
            "CR" -> "Costa Rica"
            "CU" -> "Cuba"
            "CV" -> "Cape Verde"
            "CW" -> "Curaçao"
            "CX" -> "Christmas Island"
            "CY" -> "Cyprus"
            "CZ" -> "Czechia"
            "DE" -> "Germany"
            "DJ" -> "Djibouti"
            "DK" -> "Denmark"
            "DM" -> "Dominica"
            "DO" -> "Dominican Republic"
            "DZ" -> "Algeria"
            "EC" -> "Ecuador"
            "EE" -> "Estonia"
            "EG" -> "Egypt"
            "EH" -> "Western Sahara"
            "ER" -> "Eritrea"
            "ES" -> "Spain"
            "ET" -> "Ethiopia"
            "FI" -> "Finland"
            "FJ" -> "Fiji"
            "FK" -> "Falkland Islands"
            "FM" -> "Micronesia"
            "FO" -> "Faroe Islands"
            "FR" -> "France"
            "GA" -> "Gabon"
            "GB" -> "United Kingdom"
            "GB_ENG" -> "England"
            "GB_NIR" -> "Northern Ireland"
            "GB_SCT" -> "Scotland"
            "GB_WLS" -> "Wales"
            "GD" -> "Grenada"
            "GE" -> "Georgia"
            "GF" -> "French Guiana"
            "GG" -> "Guernsey"
            "GH" -> "Ghana"
            "GI" -> "Gibraltar"
            "GL" -> "Greenland"
            "GM" -> "Gambia"
            "GN" -> "Guinea"
            "GP" -> "Guadeloupe"
            "GQ" -> "Equatorial Guinea"
            "GR" -> "Greece"
            "GS" -> "South Georgia"
            "GT" -> "Guatemala"
            "GU" -> "Guam"
            "GW" -> "Guinea-Bissau"
            "GY" -> "Guyana"
            "HK" -> "Hong Kong"
            "HM" -> "Heard Island"
            "HN" -> "Honduras"
            "HR" -> "Croatia"
            "HT" -> "Haiti"
            "HU" -> "Hungary"
            "ID" -> "Indonesia"
            "IE" -> "Ireland"
            "IL" -> "Israel"
            "IM" -> "Isle of Man"
            "IN" -> "India"
            "IO" -> "British Indian Ocean Territory"
            "IQ" -> "Iraq"
            "IR" -> "Iran"
            "IS" -> "Iceland"
            "IT" -> "Italy"
            "JE" -> "Jersey"
            "JM" -> "Jamaica"
            "JO" -> "Jordan"
            "JP" -> "Japan"
            "KE" -> "Kenya"
            "KG" -> "Kyrgyzstan"
            "KH" -> "Cambodia"
            "KI" -> "Kiribati"
            "KM" -> "Comoros"
            "KN" -> "Saint Kitts and Nevis"
            "KP" -> "North Korea"
            "KR" -> "South Korea"
            "KW" -> "Kuwait"
            "KY" -> "Cayman Islands"
            "KZ" -> "Kazakhstan"
            "LA" -> "Laos"
            "LB" -> "Lebanon"
            "LC" -> "Saint Lucia"
            "LI" -> "Liechtenstein"
            "LK" -> "Sri Lanka"
            "LR" -> "Liberia"
            "LS" -> "Lesotho"
            "LT" -> "Lithuania"
            "LU" -> "Luxembourg"
            "LV" -> "Latvia"
            "LY" -> "Libya"
            "MA" -> "Morocco"
            "MC" -> "Monaco"
            "MD" -> "Moldova"
            "ME" -> "Montenegro"
            "MF" -> "Saint Martin"
            "MG" -> "Madagascar"
            "MH" -> "Marshall Islands"
            "MK" -> "North Macedonia"
            "ML" -> "Mali"
            "MM" -> "Myanmar"
            "MN" -> "Mongolia"
            "MO" -> "Macau"
            "MP" -> "Northern Mariana Islands"
            "MQ" -> "Martinique"
            "MR" -> "Mauritania"
            "MS" -> "Montserrat"
            "MT" -> "Malta"
            "MU" -> "Mauritius"
            "MV" -> "Maldives"
            "MW" -> "Malawi"
            "MX" -> "Mexico"
            "MY" -> "Malaysia"
            "MZ" -> "Mozambique"
            "NA" -> "Namibia"
            "NC" -> "New Caledonia"
            "NE" -> "Niger"
            "NF" -> "Norfolk Island"
            "NG" -> "Nigeria"
            "NI" -> "Nicaragua"
            "NL" -> "Netherlands"
            "NO" -> "Norway"
            "NP" -> "Nepal"
            "NR" -> "Nauru"
            "NU" -> "Niue"
            "NZ" -> "New Zealand"
            "OM" -> "Oman"
            "PA" -> "Panama"
            "PE" -> "Peru"
            "PF" -> "French Polynesia"
            "PG" -> "Papua New Guinea"
            "PH" -> "Philippines"
            "PK" -> "Pakistan"
            "PL" -> "Poland"
            "PM" -> "Saint Pierre and Miquelon"
            "PN" -> "Pitcairn Islands"
            "PR" -> "Puerto Rico"
            "PS" -> "Palestine"
            "PT" -> "Portugal"
            "PW" -> "Palau"
            "PY" -> "Paraguay"
            "QA" -> "Qatar"
            "RE" -> "Réunion"
            "RO" -> "Romania"
            "RS" -> "Serbia"
            "RU" -> "Russia"
            "RW" -> "Rwanda"
            "SA" -> "Saudi Arabia"
            "SB" -> "Solomon Islands"
            "SC" -> "Seychelles"
            "SD" -> "Sudan"
            "SE" -> "Sweden"
            "SG" -> "Singapore"
            "SH" -> "Saint Helena"
            "SI" -> "Slovenia"
            "SJ" -> "Svalbard and Jan Mayen"
            "SK" -> "Slovakia"
            "SL" -> "Sierra Leone"
            "SM" -> "San Marino"
            "SN" -> "Senegal"
            "SO" -> "Somalia"
            "SR" -> "Suriname"
            "SS" -> "South Sudan"
            "ST" -> "São Tomé and Príncipe"
            "SV" -> "El Salvador"
            "SX" -> "Sint Maarten"
            "SY" -> "Syria"
            "SZ" -> "Eswatini"
            "TC" -> "Turks and Caicos Islands"
            "TD" -> "Chad"
            "TF" -> "French Southern Territories"
            "TG" -> "Togo"
            "TH" -> "Thailand"
            "TJ" -> "Tajikistan"
            "TK" -> "Tokelau"
            "TL" -> "Timor-Leste"
            "TM" -> "Turkmenistan"
            "TN" -> "Tunisia"
            "TO" -> "Tonga"
            "TR" -> "Turkey"
            "TT" -> "Trinidad and Tobago"
            "TV" -> "Tuvalu"
            "TW" -> "Taiwan"
            "TZ" -> "Tanzania"
            "UA" -> "Ukraine"
            "UG" -> "Uganda"
            "UM" -> "U.S. Minor Outlying Islands"
            "US" -> "United States"
            "UY" -> "Uruguay"
            "UZ" -> "Uzbekistan"
            "VA" -> "Vatican City"
            "VC" -> "Saint Vincent and the Grenadines"
            "VE" -> "Venezuela"
            "VG" -> "British Virgin Islands"
            "VI" -> "U.S. Virgin Islands"
            "VN" -> "Vietnam"
            "VU" -> "Vanuatu"
            "WF" -> "Wallis and Futuna"
            "WS" -> "Samoa"
            "XK" -> "Kosovo"
            "YE" -> "Yemen"
            "YT" -> "Mayotte"
            "ZA" -> "South Africa"
            "ZM" -> "Zambia"
            "ZW" -> "Zimbabwe"
            else -> countryCode.uppercase()
        }
    }
    
    private fun loadCountryFlag(countryCode: String) {
        try {
            val resourceId = countryInfoRepository.flagResourceId(countryCode)
            
            if (resourceId != 0) {
                // countryFlag.setImageResource(resourceId)
                actionBarCountryFlag.setImageResource(resourceId)
                // Scale the flag to proper size (24dp x 16dp)
                // countryFlag.scaleType = ImageView.ScaleType.FIT_CENTER
                actionBarCountryFlag.scaleType = ImageView.ScaleType.FIT_CENTER
            } else {
                // Use a generic flag or placeholder if specific flag not found
                // countryFlag.setImageResource(R.mipmap.ic_launcher_foreground)
                actionBarCountryFlag.setImageResource(R.mipmap.ic_launcher_foreground)
            }
        } catch (e: Exception) {
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
            setIntent(null)
        }
        
        // Restart periodic updates
        startPeriodicUpdates()

        // Re-create/show the mini chart if we returned to the screen while already
        // connected (the LEVEL_CONNECTED transition that normally builds it may have
        // happened while this screen was in the background).
        reconcileMiniChart()
    }

    override fun onPause() {
        super.onPause()
        // Stop periodic updates
        stopPeriodicUpdates()
    }

    // Animation methods for smooth mini chart transitions
    /**
     * Ensures the mini chart matches the current connection state. The chart is
     * normally created on the LEVEL_CONNECTED transition; if that transition was
     * missed because this screen was not in the foreground (e.g. the user connected
     * and immediately opened the log screen), this re-creates and shows it from the
     * traffic history that is already being collected. Safe to call repeatedly.
     */
    private fun reconcileMiniChart() {
        if (lastKnownLevel != ConnectionStatus.LEVEL_CONNECTED) return

        if (!chartInitialized) {
            initializeMiniChart()
            chartInitialized = true
            registerByteCountListener()
        }
        showMiniChartImmediate()
        updateMiniChart()
    }

    /** Shows the mini chart container at full height without animating (used when reconciling). */
    private fun showMiniChartImmediate() {
        miniChartContainer?.let { container ->
            container.visibility = View.VISIBLE
            container.layoutParams?.height = 160.dpToPx()
            container.requestLayout()
        }
    }

    private fun animateMiniChartShow() {
        miniChartContainer?.let { container ->
            // Already shown (or animating in) — don't collapse and replay the animation.
            if (container.visibility == View.VISIBLE && (container.layoutParams?.height ?: 0) > 0) {
                return@let
            }
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
        }
    }

    // Stop VPN profile list animations when connection state changes
    private fun stopVPNProfileAnimations() {
        // Find the VPNProfileList fragment and stop animations
        supportFragmentManager.fragments.forEach { fragment ->
            if (fragment is VPNProfileList) {
                fragment.stopAllAnimations()
                return@forEach
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up
        stopPeriodicUpdates()
        VpnStatus.removeStateListener(this)
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        countryInfoRepository.stopNetworkMonitoring()
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
    
    private fun refreshVPNProfileList() {
        // Find the VPNProfileList fragment and refresh it
        supportFragmentManager.fragments.forEach { fragment ->
            if (fragment is VPNProfileList) {
                fragment.refreshFlags()
                return@forEach
            }
        }
    }
}