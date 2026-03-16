package com.mavodev.openvpnneo.fragments

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.fragment.app.ListFragment
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.adapters.SettingsAdapter
import com.mavodev.openvpnneo.activities.OpenSSLSpeed
import com.mavodev.openvpnneo.core.Preferences
import com.mavodev.openvpnneo.core.ProfileManager
import com.mavodev.openvpnneo.model.SettingItem
import com.mavodev.openvpnneo.model.SettingType

class CustomSettingsFragment : ListFragment() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var settingsAdapter: SettingsAdapter
    private lateinit var settings: MutableList<SettingItem>

    companion object {
        fun newInstance(): CustomSettingsFragment {
            return CustomSettingsFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_custom_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        sharedPreferences = Preferences.getDefaultSharedPreferences(requireContext())
        setupSettings()
        setupAdapter()
    }

    private fun setupSettings() {
        settings = mutableListOf()

        // Application Behaviour Category
        settings.add(SettingItem(
            key = "category_app_behaviour",
            title = getString(R.string.appbehaviour),
            description = "",
            type = SettingType.CATEGORY
        ))

        settings.add(SettingItem(
            key = "showlogwindow",
            title = getString(R.string.show_log_window),
            description = getString(R.string.show_log_summary),
            type = SettingType.TOGGLE_SLIDER,
            value = sharedPreferences.getBoolean("showlogwindow", false)
        ))

        settings.add(SettingItem(
            key = "allow_translation",
            title = getString(R.string.allow_translations_title),
            description = getString(R.string.allow_translations_summary),
            type = SettingType.TOGGLE_SLIDER,
            value = sharedPreferences.getBoolean("allow_translation", resources.getBoolean(R.bool.allowTranslationDefault))
        ))

        settings.add(SettingItem(
            key = "ovpn3",
            title = "OpenVPN 3 Core",
            description = "Use the C++ OpenVPN library (experimental)",
            type = SettingType.TOGGLE_SLIDER,
            value = sharedPreferences.getBoolean("ovpn3", false)
        ))

        settings.add(SettingItem(
            key = "display_vpn_country",
            title = "Display VPN country",
            description = "Request and display the country of your current connection via https://api.country.is/",
            type = SettingType.TOGGLE_SLIDER,
            value = sharedPreferences.getBoolean("display_vpn_country", false)
        ))

        settings.add(SettingItem(
            key = "alwaysOnVpn",
            title = getString(R.string.defaultvpn),
            description = getString(R.string.defaultvpnsummary),
            type = SettingType.ACTION,
            action = { showDefaultVpnSelectionDialog() }
        ))

        settings.add(SettingItem(
            key = "restartvpnonboot",
            title = getString(R.string.keep_vpn_connected),
            description = getString(R.string.keep_vpn_connected_summary),
            type = SettingType.TOGGLE_SLIDER,
            value = sharedPreferences.getBoolean("restartvpnonboot", false)
        ))

        settings.add(SettingItem(
            key = "preferencryption",
            title = getString(R.string.encrypt_profiles),
            description = getString(R.string.encrypt_profiles_summary),
            type = SettingType.TOGGLE_SLIDER,
            value = sharedPreferences.getBoolean("preferencryption", true)
        ))

        // Add "Clear allowed external apps" with conditional logic
        val allowedApps = sharedPreferences.getStringSet("allowed_apps", emptySet()) ?: emptySet()
        val clearApiDescription = if (allowedApps.isEmpty()) {
            getString(R.string.no_external_app_allowed)
        } else {
            "${allowedApps.size} app(s) allowed"
        }
        
        settings.add(SettingItem(
            key = "clearapi",
            title = getString(R.string.clear_external_apps),
            description = clearApiDescription,
            type = SettingType.ACTION,
            action = { 
                if (allowedApps.isNotEmpty()) {
                    showClearExternalAppsDialog()
                }
                // If no apps, action does nothing (item is effectively disabled)
            }
        ))

        // VPN Behaviour Category
        settings.add(SettingItem(
            key = "category_vpn_behaviour",
            title = getString(R.string.vpnbehaviour),
            description = "",
            type = SettingType.CATEGORY
        ))

        settings.add(SettingItem(
            key = "usesystemproxy",
            title = getString(R.string.use_system_proxy),
            description = getString(R.string.use_system_proxy_summary),
            type = SettingType.TOGGLE_SLIDER,
            value = sharedPreferences.getBoolean("usesystemproxy", true)
        ))

        settings.add(SettingItem(
            key = "netchangereconnect",
            title = getString(R.string.netchange),
            description = getString(R.string.netchange_summary),
            type = SettingType.TOGGLE_SLIDER,
            value = sharedPreferences.getBoolean("netchangereconnect", true)
        ))

        settings.add(SettingItem(
            key = "screenoff",
            title = getString(R.string.screenoff_title),
            description = getString(R.string.screenoff_summary),
            type = SettingType.TOGGLE_SLIDER,
            value = sharedPreferences.getBoolean("screenoff", false)
        ))

        settings.add(SettingItem(
            key = "disableconfirmation",
            title = getString(R.string.confirmations_title),
            description = getString(R.string.confirmations_summary),
            type = SettingType.TOGGLE_SLIDER,
            value = sharedPreferences.getBoolean("disableconfirmation", false)
        ))

        settings.add(SettingItem(
            key = "osslspeed",
            title = getString(R.string.osslspeedtest),
            description = "",
            type = SettingType.ACTION,
            action = {
                val intent = Intent(requireContext(), OpenSSLSpeed::class.java)
                intent.putExtra("from_settings", true)
                startActivity(intent)
            }
        ))

        // Device Specific Hacks Category - Add conditionally
        val deviceHacks = mutableListOf<SettingItem>()
        
        // CM9 Fix - Only show on older Android versions or if already enabled
        val cm9FixEnabled = sharedPreferences.getBoolean("useCM9Fix", false)
        if (cm9FixEnabled || android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            deviceHacks.add(SettingItem(
                key = "useCM9Fix",
                title = getString(R.string.owner_fix),
                description = getString(R.string.owner_fix_summary),
                type = SettingType.TOGGLE_SLIDER,
                value = cm9FixEnabled
            ))
        }
        
        // Load Tun Module - Only show if tun module is available
        if (isTunModuleAvailable()) {
            deviceHacks.add(SettingItem(
                key = "loadTunModule",
                title = getString(R.string.setting_loadtun),
                description = getString(R.string.setting_loadtun_summary),
                type = SettingType.TOGGLE_SLIDER,
                value = sharedPreferences.getBoolean("loadTunModule", false)
            ))
        }
        
        // Internal File Selector - Only show on older Android versions
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
            deviceHacks.add(SettingItem(
                key = "useInternalFileSelector",
                title = "Use internal file browser",
                description = "Always use the very basic file browser instead of the Android file browser. Use this option if you have problems selecting files.",
                type = SettingType.TOGGLE_SLIDER,
                value = sharedPreferences.getBoolean("useInternalFileSelector", false)
            ))
        }
        
        // Only add the category if there are any device hacks to show
        if (deviceHacks.isNotEmpty()) {
            settings.add(SettingItem(
                key = "category_device_hacks",
                title = getString(R.string.device_specific),
                description = "",
                type = SettingType.CATEGORY
            ))
            settings.addAll(deviceHacks)
        }
    }

    private fun isTunModuleAvailable(): Boolean {
        // Check if /dev/tun exists and is accessible
        return try {
            val tunFile = java.io.File("/dev/tun")
            tunFile.exists() && tunFile.canRead() && tunFile.canWrite()
        } catch (e: Exception) {
            false
        }
    }

    private fun setupAdapter() {
        settingsAdapter = SettingsAdapter(
            context = requireContext(),
            settings = settings,
            onSettingChanged = { key, value ->
                sharedPreferences.edit().putBoolean(key, value).apply()
                
                // Handle special cases
                when (key) {
                    "openvpn3" -> {
                        // Handle OpenVPN 3 toggle logic
                    }
                    "keep_vpn_connected" -> {
                        // Handle Always-On VPN logic
                    }
                    // Add other special cases as needed
                }
            }
        )
        
        listAdapter = settingsAdapter
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        super.onListItemClick(l, v, position, id)
        
        val setting = settings[position]
        if (setting.type == SettingType.ACTION) {
            setting.action?.invoke()
        }
    }

    private fun showDefaultVpnSelectionDialog() {
        val profileManager = ProfileManager.getInstance(requireContext())
        val profiles = profileManager.getProfiles()
        
        if (profiles.isEmpty()) {
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.defaultvpn))
                .setMessage(getString(R.string.novpn_selected))
                .setPositiveButton(android.R.string.ok, null)
                .create()
            
            // Set background color only in dark mode
            dialog.setOnShowListener {
                if (isInDarkMode()) {
                    val colorDrawable = android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#111111"))
                    dialog.window?.setBackgroundDrawable(colorDrawable)
                }
            }
            dialog.show()
            return
        }

        val profileNames = profiles.map { it.name }.toTypedArray()
        val profileUuids = profiles.map { it.uuid.toString() }.toTypedArray()
        
        val currentDefaultVpn = sharedPreferences.getString("alwaysOnVpn", null)
        val currentIndex = profileUuids.indexOf(currentDefaultVpn)
        
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.defaultvpn))
            .setSingleChoiceItems(profileNames, currentIndex) { dialog, which ->
                val selectedUuid = profileUuids[which]
                sharedPreferences.edit().putString("alwaysOnVpn", selectedUuid).apply()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            
        // Set background color only in dark mode
        dialog.setOnShowListener {
            if (isInDarkMode()) {
                val colorDrawable = android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#111111"))
                dialog.window?.setBackgroundDrawable(colorDrawable)
            }
        }
        dialog.show()
    }

    private fun showClearExternalAppsDialog() {
        val allowedApps = sharedPreferences.getStringSet("allowed_apps", emptySet()) ?: emptySet()
        
        if (allowedApps.isEmpty()) {
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.clear_external_apps))
                .setMessage(getString(R.string.no_external_app_allowed))
                .setPositiveButton(android.R.string.ok, null)
                .create()
                
            // Set background color only in dark mode
            dialog.setOnShowListener {
                if (isInDarkMode()) {
                    val colorDrawable = android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#111111"))
                    dialog.window?.setBackgroundDrawable(colorDrawable)
                }
            }
            dialog.show()
            return
        }

        val appsList = allowedApps.joinToString("\n")
        
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.clear_external_apps))
            .setMessage(getString(R.string.clearappsdialog, appsList))
            .setPositiveButton(android.R.string.yes) { _, _ ->
                sharedPreferences.edit().remove("allowed_apps").apply()
            }
            .setNegativeButton(android.R.string.no, null)
            .create()
            
        // Set background color only in dark mode
        dialog.setOnShowListener {
            if (isInDarkMode()) {
                val colorDrawable = android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#111111"))
                dialog.window?.setBackgroundDrawable(colorDrawable)
            }
        }
        dialog.show()
    }

    private fun isInDarkMode(): Boolean {
        return when (requireContext().resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) {
            android.content.res.Configuration.UI_MODE_NIGHT_YES -> true
            android.content.res.Configuration.UI_MODE_NIGHT_NO -> false
            else -> false
        }
    }
}
