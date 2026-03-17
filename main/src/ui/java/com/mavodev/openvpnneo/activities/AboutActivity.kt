/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.activities

import android.content.pm.PackageManager.NameNotFoundException
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.mavodev.openvpnneo.BuildConfig
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.core.NativeUtils
import java.util.*

class AboutActivity : BaseActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.about)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.about)
        
        setupVersionInfo()
        setupLinks()
        setupWindowInsets()
    }
    
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val extraPadding = resources.getDimensionPixelSize(R.dimen.stdpadding)
            view.setPadding(insets.left, insets.top + extraPadding, insets.right, insets.bottom)
            windowInsets
        }
    }
    
    private fun setupVersionInfo() {
        val ver = findViewById<TextView>(R.id.version)
        var version: String
        var name = "Openvpn"
        try {
            val packageinfo = packageManager.getPackageInfo(packageName, 0)
            version = packageinfo.versionName ?: "unknown"
            name = getString(R.string.app)
        } catch (e: NameNotFoundException) {
            version = "error fetching version"
        }
        
        ver.text = getString(R.string.version_info, name, version)
        
        // Set OpenVPN version info
        val verO2 = findViewById<TextView>(R.id.version_ovpn2)
        val verO3 = findViewById<TextView>(R.id.version_ovpn3)
        val osslVer = findViewById<TextView>(R.id.openssl_version)
        
        verO2.text = String.format(Locale.US, "OpenVPN version: %s", NativeUtils.getOpenVPN2GitVersion())
        if (BuildConfig.openvpn3)
            verO3.text = String.format(Locale.US, "OpenVPN3 core version: %s", NativeUtils.getOpenVPN3GitVersion())
        else
            verO3.text = "(OpenVPN 2.x only build. No OpenVPN 3.x core in this app)"
        
        osslVer.text = String.format(Locale.US, "OpenSSL version: %s", NativeUtils.getOpenSSLVersion())
    }
    
    private fun setupLinks() {
        // Links are automatically handled by autoLink="all" in the layout
        // No additional setup needed
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
