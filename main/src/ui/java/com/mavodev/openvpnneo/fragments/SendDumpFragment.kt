/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.fragments

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.net.Uri
import android.os.Bundle
import android.util.Pair
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.core.VpnStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

class SendDumpFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.fragment_senddump, container, false)
        v.findViewById<View>(R.id.senddump).setOnClickListener { emailMiniDumps() }

        viewLifecycleOwner.lifecycleScope.launch {
            // Do in background since it does I/O
            val ldump = withContext(Dispatchers.IO) { getLastestDump(requireContext()) } ?: return@launch
            val dumpDateText = v.findViewById<TextView>(R.id.dumpdate)
            val datestr = Date(ldump.second).toString()
            val timediff = System.currentTimeMillis() - ldump.second
            val minutes = timediff / 1000 / 60 % 60
            val hours = timediff / 1000 / 60 / 60
            dumpDateText.text = getString(R.string.lastdumpdate, hours, minutes, datestr)
        }
        return v
    }

    fun emailMiniDumps() {
        //need to "send multiple" to get more than one attachment
        val emailIntent = Intent(Intent.ACTION_SEND_MULTIPLE)
        emailIntent.type = "*/*"
        emailIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf("Arne Schwabe <arne@rfc2549.org>"))

        var version: String
        var name = "ics-openvpn"
        try {
            val packageinfo = requireActivity().packageManager.getPackageInfo(requireActivity().packageName, 0)
            version = packageinfo.versionName ?: "unknown"
            name = packageinfo.applicationInfo?.name ?: name
        } catch (e: NameNotFoundException) {
            version = "error fetching version"
        }

        emailIntent.putExtra(
            Intent.EXTRA_SUBJECT,
            String.format("%s(%s) %s Minidump", name, requireActivity().packageName, version)
        )
        emailIntent.putExtra(Intent.EXTRA_TEXT, "Please describe the issue you have experienced")

        val uris = ArrayList<Uri>()

        val ldump = getLastestDump(requireContext())
        if (ldump == null) {
            VpnStatus.logError("No Minidump found!")
            return
        }

        val authority = requireContext().packageName + ".FileProvider"
        uris.add(Uri.parse("content://$authority/" + ldump.first.name))
        uris.add(Uri.parse("content://$authority/" + ldump.first.name + ".log"))

        emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        startActivity(emailIntent)
    }

    companion object {
        @JvmStatic
        fun getLastestDump(c: Context): Pair<File, Long>? {
            var newestDumpTime = 0L
            var newestDumpFile: File? = null

            val cacheDir = c.cacheDir ?: return null
            val filesList = cacheDir.listFiles() ?: return null

            for (f in filesList) {
                if (!f.name.endsWith(".dmp")) continue

                if (newestDumpTime < f.lastModified()) {
                    newestDumpTime = f.lastModified()
                    newestDumpFile = f
                }
            }
            // Ignore old dumps
            if (System.currentTimeMillis() - 48 * 60 * 1000 > newestDumpTime) return null

            return Pair.create(newestDumpFile!!, newestDumpTime)
        }
    }
}
