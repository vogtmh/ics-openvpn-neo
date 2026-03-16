/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.activities

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.Window
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.core.GlobalPreferences
import com.mavodev.openvpnneo.fragments.*
import com.mavodev.openvpnneo.fragments.ImportRemoteConfig.Companion.newInstance
import com.mavodev.openvpnneo.views.ScreenSlidePagerAdapter

class MainActivity : BaseActivity() {
    private lateinit var mPager: ViewPager2
    private lateinit var mPagerAdapter: ScreenSlidePagerAdapter


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
            mPagerAdapter.addTab(R.string.generalsettings, GeneralSettings::class.java)
            mPagerAdapter.addTab(R.string.faq, FaqFragment::class.java)
            if (SendDumpFragment.getLastestDump(this) != null) {
                mPagerAdapter.addTab(R.string.crashdump, SendDumpFragment::class.java)
            }

        }
        if (isAndroidTV || minimalUi)
            mPagerAdapter.addTab(R.string.openvpn_log, LogFragment::class.java)

        mPagerAdapter.addTab(R.string.about, AboutFragment::class.java)
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
        val settingsBtn = rootLayout.findViewById<ImageButton>(R.id.tab_settings)
        val faqBtn = rootLayout.findViewById<ImageButton>(R.id.tab_faq)
        val aboutBtn = rootLayout.findViewById<ImageButton>(R.id.tab_about)
        
        Log.d("MainActivity", "Profiles button found: ${profilesBtn != null}")
        Log.d("MainActivity", "Graph button found: ${graphBtn != null}")
        Log.d("MainActivity", "Settings button found: ${settingsBtn != null}")
        Log.d("MainActivity", "FAQ button found: ${faqBtn != null}")
        Log.d("MainActivity", "About button found: ${aboutBtn != null}")
        
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
        settingsBtn?.setOnClickListener {
            Log.d("MainActivity", "SETTINGS BUTTON CLICKED - going to position 2")
            mPager.currentItem = 2  // Settings is position 2
            supportActionBar?.title = mPagerAdapter.getPageTitle(2)
            updateButtonStates(2)
        }
        faqBtn?.setOnClickListener {
            Log.d("MainActivity", "FAQ BUTTON CLICKED - going to position 3")
            mPager.currentItem = 3  // FAQ is position 3
            supportActionBar?.title = mPagerAdapter.getPageTitle(3)
            updateButtonStates(3)
        }
        aboutBtn?.setOnClickListener {
            val lastPosition = mPagerAdapter.itemCount - 1  // Go to last tab
            Log.d("MainActivity", "ABOUT BUTTON CLICKED - going to position $lastPosition")
            mPager.currentItem = lastPosition
            supportActionBar?.title = mPagerAdapter.getPageTitle(lastPosition)
            updateButtonStates(lastPosition)
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
                val statusBarHeight = resources.getDimensionPixelSize(resourceId)
                val rootLayout = findViewById<LinearLayout>(R.id.root_linear_layout)
                
                // Get navigation bar height
                val navBarResourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
                val navBarHeight = if (navBarResourceId > 0) {
                    resources.getDimensionPixelSize(navBarResourceId)
                } else 0
                
                // Set padding: top for status bar, bottom for navigation bar
                rootLayout.setPadding(0, statusBarHeight, 0, navBarHeight)
            }
        }
        
        // Set initial button states after layout is set
        updateButtonStates(0)
    }


    private fun disableToolbarElevation() {
        supportActionBar?.elevation = 0f
    }
    
    private fun updateButtonStates(selectedPosition: Int) {
        val rootLayout = findViewById<LinearLayout>(R.id.root_linear_layout)
        val profilesBtn = rootLayout.findViewById<ImageButton>(R.id.tab_profiles)
        val graphBtn = rootLayout.findViewById<ImageButton>(R.id.tab_graph)
        val settingsBtn = rootLayout.findViewById<ImageButton>(R.id.tab_settings)
        val faqBtn = rootLayout.findViewById<ImageButton>(R.id.tab_faq)
        val aboutBtn = rootLayout.findViewById<ImageButton>(R.id.tab_about)
        
        val lastPosition = mPagerAdapter.itemCount - 1
        
        profilesBtn?.isSelected = selectedPosition == 0
        graphBtn?.isSelected = selectedPosition == 1
        settingsBtn?.isSelected = selectedPosition == 2
        faqBtn?.isSelected = selectedPosition == 3
        aboutBtn?.isSelected = selectedPosition == lastPosition
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (!GlobalPreferences.getMinimalUi())
            menuInflater.inflate(R.menu.main_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.show_log) {
            val showLog = Intent(this, LogWindow::class.java)
            startActivity(showLog)
        }
        return super.onOptionsItemSelected(item)
    }
}