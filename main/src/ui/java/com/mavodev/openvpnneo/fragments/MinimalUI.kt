/*
 * Copyright (c) 2012-2025 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package com.mavodev.openvpnneo.fragments

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.security.KeyChain
import android.net.Uri
import android.provider.DocumentsContract
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.Button
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat.invalidateOptionsMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mavodev.openvpnneo.LaunchVPN
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.VpnProfile
import com.mavodev.openvpnneo.VpnProfile.TYPE_KEYSTORE
import com.mavodev.openvpnneo.VpnProfile.TYPE_USERPASS_KEYSTORE
import com.mavodev.openvpnneo.activities.BaseActivity
import com.mavodev.openvpnneo.activities.ConfigConverter
import com.mavodev.openvpnneo.activities.MainActivity
import com.mavodev.openvpnneo.core.ConnectionStatus
import com.mavodev.openvpnneo.core.GlobalPreferences
import com.mavodev.openvpnneo.core.IOpenVPNServiceInternal
import com.mavodev.openvpnneo.core.OpenVPNService
import com.mavodev.openvpnneo.core.ProfileManager
import com.mavodev.openvpnneo.core.VpnStatus
import com.mavodev.openvpnneo.fragments.ImportRemoteConfig.Companion.newInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MinimalUI: Fragment(), VpnStatus.StateListener {
    private var mLastConnectionLevel: ConnectionStatus = ConnectionStatus.LEVEL_NOTCONNECTED
    private var mPermReceiver: ActivityResultLauncher<String>? = null
    private lateinit var mFileImportReceiver: ActivityResultLauncher<Intent?>
    private lateinit var profileManger: ProfileManager
    private var mService: IOpenVPNServiceInternal? = null
    private lateinit var vpnstatus: TextView
    private lateinit var vpntoggle: CompoundButton
    private lateinit var importProfileButton: Button
    private lateinit var importAsButton: Button
    private lateinit var importButtonsLayout: LinearLayout

    private lateinit var view: View
    private var mImportMenuActive = false

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            className: ComponentName,
            service: IBinder
        ) {
            mService = IOpenVPNServiceInternal.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mService = null
        }
    }

    private fun registerPermissionReceiver() {
        mPermReceiver = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { result: Boolean? ->
            checkForNotificationPermission(
                requireView()
            )
        }
    }

    private fun registerStartFileImportReceiver()
    {
        Log.d("MinimalUI", "Registering file import receiver")
        mFileImportReceiver = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult())
        {
                result: ActivityResult ->
            Log.d("MinimalUI", "File picker result: $result")
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                Log.d("MinimalUI", "Selected URI: $uri")
                
                // Import any selected file directly
                if (uri != null) {
                    Log.d("MinimalUI", "Importing file: $uri")
                    val startImport = Intent(getActivity(), ConfigConverter::class.java)
                    startImport.setAction(ConfigConverter.IMPORT_PROFILE)
                    startImport.setData(uri)
                    startActivity(startImport)
                    
                    // Request focus back to import button after import dialog closes
                    importProfileButton.postDelayed({
                        importProfileButton.requestFocus()
                    }, 500)
                } else {
                    Log.e("MinimalUI", "No file selected")
                }
            } else {
                Log.d("MinimalUI", "File picker cancelled or failed. Result code: ${result.resultCode}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerPermissionReceiver()
        registerStartFileImportReceiver()
        setHasOptionsMenu(true)

        profileManger = ProfileManager.getInstance(requireContext());

    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        Log.d("MinimalUI", "onCreateOptionsMenu called")
        Log.d("MinimalUI", "AllowInitialImport: ${GlobalPreferences.getAllowInitialImport()}")
        val alwaysOnVPN = ProfileManager.getAlwaysOnVPN(requireContext())
        Log.d("MinimalUI", "AlwaysOnVPN: $alwaysOnVPN")
        
        if (GlobalPreferences.getAllowInitialImport() && alwaysOnVPN == null ) {
            Log.d("MinimalUI", "Import menu should be visible")
            mImportMenuActive = true
            // Show import buttons in layout for better accessibility
            importButtonsLayout.visibility = View.VISIBLE
            importProfileButton.requestFocus()  // Set initial focus for remote navigation
        } else {
            Log.d("MinimalUI", "Import menu hidden - AllowInitialImport: ${GlobalPreferences.getAllowInitialImport()}, AlwaysOnVPN: $alwaysOnVPN")
            // Hide import buttons in layout
            importButtonsLayout.visibility = View.GONE
        }
        
        // Always show VPN controls when there's a profile or when there's no profile
        if (alwaysOnVPN != null) {
            // Show VPN controls when no profile exists
            vpnstatus.visibility = View.VISIBLE
            vpntoggle.visibility = View.VISIBLE
            Log.d("MinimalUI", "VPN controls visible - no profile exists")
        } else {
            // Show VPN controls when profile exists
            vpnstatus.visibility = View.VISIBLE
            vpntoggle.visibility = View.VISIBLE
            Log.d("MinimalUI", "VPN controls visible - profile exists: $alwaysOnVPN")
        }
    }

    private fun startASProfileImport(): Boolean {
        val asImportFrag = newInstance(null)
        asImportFrag.show(getParentFragmentManager(), "dialog")
        invalidateOptionsMenu(activity)
        return true
    }

    override fun onResume() {
        super.onResume()
        VpnStatus.addStateListener(this)

        val intent = Intent(requireActivity(), OpenVPNService::class.java)
        intent.action = OpenVPNService.START_SERVICE
        requireActivity().bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
        if (mImportMenuActive && ProfileManager.getAlwaysOnVPN(requireActivity()) != null )
            invalidateOptionsMenu(requireActivity())
    }

    override fun onPause() {
        super.onPause()
        VpnStatus.removeStateListener(this)

        requireActivity().unbindService(mConnection)
    }

    private fun checkForNotificationPermission(v: View) {
        val permissionView = v.findViewById<View>(R.id.notification_permission) ?: return

        val permissionGranted =
            requireActivity().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        permissionView.setVisibility(if (permissionGranted) View.GONE else View.VISIBLE)
        permissionView.setOnClickListener({ view: View? ->
            mPermReceiver?.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        })

    }

    private suspend fun checkForKeychainPermission(v: View) {
        val keychainView = v.findViewById<View>(R.id.keychain_notification) ?: return

        val profile = ProfileManager.getAlwaysOnVPN(context)

        var permissionGranted = false
        withContext(Dispatchers.IO)
        {
            permissionGranted = (profile == null || !checkKeychainAccessIsMissing(profile))
        }


        keychainView.setVisibility(if (permissionGranted) View.GONE else View.VISIBLE)
        keychainView.setOnClickListener({

            try {
                KeyChain.choosePrivateKeyAlias(requireActivity(),
                    { alias ->
                        // Credential alias selected.  Remember the alias selection for future use.
                        profile.mAlias = alias
                        ProfileManager.saveProfile(context, profile)
                        viewLifecycleOwner.lifecycleScope.launch {
                            checkForKeychainPermission(v)
                        }
                    },
                    arrayOf("RSA", "EC"), null,
                    profile.mServerName,
                    -1,
                    profile.mAlias)
                // alias to preselect, null if unavailable
            } catch (anf: ActivityNotFoundException) {
                val ab = AlertDialog.Builder(activity)
                ab.setTitle(R.string.broken_image_cert_title)
                ab.setMessage(R.string.broken_image_cert)
                ab.setPositiveButton(android.R.string.ok, null)
                ab.show()
            }
        })
    }

    override fun updateState(
        state: String?,
        logmessage: String?,
        localizedResId: Int,
        level: ConnectionStatus,
        Intent: Intent?
    ) {
        val cleanLogMessage = VpnStatus.getLastCleanLogMessage(activity, true)

        requireActivity().runOnUiThread {
            vpnstatus.setText(cleanLogMessage)
            val connected = level == ConnectionStatus.LEVEL_CONNECTED;
            vpntoggle.isChecked = connected
        }
        mLastConnectionLevel = level;
    }

    override fun setConnectedVPN(uuid: String?) {
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        view = inflater.inflate(R.layout.minimalui, container, false)
        vpntoggle = view.findViewById(R.id.vpntoggle)
        importProfileButton = view.findViewById(R.id.import_profile_button)
        importAsButton = view.findViewById(R.id.import_as_button)
        importButtonsLayout = view.findViewById(R.id.import_buttons)
        vpnstatus = view.findViewById(R.id.vpnstatus)

        vpntoggle.setOnClickListener { view ->
            toggleSwitchPressed(view as CompoundButton)
        }
        
        importProfileButton.setOnClickListener {
            // Import profile from file
            Log.d("MinimalUI", "Import from file button clicked")
            // Simple file picker that should work with any file manager
            try {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "application/octet-stream"
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                Log.d("MinimalUI", "File picker intent: $intent")
                mFileImportReceiver.launch(intent)
            } catch (e: Exception) {
                Log.e("MinimalUI", "Error opening file picker: ${e.message}", e)
                Toast.makeText(requireContext(), "Unable to open file picker", Toast.LENGTH_LONG).show()
            }
        }
        
        importAsButton.setOnClickListener {
            // Import from remote URL
            startASProfileImport()
        }
        if ((activity as BaseActivity).isAndroidTV)
        {
            with( view.findViewById<TextView>(R.id.minimal_ui_title)) {
                setOnClickListener { _ -> toggleSwitchPressed(vpntoggle) }
                visibility = View.VISIBLE;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            checkForNotificationPermission(view)

        fun checkForKeychainPermission(view: View) {
            viewLifecycleOwner.lifecycleScope.launch {
                checkForKeychainPermission(view)
            }
        }
        view.setOnKeyListener { v, key, event ->
            Toast.makeText(activity, "Got key event " + event + " key " + key + " view " + v, Toast.LENGTH_LONG).show();
            false;
        }

        return view
    }

    fun checkKeychainAccessIsMissing(vp: VpnProfile): Boolean {
        if ((vp.mAuthenticationType != TYPE_USERPASS_KEYSTORE) && (vp.mAuthenticationType != TYPE_KEYSTORE)) {
            return false
        }

        if (TextUtils.isEmpty(vp.mAlias))
            return true
        val certs = vp.getExternalCertificates(context)
        if (certs == null)
            return true

        return false
    }

    suspend fun checkVpnConfigured(): VpnProfile? {
        val alwaysOnVPN = ProfileManager.getAlwaysOnVPN(requireContext())
        if (alwaysOnVPN == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    requireContext(),
                    R.string.cannot_start_vpn_not_configured,
                    Toast.LENGTH_SHORT
                ).show()
            }
            return null
        }

        if (checkKeychainAccessIsMissing(alwaysOnVPN))
        {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    requireContext(),
                    R.string.keychain_access,
                    Toast.LENGTH_SHORT
                ).show()
            }
            return null
        }
        return alwaysOnVPN
    }

    fun toggleSwitchPressed(view: CompoundButton) {
        viewLifecycleOwner.lifecycleScope.launch {toggleSwitchPressedReal(view) }
    }

    suspend fun toggleSwitchPressedReal(view: CompoundButton) {
        var alwaysOnVPN: VpnProfile? = null
        withContext(Dispatchers.IO) {
            alwaysOnVPN = checkVpnConfigured()
        }

        if (alwaysOnVPN == null)
        {
            view.setChecked(false)
            return
        }

        // Figure out if we should disconnect
        if (!GlobalPreferences.getForceConnected() && mLastConnectionLevel != ConnectionStatus.LEVEL_NOTCONNECTED) {
            ProfileManager.setConntectedVpnProfileDisconnected(requireContext())
            val service = mService;
            if (service != null) {
                try {
                    service.stopVPN(false)
                } catch (e: RemoteException) {
                    VpnStatus.logException(e)
                }
            }
            return
        }

        val intent = Intent(requireContext(), LaunchVPN::class.java)
        intent.putExtra(LaunchVPN.EXTRA_KEY, alwaysOnVPN.uuidString)
        intent.putExtra(OpenVPNService.EXTRA_START_REASON, "VPN started from homescreen.")
        intent.action = Intent.ACTION_MAIN
        startActivity(intent)
    }

    companion object {
    }
}