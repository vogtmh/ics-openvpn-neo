/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.activities

import android.content.pm.PackageManager.NameNotFoundException
import android.os.Bundle
import androidx.core.text.HtmlCompat
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
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
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
        setUpEdgeEdgeInsetsListener(window.decorView.rootView, R.id.about_content)
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
        
        // Render the privacy policy with paragraphs + bold keywords. autoLink="all" on the
        // TextView still linkifies the domains/e-mails in the resulting text.
        val privacyPolicy = findViewById<TextView>(R.id.privacy_policy_text)
        privacyPolicy.text =
            HtmlCompat.fromHtml(getString(R.string.privacy_policy), HtmlCompat.FROM_HTML_MODE_LEGACY)
        
        // Setup full licenses - same as AboutFragment
        val fullLicenses = findViewById<TextView>(R.id.full_licenses)
        fullLicenses.text = HtmlCompat.fromHtml(readHtmlFromAssets(), HtmlCompat.FROM_HTML_MODE_LEGACY)
        fullLicenses.movementMethod = LinkMovementMethod.getInstance()
    }
    
    private fun setupLinks() {
        // Links are automatically handled by autoLink="all" in the layout
        // No additional setup needed
    }
    
    // Copy from AboutFragment - read full licenses from assets
    private fun readHtmlFromAssets(): String {
        return try {
            val inputStream = assets.open("full_licenses.html")
            val reader = BufferedReader(InputStreamReader(inputStream, Charset.forName("UTF-8")))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append("\n")
            }
            reader.close()
            sb.toString()
        } catch (e: IOException) {
            "full_licenses.html not found"
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
