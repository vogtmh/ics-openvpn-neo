/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.fragments

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.mavodev.openvpnneo.R
import kotlin.math.max

class FaqFragment : Fragment() {

    class FAQEntry(
        val startVersion: Int,
        val endVersion: Int,
        val title: Int,
        val description: Int
    ) {
        fun runningVersion(): Boolean {
            if (Build.VERSION.SDK_INT >= startVersion) {
                if (Build.VERSION.SDK_INT <= endVersion) return true
                if (endVersion == -1) return true

                val release = Build.VERSION.RELEASE
                val isOlderThan443 = !release.startsWith("4.4.3") && !release.startsWith("4.4.4") &&
                    !release.startsWith("4.4.5") && !release.startsWith("4.4.6")
                val isOlderThan442 = isOlderThan443 && !release.startsWith("4.4.2")

                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
                    if (endVersion == -441 && isOlderThan442) return true
                    if (endVersion == -442 && isOlderThan443) return true
                } else if (endVersion == -441 || endVersion == -442) {
                    return Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT
                }
            }
            return false
        }

        fun getVersionsString(c: Context): String? {
            if (startVersion == Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                return if (endVersion == -1) null
                else c.getString(R.string.version_upto, getAndroidVersionString(c, endVersion))
            }

            if (endVersion == -1) {
                return c.getString(R.string.version_and_later, getAndroidVersionString(c, startVersion))
            }

            val startver = getAndroidVersionString(c, startVersion)
            if (endVersion == startVersion) return startver

            return String.format("%s - %s", startver, getAndroidVersionString(c, endVersion))
        }

        private fun getAndroidVersionString(c: Context, versionCode: Int): String {
            return when (versionCode) {
                Build.VERSION_CODES.ICE_CREAM_SANDWICH -> "4.0 (Ice Cream Sandwich)"
                -441 -> "4.4.1 (KitKat)"
                -442 -> "4.4.2 (KitKat)"
                Build.VERSION_CODES.JELLY_BEAN_MR2 -> "4.3 (Jelly Bean MR2)"
                Build.VERSION_CODES.KITKAT -> "4.4 (KitKat)"
                Build.VERSION_CODES.LOLLIPOP -> "5.0 (Lollipop)"
                else -> "API $versionCode"
            }
        }
    }

    private lateinit var mRecyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.faq, container, false)

        val displaymetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        requireActivity().windowManager.defaultDisplay.getMetrics(displaymetrics)
        val dpWidth = (displaymetrics.widthPixels / resources.displayMetrics.density).toInt()

        val columns = max(1, dpWidth / 360)

        mRecyclerView = v.findViewById(R.id.faq_recycler_view)

        // use this setting to improve performance if you know that changes
        // in content do not change the layout size of the RecyclerView
        mRecyclerView.setHasFixedSize(true)

        mRecyclerView.layoutManager = StaggeredGridLayoutManager(columns, StaggeredGridLayoutManager.VERTICAL)

        Utils.applyInsetListener(v)

        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Post the adapter setup to ensure the RecyclerView is properly laid out
        mRecyclerView.post {
            try {
                val entries = getFAQEntries()
                if (entries.isNotEmpty()) {
                    mRecyclerView.adapter = FaqViewAdapter(requireActivity(), entries)
                }
            } catch (e: Exception) {
                // If there's an error, try again after a short delay
                mRecyclerView.postDelayed({
                    try {
                        val entries = getFAQEntries()
                        if (entries.isNotEmpty()) {
                            mRecyclerView.adapter = FaqViewAdapter(requireActivity(), entries)
                        }
                    } catch (e2: Exception) {
                        // Log error but don't crash
                        Log.e("FaqFragment", "Failed to load FAQ entries", e2)
                    }
                }, 100)
            }
        }
    }

    private fun getFAQEntries(): Array<FAQEntry> {
        val faqItems = ArrayList<FAQEntry>()
        for (fe in faqitemsVersionSpecific) {
            if (fe.runningVersion()) faqItems.add(fe)
        }
        for (fe in faqitemsVersionSpecific) {
            if (!fe.runningVersion()) faqItems.add(fe)
        }
        return faqItems.toTypedArray()
    }

    companion object {
        private val faqitemsVersionSpecific = arrayOf(
            FAQEntry(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.faq_howto_title, R.string.faq_howto),
            FAQEntry(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.faq_title_ncp, R.string.faq_ncp),
            FAQEntry(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.faq_killswitch_title, R.string.faq_killswitch),
            FAQEntry(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.faq_remote_api_title, R.string.faq_remote_api),
            FAQEntry(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.weakmd_title, R.string.weakmd),
            FAQEntry(Build.VERSION_CODES.LOLLIPOP, -1, R.string.samsung_broken_title, R.string.samsung_broken),
            FAQEntry(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.faq_duplicate_notification_title, R.string.faq_duplicate_notification),
            FAQEntry(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.faq_androids_clients_title, R.string.faq_android_clients),
            FAQEntry(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.battery_consumption_title, R.string.baterry_consumption),
            FAQEntry(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.tap_mode, R.string.faq_tap_mode),
            FAQEntry(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.tls_cipher_alert_title, R.string.tls_cipher_alert),
            FAQEntry(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.faq_security_title, R.string.faq_security),
            FAQEntry(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.faq_shortcut, R.string.faq_howto_shortcut),
            FAQEntry(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.tap_mode, R.string.tap_faq2),
            FAQEntry(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.copying_log_entries, R.string.faq_copying),
            FAQEntry(Build.VERSION_CODES.KITKAT, -1, R.string.faq_routing_title, R.string.faq_routing),
            FAQEntry(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.ab_only_cidr_title, R.string.ab_only_cidr),
            FAQEntry(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.ab_proxy_title, R.string.ab_proxy),
            FAQEntry(Build.VERSION_CODES.LOLLIPOP, -1, R.string.ab_not_route_to_vpn_title, R.string.ab_not_route_to_vpn),
            FAQEntry(Build.VERSION_CODES.ICE_CREAM_SANDWICH, -1, R.string.tap_mode, R.string.tap_faq3)
        )
    }
}
