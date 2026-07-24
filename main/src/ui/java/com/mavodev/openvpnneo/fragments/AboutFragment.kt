/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.fragments

import android.content.pm.PackageManager.NameNotFoundException
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.mavodev.openvpnneo.BuildConfig
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.core.NativeUtils
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.Locale

class AboutFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.about, container, false)
        val ver = v.findViewById<TextView>(R.id.version)

        var version: String
        var name = "Openvpn"
        try {
            val packageinfo = requireActivity().packageManager.getPackageInfo(requireActivity().packageName, 0)
            version = packageinfo.versionName ?: "unknown"
            name = getString(R.string.app)
        } catch (e: NameNotFoundException) {
            version = "error fetching version"
        }

        ver.text = getString(R.string.version_info, name, version)

        val verO2 = v.findViewById<TextView>(R.id.version_ovpn2)
        val verO3 = v.findViewById<TextView>(R.id.version_ovpn3)
        val osslVer = v.findViewById<TextView>(R.id.openssl_version)

        verO2.text = String.format(Locale.US, "OpenVPN version: %s", NativeUtils.getOpenVPN2GitVersion())
        if (BuildConfig.openvpn3) {
            verO3.text = String.format(Locale.US, "OpenVPN3 core version: %s", NativeUtils.getOpenVPN3GitVersion())
        } else {
            verO3.text = "(OpenVPN 2.x only build. No OpenVPN 3.x core in this app)"
        }

        osslVer.text = String.format(Locale.US, "OpenSSL version: %s", NativeUtils.getOpenSSLVersion())

        val wv = v.findViewById<TextView>(R.id.full_licenses)
        wv.text = HtmlCompat.fromHtml(readHtmlFromAssets(), HtmlCompat.FROM_HTML_MODE_LEGACY)

        ViewCompat.setOnApplyWindowInsetsListener(v) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        return v
    }

    private fun readHtmlFromAssets(): String {
        return try {
            requireActivity().assets.open("full_licenses.html").use { mvpn ->
                BufferedReader(InputStreamReader(mvpn, Charsets.UTF_8)).use { reader ->
                    val sb = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line).append("\n")
                    }
                    sb.toString()
                }
            }
        } catch (errabi: IOException) {
            "full_licenses.html not found"
        }
    }
}
