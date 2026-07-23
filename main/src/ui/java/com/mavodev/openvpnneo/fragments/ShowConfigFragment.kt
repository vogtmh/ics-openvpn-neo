/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.VpnProfile
import com.mavodev.openvpnneo.core.ProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShowConfigFragment : Fragment() {
    private var configtext: String? = null
    private lateinit var mConfigView: TextView
    private var mfabButton: ImageButton? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.viewconfig, container, false)
        mConfigView = v.findViewById(R.id.configview)

        mfabButton = v.findViewById(R.id.share_config)
        mfabButton?.let {
            it.setOnClickListener { shareConfig() }
            it.visibility = View.INVISIBLE
        }
        return v
    }

    private fun startGenConfig(vp: VpnProfile, cv: TextView) {
        viewLifecycleOwner.lifecycleScope.launch {
            /* Add a few newlines to make the textview scrollable past the FAB */
            val text = withContext(Dispatchers.IO) {
                try {
                    vp.getConfigFile(requireContext(), VpnProfile.doUseOpenVPN3(requireContext())) + "\n\n\n"
                } catch (e: Exception) {
                    e.printStackTrace()
                    "Error generating config file: " + e.localizedMessage
                }
            }
            configtext = text
            cv.text = text
            mfabButton?.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
    }

    private fun shareConfig() {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.putExtra(Intent.EXTRA_TEXT, configtext)
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_config_title))
        shareIntent.type = "text/plain"
        startActivity(Intent.createChooser(shareIntent, getString(R.string.export_config_chooser_title)))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.sendConfig) {
            shareConfig()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        populateConfigText()
    }

    private fun populateConfigText() {
        val profileUUID = requireArguments().getString(requireActivity().packageName + ".profileUUID")
        val vp = ProfileManager.get(requireActivity(), profileUUID)
        val check = vp.checkProfile(requireActivity())

        if (check != R.string.no_error_found) {
            mConfigView.setText(check)
            configtext = getString(check)
        } else {
            // Run in own Thread since Keystore does not like to be queried from the main thread
            mConfigView.setText(R.string.generating_config)
            startGenConfig(vp, mConfigView)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun setUserVisibleHint(visible: Boolean) {
        @Suppress("DEPRECATION")
        super.setUserVisibleHint(visible)
        if (visible && isResumed) {
            populateConfigText()
        }
    }
}
