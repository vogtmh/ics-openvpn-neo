/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.activities

import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.util.DisplayMetrics
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.fragments.Utils
import java.util.*

class FAQActivity : BaseActivity() {
    
    private lateinit var mRecyclerView: RecyclerView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.faq)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.faq)
        
        setupRecyclerView()
        setupWindowInsets()
    }
    
    // Direct copy from FaqFragment.onCreateView
    private fun setupRecyclerView() {
        val displaymetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displaymetrics)
        val dpWidth = (displaymetrics.widthPixels / resources.displayMetrics.density).toInt()
        
        val columns = dpWidth / 360
        val finalColumns = Math.max(1, columns)
        
        mRecyclerView = findViewById(R.id.faq_recycler_view)
        mRecyclerView.setHasFixedSize(true)
        mRecyclerView.layoutManager = StaggeredGridLayoutManager(finalColumns, StaggeredGridLayoutManager.VERTICAL)
        
        Utils.applyInsetListener(findViewById(android.R.id.content))
        
        // Direct copy from FaqFragment.onViewCreated
        mRecyclerView.post {
            try {
                val entries = getFAQEntries()
                if (entries.isNotEmpty()) {
                    mRecyclerView.adapter = CustomFAQAdapter(entries)
                }
            } catch (e: Exception) {
                // If there's an error, try again after a short delay
                mRecyclerView.postDelayed({
                    try {
                        val entries = getFAQEntries()
                        if (entries.isNotEmpty()) {
                            mRecyclerView.adapter = CustomFAQAdapter(entries)
                        }
                    } catch (e2: Exception) {
                        // Log error but don't crash
                        android.util.Log.e("FAQActivity", "Failed to load FAQ entries", e2)
                    }
                }, 100)
            }
        }
    }
    
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val extraPadding = resources.getDimensionPixelSize(R.dimen.stdpadding)
            view.setPadding(insets.left, insets.top + extraPadding, insets.right, insets.bottom)
            windowInsets
        }
    }
    
    // Direct copy from FaqFragment.getFAQEntries - use simple approach
    private fun getFAQEntries(): List<FAQEntryWrapper> {
        // Create a simple wrapper that mimics FAQEntry behavior
        return listOf(
            FAQEntryWrapper(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.faq_howto_title, R.string.faq_howto),
            FAQEntryWrapper(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.faq_title_ncp, R.string.faq_ncp),
            FAQEntryWrapper(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.faq_killswitch_title, R.string.faq_killswitch),
            FAQEntryWrapper(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.faq_remote_api_title, R.string.faq_remote_api),
            FAQEntryWrapper(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.weakmd_title, R.string.weakmd),
            FAQEntryWrapper(Build.VERSION_CODES.LOLLIPOP, -1, R.string.samsung_broken_title, R.string.samsung_broken),
            FAQEntryWrapper(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.faq_duplicate_notification_title, R.string.faq_duplicate_notification),
            FAQEntryWrapper(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.faq_androids_clients_title, R.string.faq_android_clients),
            FAQEntryWrapper(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.battery_consumption_title, R.string.baterry_consumption),
            FAQEntryWrapper(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.tap_mode, R.string.faq_tap_mode),
            FAQEntryWrapper(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.tls_cipher_alert_title, R.string.tls_cipher_alert),
            FAQEntryWrapper(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.faq_security_title, R.string.faq_security),
            FAQEntryWrapper(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.faq_shortcut, R.string.faq_howto_shortcut),
            FAQEntryWrapper(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.tap_mode, R.string.tap_faq2),
            FAQEntryWrapper(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.copying_log_entries, R.string.faq_copying),
            FAQEntryWrapper(Build.VERSION_CODES.KITKAT, -1, R.string.faq_routing_title, R.string.faq_routing),
            FAQEntryWrapper(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.ab_only_cidr_title, R.string.ab_only_cidr),
            FAQEntryWrapper(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.ab_proxy_title, R.string.ab_proxy),
            FAQEntryWrapper(Build.VERSION_CODES.LOLLIPOP, -1, R.string.ab_not_route_to_vpn_title, R.string.ab_not_route_to_vpn),
            FAQEntryWrapper(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.tap_mode, R.string.tap_faq3)
        )
    }
    
    // Custom adapter that mimics FaqViewAdapter behavior
    inner class CustomFAQAdapter(private val items: List<FAQEntryWrapper>) : RecyclerView.Adapter<CustomFAQAdapter.ViewHolder>() {
        
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val cardView: CardView = view as CardView
            val headView: TextView = view.findViewById(R.id.faq_head)
            val bodyView: TextView = view.findViewById(R.id.faq_body)
            
            init {
                bodyView.movementMethod = LinkMovementMethod.getInstance()
            }
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.faqcard, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            
            val versionText = item.getVersionsString(holder.itemView.context)
            var textColor = ""
            
            if (!item.runningVersion()) {
                textColor = "<font color=\"gray\">"
            }
            
            // Get string resources like the original adapter
            val title = if (item.title == -1) "" else holder.itemView.context.getString(item.title)
            val content = holder.itemView.context.getString(item.description)
            
            // Format title with version info - exact same as original
            val titleSpanned: Spanned = if (versionText != null) {
                TextUtils.concat(
                    Html.fromHtml(textColor + title),
                    Html.fromHtml(textColor + "<br><small>" + versionText + "</small>")
                ) as Spanned
            } else {
                Html.fromHtml(textColor + title)
            }
            
            // Format body - exact same as original FaqViewAdapter
            val bodySpanned = Html.fromHtml(textColor + content)
            
            holder.headView.text = titleSpanned
            holder.bodyView.text = bodySpanned
        }
        
        override fun getItemCount(): Int = items.size
    }
    
    // Wrapper class that mimics FAQEntry behavior
    class FAQEntryWrapper(
        val startVersion: Int,
        val endVersion: Int,
        val title: Int,
        val description: Int
    ) {
        fun runningVersion(): Boolean {
            if (android.os.Build.VERSION.SDK_INT >= startVersion) {
                if (android.os.Build.VERSION.SDK_INT <= endVersion) {
                    return true
                }
                if (endVersion == -1) {
                    return true
                }
            }
            return false
        }
        
        fun getVersionsString(context: android.content.Context): String? {
            if (startVersion == android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                if (endVersion == -1)
                    return null
                else
                    return context.getString(R.string.version_upto, getAndroidVersionString(context, endVersion))
            }

            if (endVersion == -1)
                return context.getString(R.string.version_and_later, getAndroidVersionString(context, startVersion))

            val startver = getAndroidVersionString(context, startVersion)

            if (endVersion == startVersion)
                return startver

            return String.format("%s - %s", startver, getAndroidVersionString(context, endVersion))
        }
        
        private fun getAndroidVersionString(context: android.content.Context, versionCode: Int): String {
            return when (versionCode) {
                android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH -> "4.0 (Ice Cream Sandwich)"
                -441 -> "4.4.1 (KitKat)"
                -442 -> "4.4.2 (KitKat)"
                android.os.Build.VERSION_CODES.JELLY_BEAN_MR2 -> "4.3 (Jelly Bean MR2)"
                android.os.Build.VERSION_CODES.KITKAT -> "4.4 (KitKat)"
                android.os.Build.VERSION_CODES.LOLLIPOP -> "5.0 (Lollipop)"
                else -> "API $versionCode"
            }
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
