/*
 * Copyright (c) 2012-2019 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.fragments

import android.Manifest
import android.app.Activity
import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import androidx.core.graphics.drawable.DrawableCompat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.text.Html
import androidx.core.text.HtmlCompat
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.mavodev.openvpnneo.LaunchVPN
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.VpnProfile
import com.mavodev.openvpnneo.activities.ConfigConverter
import com.mavodev.openvpnneo.activities.DisconnectVPN
import com.mavodev.openvpnneo.activities.FileSelect
import com.mavodev.openvpnneo.activities.LogWindow
import com.mavodev.openvpnneo.activities.VPNPreferences
import com.mavodev.openvpnneo.activities.AboutActivity
import com.mavodev.openvpnneo.activities.FAQActivity
import com.mavodev.openvpnneo.activities.SettingsActivity
import com.mavodev.openvpnneo.activities.GraphActivity
import com.mavodev.openvpnneo.activities.OpenSSLSpeed
import com.mavodev.openvpnneo.core.ConnectionStatus
import com.mavodev.openvpnneo.core.OpenVPNService
import com.mavodev.openvpnneo.core.PasswordDialogFragment.Companion.newInstance
import com.mavodev.openvpnneo.core.Preferences
import com.mavodev.openvpnneo.core.ProfileManager
import com.mavodev.openvpnneo.core.VpnStatus
import com.mavodev.openvpnneo.core.VpnStatus.StateListener
import com.mavodev.openvpnneo.fragments.ImportRemoteConfig.Companion.newInstance
import com.mavodev.openvpnneo.fragments.Utils.alwaysUseOldFileChooser
import java.util.LinkedList
import java.util.TreeSet
import kotlin.math.min

class VPNProfileList : Fragment(), View.OnClickListener, StateListener, AddProfileBottomSheet.Listener,
    MainMenuBottomSheet.Listener {
    protected var mEditProfile: VpnProfile? = null
    private var mAdapter: ProfileAdapter? = null
    private var mRecyclerView: RecyclerView? = null
    private var mEmptyView: View? = null
    private var mLastIntent: Intent? = null
    private var defaultVPN: VpnProfile? = null
    private lateinit var mPermissionView: View
    private lateinit var mPermReceiver: ActivityResultLauncher<String>
    private lateinit var mEditVpnLauncher: ActivityResultLauncher<Intent>
    private lateinit var mSelectProfileLauncher: ActivityResultLauncher<Intent>
    private lateinit var mImportProfileLauncher: ActivityResultLauncher<Intent>
    private lateinit var mFilePickerLauncher: ActivityResultLauncher<Intent>
    private var currentConnectionLevel: ConnectionStatus = ConnectionStatus.LEVEL_NOTCONNECTED
    private var connectingProfileUUID: String? = null // Track profile being connected
    private var connectingState: String? = null // Latest OpenVPN state string while connecting
    private var highlightedUuids: Set<String> = emptySet()

    override fun updateState(
        state: String?,
        logmessage: String?,
        localizedResId: Int,
        level: ConnectionStatus?,
        intent: Intent?
    ) {
        // Store the current connection level
        currentConnectionLevel = level ?: ConnectionStatus.LEVEL_NOTCONNECTED
        connectingState = state
        
        // Detect when a new VPN connection is starting (from any source)
        if (level == ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET && connectingProfileUUID == null) {
            val currentVpnUUID = VpnStatus.getLastConnectedVPNProfile()
            if (currentVpnUUID != null) {
                Log.d("VPNProfileList", "New VPN connection detected, setting connectingProfileUUID: $currentVpnUUID")
                connectingProfileUUID = currentVpnUUID
            }
        }
        
        // Clear connectingProfileUUID when VPN successfully connects
        if (level == ConnectionStatus.LEVEL_CONNECTED && connectingProfileUUID != null) {
            Log.d("VPNProfileList", "VPN connected, clearing connectingProfileUUID: $connectingProfileUUID")
            connectingProfileUUID = null
            connectingState = null
        }
        
        // Clear connectingProfileUUID when VPN connection fails
        if ((level == ConnectionStatus.LEVEL_AUTH_FAILED || level == ConnectionStatus.LEVEL_NONETWORK) && connectingProfileUUID != null) {
            Log.d("VPNProfileList", "VPN connection failed, clearing connectingProfileUUID: $connectingProfileUUID")
            connectingProfileUUID = null
            connectingState = null
        }
        
        // Clear connectingProfileUUID when VPN disconnects
        if (level == ConnectionStatus.LEVEL_NOTCONNECTED && connectingProfileUUID != null) {
            Log.d("VPNProfileList", "VPN disconnected, clearing connectingProfileUUID: $connectingProfileUUID")
            connectingProfileUUID = null
            connectingState = null
        }
        
        requireActivity().runOnUiThread(Runnable {
            mLastIntent = intent
            refreshHighlightedRows()
            showUserRequestDialogIfNeeded(level, intent)
        })
    }

    private fun showUserRequestDialogIfNeeded(level: ConnectionStatus?, intent: Intent?): Boolean {
        if (level == ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT) {
            if (intent != null && intent.getStringExtra(OpenVPNService.EXTRA_CHALLENGE_TXT) != null) {
                val pwInputFrag = newInstance(intent, false)

                pwInputFrag!!.show(getParentFragmentManager(), "dialog")
                return true
            }
        }
        return false
    }

    override fun setConnectedVPN(uuid: String?) {
    }

    private fun startOrStopVPN(profile: VpnProfile) {
        if (VpnStatus.isVPNActive() && profile.getUUIDString() == VpnStatus.getLastConnectedVPNProfile()) {
            if (mLastIntent != null) {
                startActivity(mLastIntent!!)
            } else {
                val disconnectVPN = Intent(getActivity(), DisconnectVPN::class.java)
                startActivity(disconnectVPN)
            }
        } else {
            startVPN(profile)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mAdapter = ProfileAdapter()

        registerPermissionReceiver()
        registerActivityResultLaunchers()
    }

    private fun registerPermissionReceiver() {
        mPermReceiver = registerForActivityResult<String, Boolean>(
            RequestPermission(),
            ActivityResultCallback { result: Boolean? -> checkForNotificationPermission(requireView()) })
    }

    private fun registerActivityResultLaunchers() {
        mEditVpnLauncher = registerForActivityResult(StartActivityForResult()) { result ->
            val data = result.data
            when (result.resultCode) {
                RESULT_VPN_DELETED -> if (mEditProfile != null) populateVpnList()
                RESULT_VPN_DUPLICATE -> if (data != null) {
                    val profileUUID = data.getStringExtra(VpnProfile.EXTRA_PROFILEUUID)
                    val profile = ProfileManager.get(getActivity(), profileUUID)
                    if (profile != null) onAddOrDuplicateProfile(profile)
                }
                Activity.RESULT_OK -> {
                    val configuredVPN = data!!.getStringExtra(VpnProfile.EXTRA_PROFILEUUID)
                    val profile = ProfileManager.get(getActivity(), configuredVPN)
                    profile.addChangeLogEntry("Profile edited by user")
                    ProfileManager.saveProfile(getActivity(), profile)
                    // Name could be modified, refresh the list
                    populateVpnList()
                }
            }
        }

        mSelectProfileLauncher = registerForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val fileData = result.data!!.getStringExtra(FileSelect.RESULT_DATA)
            val uri = Uri.Builder().path(fileData).scheme("file").build()
            startConfigImport(uri)
        }

        mImportProfileLauncher = registerForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val profileUUID = result.data!!.getStringExtra(VpnProfile.EXTRA_PROFILEUUID)
            val importedProfile = ProfileManager.get(getActivity(), profileUUID)
            val isFirstProfile = (mAdapter?.itemCount ?: 0) == 0
            if (isFirstProfile && importedProfile != null) {
                Preferences.getDefaultSharedPreferences(requireContext()).edit()
                    .putString("alwaysOnVpn", importedProfile.getUUIDString()).apply()
            }
            populateVpnList()
        }

        mFilePickerLauncher = registerForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val data = result.data
            if (data != null) {
                val uri = data.getData()
                startConfigImport(uri)
            }
        }
    }

    fun updateDynamicShortcuts() {
        // ShortcutManager IPC calls (getDynamicShortcuts/updateShortcuts/removeDynamicShortcuts/
        // disableShortcuts) can block for several seconds, so run them off the main thread.
        val ctx = context?.applicationContext ?: return
        Thread { updateDynamicShortcutsBlocking(ctx) }.start()
    }

    private fun updateDynamicShortcutsBlocking(ctx: Context) {
        val versionExtras = PersistableBundle()
        versionExtras.putInt("version", SHORTCUT_VERSION)

        val shortcutManager =
            ctx.getSystemService<ShortcutManager>(ShortcutManager::class.java)
        if (shortcutManager.isRateLimitingActive()) return

        val shortcuts = shortcutManager.getDynamicShortcuts()
        var maxvpn = shortcutManager.getMaxShortcutCountPerActivity() - 1


        val disconnectShortcut = ShortcutInfo.Builder(ctx, "disconnectVPN")
            .setShortLabel(ctx.getString(R.string.cancel_connection))
            .setLongLabel(ctx.getString(R.string.cancel_connection_long))
            .setIntent(
                Intent(
                    ctx,
                    DisconnectVPN::class.java
                ).setAction(OpenVPNService.DISCONNECT_VPN)
            )
            .setIcon(Icon.createWithResource(ctx, R.drawable.ic_shortcut_cancel))
            .setExtras(versionExtras)
            .build()

        val newShortcuts = LinkedList<ShortcutInfo>()
        val updateShortcuts = LinkedList<ShortcutInfo>()

        val removeShortcuts = LinkedList<String>()
        val disableShortcuts = LinkedList<String>()

        var addDisconnect = true


        val sortedProfilesLRU = TreeSet<VpnProfile?>(VpnProfileLRUComparator())
        val profileManager = ProfileManager.getInstance(ctx)
        sortedProfilesLRU.addAll(profileManager.getProfiles())

        val LRUProfiles = LinkedList<VpnProfile>()
        maxvpn = min(maxvpn, sortedProfilesLRU.size)

        for (i in 0..<maxvpn) {
            LRUProfiles.add(sortedProfilesLRU.pollFirst()!!)
        }

        for (shortcut in shortcuts) {
            if (shortcut.getId() == "disconnectVPN") {
                addDisconnect = false
                if (shortcut.getExtras() == null
                    || shortcut.getExtras()!!.getInt("version") != SHORTCUT_VERSION
                ) updateShortcuts.add(disconnectShortcut)
            } else {
                val p = ProfileManager.get(ctx, shortcut.getId())
                if (p == null || p.profileDeleted) {
                    if (shortcut.isEnabled()) {
                        disableShortcuts.add(shortcut.getId())
                        removeShortcuts.add(shortcut.getId())
                    }
                    if (!shortcut.isPinned()) removeShortcuts.add(shortcut.getId())
                } else {
                    if (LRUProfiles.contains(p)) LRUProfiles.remove(p)
                    else removeShortcuts.add(p.getUUIDString())

                    if ((p.getName() != shortcut.getShortLabel()) || shortcut.getExtras() == null || shortcut.getExtras()!!
                            .getInt("version") != SHORTCUT_VERSION
                    ) updateShortcuts.add(createShortcut(ctx, p))
                }
            }
        }
        if (addDisconnect) newShortcuts.add(disconnectShortcut)
        for (p in LRUProfiles) newShortcuts.add(createShortcut(ctx, p))

        if (updateShortcuts.size > 0) shortcutManager.updateShortcuts(updateShortcuts)
        if (removeShortcuts.size > 0) shortcutManager.removeDynamicShortcuts(removeShortcuts)
        if (newShortcuts.size > 0) shortcutManager.addDynamicShortcuts(newShortcuts)
        if (disableShortcuts.size > 0) shortcutManager.disableShortcuts(
            disableShortcuts,
            "VpnProfile does not exist anymore."
        )
    }

    fun createShortcut(ctx: Context, profile: VpnProfile): ShortcutInfo {
        val shortcutIntent = Intent(Intent.ACTION_MAIN)
        shortcutIntent.setClass(ctx, LaunchVPN::class.java)
        shortcutIntent.putExtra(LaunchVPN.EXTRA_KEY, profile.getUUID().toString())
        shortcutIntent.setAction(Intent.ACTION_MAIN)
        shortcutIntent.putExtra(OpenVPNService.EXTRA_START_REASON, "shortcut")
        shortcutIntent.putExtra("EXTRA_HIDELOG", true)

        val versionExtras = PersistableBundle()
        versionExtras.putInt("version", SHORTCUT_VERSION)

        return ShortcutInfo.Builder(ctx, profile.getUUIDString())
            .setShortLabel(profile.getName())
            .setLongLabel(ctx.getString(R.string.qs_connect, profile.getName()))
            .setIcon(Icon.createWithResource(ctx, R.drawable.ic_shortcut_vpn_key))
            .setIntent(shortcutIntent)
            .setExtras(versionExtras)
            .build()
    }

    override fun onResume() {
        super.onResume()
        updateDefaultVpn()
        populateVpnList()
        updateDynamicShortcuts()
        VpnStatus.addStateListener(this)
    }

    /** Re-read the default profile and rebind the old + new default rows so the star moves. */
    private fun updateDefaultVpn() {
        val oldUuid = defaultVPN?.getUUIDString()
        defaultVPN = ProfileManager.getAlwaysOnVPN(requireContext())
        val newUuid = defaultVPN?.getUUIDString()
        if (oldUuid == newUuid) return
        val adapter = mAdapter ?: return
        for (uuid in listOfNotNull(oldUuid, newUuid)) {
            val idx = adapter.currentList.indexOfFirst { it.getUUIDString() == uuid }
            if (idx >= 0) adapter.notifyItemChanged(idx)
        }
    }

    override fun onPause() {
        super.onPause()
        VpnStatus.removeStateListener(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.vpn_profile_list, container, false)

        mRecyclerView = v.findViewById(R.id.vpn_list)
        mEmptyView = v.findViewById(R.id.empty_view)
        mRecyclerView?.layoutManager = LinearLayoutManager(requireContext())
        mRecyclerView?.adapter = mAdapter

        val newvpntext = v.findViewById<View?>(R.id.add_new_vpn_hint) as TextView
        
        // Set up floating add profile button
        val fabAddProfile = v.findViewById<ImageButton>(R.id.fab_add_profile)
        fabAddProfile.setOnClickListener {
            val sheet = AddProfileBottomSheet()
            sheet.listener = this
            sheet.show(parentFragmentManager, AddProfileBottomSheet.TAG)
        }

        // Set up floating main menu button
        val fabMainMenu = v.findViewById<ImageButton>(R.id.fab_main_menu)
        fabMainMenu.setOnClickListener {
            val sheet = MainMenuBottomSheet()
            sheet.listener = this
            sheet.show(parentFragmentManager, MainMenuBottomSheet.TAG)
        }

        newvpntext.setText(
            HtmlCompat.fromHtml(
                getString(R.string.add_new_vpn_hint),
                HtmlCompat.FROM_HTML_MODE_LEGACY,
                MiniImageGetter(),
                null
            )
        )

        val fab_add = v.findViewById<View?>(R.id.fab_add) as ImageButton?
        val fab_import = v.findViewById<View?>(R.id.fab_import) as ImageButton?
        if (fab_add != null) fab_add.setOnClickListener(this)

        if (fab_import != null) fab_import.setOnClickListener(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            checkForNotificationPermission(v)

        defaultVPN = ProfileManager.getAlwaysOnVPN(requireContext())
        populateVpnList()

        return v
    }

    private fun checkForNotificationPermission(v: View) {
        mPermissionView = v.findViewById<View>(R.id.notification_permission)
        val permissionGranted =
            (requireActivity().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        mPermissionView.setVisibility(if (permissionGranted) View.GONE else View.VISIBLE)

        mPermissionView.setOnClickListener(View.OnClickListener { view: View? ->
            mPermReceiver.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        })
    }

    private fun populateVpnList() {
        val sortByLRU = Preferences.getDefaultSharedPreferences(requireActivity()).getBoolean(
            PREF_SORT_BY_LRU, false
        )
        this.pM.refreshVPNList(requireContext())
        val allvpn: MutableCollection<VpnProfile?>? = this.pM.getProfiles()
        val sortedset: TreeSet<VpnProfile?> =
            if (sortByLRU) TreeSet(VpnProfileLRUComparator())
            else TreeSet(VpnProfileNameComparator())

        sortedset.addAll(allvpn!!)
        val list = sortedset.filterNotNull().toList()
        mAdapter?.submitList(list) { updateEmptyView(list.isEmpty()) }
    }

    private fun updateEmptyView(isEmpty: Boolean) {
        mEmptyView?.visibility = if (isEmpty) View.VISIBLE else View.GONE
        mRecyclerView?.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    /** Refresh only the rows whose highlight (connecting/connected border) may have changed. */
    private fun refreshHighlightedRows() {
        val adapter = mAdapter ?: return
        val connectedUuid =
            if (currentConnectionLevel == ConnectionStatus.LEVEL_CONNECTED)
                VpnStatus.getLastConnectedVPNProfile() else null
        val newHighlights = setOfNotNull(connectingProfileUUID, connectedUuid)
        val toRefresh = highlightedUuids + newHighlights
        highlightedUuids = newHighlights
        for (uuid in toRefresh) {
            val idx = adapter.currentList.indexOfFirst { it.getUUIDString() == uuid }
            if (idx >= 0) adapter.notifyItemChanged(idx)
        }
    }

    // MainMenuBottomSheet.Listener
    override fun onMenuChangeSorting() {
        changeSorting()
    }

    override fun onMenuSettings() {
        startActivity(Intent(getActivity(), SettingsActivity::class.java as Class<SettingsActivity>))
    }

    override fun onMenuShowLog() {
        startActivity(Intent(getActivity(), LogWindow::class.java as Class<LogWindow>))
    }

    override fun onMenuGraph() {
        startActivity(Intent(getActivity(), GraphActivity::class.java as Class<GraphActivity>))
    }

    override fun onMenuOpenSSLSpeed() {
        startActivity(Intent(getActivity(), OpenSSLSpeed::class.java))
    }

    override fun onMenuFAQ() {
        startActivity(Intent(getActivity(), FAQActivity::class.java as Class<FAQActivity>))
    }

    override fun onMenuAbout() {
        startActivity(Intent(getActivity(), AboutActivity::class.java as Class<AboutActivity>))
    }

    private fun startASProfileImport(): Boolean {
        val asImportFrag = newInstance(null)
        asImportFrag.show(getParentFragmentManager(), "dialog")
        return true
    }

    // AddProfileBottomSheet.Listener
    override fun onCreateManually() {
        onAddOrDuplicateProfile(null)
    }

    override fun onImportFromFile() {
        startImportConfigFilePicker()
    }

    override fun onImportFromRemote() {
        startASProfileImport()
    }

    private fun changeSorting(): Boolean {
        val prefs = Preferences.getDefaultSharedPreferences(requireActivity())
        val oldValue = prefs.getBoolean(PREF_SORT_BY_LRU, false)
        val prefsedit = prefs.edit()
        if (oldValue) {
            Toast.makeText(getActivity(), R.string.sorted_az, Toast.LENGTH_SHORT).show()
            prefsedit.putBoolean(PREF_SORT_BY_LRU, false)
        } else {
            prefsedit.putBoolean(PREF_SORT_BY_LRU, true)
            Toast.makeText(getActivity(), R.string.sorted_lru, Toast.LENGTH_SHORT).show()
        }
        prefsedit.apply()
        populateVpnList()
        return true
    }

    override fun onClick(v: View) {
        when (v.getId()) {
            R.id.fab_import -> startImportConfigFilePicker()
            R.id.fab_add -> onAddOrDuplicateProfile(null)
        }
    }

    private fun startImportConfigFilePicker(): Boolean {
        var startOldFileDialog = true
        if (!alwaysUseOldFileChooser(getActivity())) startOldFileDialog = !startFilePicker()

        if (startOldFileDialog) startImportConfig()

        return true
    }

    private fun startFilePicker(): Boolean {
        val i = Utils.getFilePickerIntent(getActivity()!!, Utils.FileType.OVPN_CONFIG)
        if (i != null) {
            mFilePickerLauncher.launch(i)
            return true
        } else return false
    }

    private fun startImportConfig() {
        val intent = Intent(getActivity(), FileSelect::class.java)
        intent.putExtra(FileSelect.NO_INLINE_SELECTION, true)
        intent.putExtra(FileSelect.WINDOW_TITLE, R.string.import_configuration_file)
        mSelectProfileLauncher.launch(intent)
    }

    private fun onAddOrDuplicateProfile(mCopyProfile: VpnProfile?) {
        val context: Context? = getActivity()
        if (context != null) {
            val entry = EditText(context)
            entry.setSingleLine()
            entry.setContentDescription(getString(R.string.name_of_the_vpn_profile))

            val dialog = AlertDialog.Builder(context)
            if (mCopyProfile == null) dialog.setTitle(R.string.menu_add_profile)
            else {
                dialog.setTitle(
                    context.getString(
                        R.string.duplicate_profile_title,
                        mCopyProfile.mName
                    )
                )
                entry.setText(getString(R.string.copy_of_profile, mCopyProfile.mName))
            }

            dialog.setMessage(R.string.add_profile_name_prompt)
            dialog.setView(entry)

            dialog.setPositiveButton(
                android.R.string.ok
            ) { dialog12: DialogInterface?, which: Int ->
                val name = entry.getText().toString()
                if (this.pM.getProfileByName(name) == null) {
                    val profile: VpnProfile
                    if (mCopyProfile != null) {
                        profile = mCopyProfile.copy(name)
                        // Remove restrictions on copy profile
                        profile.mProfileCreator = null
                        profile.mUserEditable = true
                    } else profile = VpnProfile(name)

                    addProfile(profile)
                    editVPN(profile)
                } else {
                    Toast.makeText(
                        getActivity(),
                        R.string.duplicate_profile_name,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            dialog.setNegativeButton(android.R.string.cancel, null)
            dialog.create().show()
        }
    }

    private fun addProfile(profile: VpnProfile) {
        val isFirstProfile = pM.getProfiles().isEmpty()
        this.pM.addProfile(profile)
        this.pM.saveProfileList(getActivity())
        profile.addChangeLogEntry("empty profile added via main profile list")
        ProfileManager.saveProfile(getActivity(), profile)
        if (isFirstProfile) {
            Preferences.getDefaultSharedPreferences(requireContext()).edit()
                .putString("alwaysOnVpn", profile.getUUIDString()).apply()
        }
        populateVpnList()
    }

    private val pM: ProfileManager
        get() = ProfileManager.getInstance(getActivity())

    private fun startConfigImport(uri: Uri?) {
        val startImport = Intent(getActivity(), ConfigConverter::class.java)
        startImport.setAction(ConfigConverter.IMPORT_PROFILE)
        startImport.setData(uri)
        mImportProfileLauncher.launch(startImport)
    }

    private fun editVPN(profile: VpnProfile) {
        mEditProfile = profile
        val vprefintent = Intent(getActivity(), VPNPreferences::class.java)
            .putExtra(
                getActivity()!!.getPackageName() + ".profileUUID",
                profile.getUUID().toString()
            )

        mEditVpnLauncher.launch(vprefintent)
    }

    // Simple method to stop all animations (called from MainActivity)
    fun stopAllAnimations() {
        Log.d("VPNProfileList", "stopAllAnimations() called - clearing connectingProfileUUID")
        // Only clear if we had a connecting profile that needs to be stopped
        if (connectingProfileUUID != null) {
            Log.d("VPNProfileList", "Had connecting profile, clearing animation")
            connectingProfileUUID = null
            refreshHighlightedRows()
        } else {
            Log.d("VPNProfileList", "No connecting profile to clear")
        }
    }
    
    private fun startVPN(profile: VpnProfile) {
        ProfileManager.saveProfile(getActivity(), profile)
        
        // Start progress indicator immediately when user clicks
        Log.d("VPNProfileList", "startVPN called for profile: " + profile.getUUIDString() + " - starting progress")
        connectingProfileUUID = profile.getUUIDString()
        connectingState = null
        refreshHighlightedRows()

        val intent = Intent(getActivity(), LaunchVPN::class.java)
        intent.putExtra(LaunchVPN.EXTRA_KEY, profile.getUUID().toString())
        intent.putExtra(OpenVPNService.EXTRA_START_REASON, "main profile list")
        intent.setAction(Intent.ACTION_MAIN)
        startActivity(intent)
    }

    internal class VpnProfileNameComparator : Comparator<VpnProfile?> {
        override fun compare(lhs: VpnProfile?, rhs: VpnProfile?): Int {
            if (lhs === rhs)  // Catches also both null
                return 0

            if (lhs == null) return -1
            if (rhs == null) return 1

            if (lhs.mName == null) return -1
            if (rhs.mName == null) return 1

            return lhs.mName.compareTo(rhs.mName)
        }
    }

    internal class VpnProfileLRUComparator : Comparator<VpnProfile?> {
        var nameComparator: VpnProfileNameComparator = VpnProfileNameComparator()

        override fun compare(lhs: VpnProfile?, rhs: VpnProfile?): Int {
            if (lhs === rhs)  // Catches also both null
                return 0

            if (lhs == null) return -1
            if (rhs == null) return 1

            // Copied from Long.compare
            if (lhs.mLastUsed > rhs.mLastUsed) return -1
            if (lhs.mLastUsed < rhs.mLastUsed) return 1
            else return nameComparator.compare(lhs, rhs)
        }
    }

    private inner class ProfileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.vpn_item_title)
        val row: View = view.findViewById(R.id.vpn_list_item_left)
        val settings: View = view.findViewById(R.id.quickedit_settings)
        val flag: ImageView = view.findViewById(R.id.vpn_item_country_flag)
        val star: ImageView = view.findViewById(R.id.vpn_item_default_star)
        val container: View = view.findViewById(R.id.vpn_item_container)
        val progress: LinearProgressIndicator = view.findViewById(R.id.vpn_item_progress)
    }

    private inner class ProfileAdapter :
        ListAdapter<VpnProfile, ProfileViewHolder>(PROFILE_DIFF) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
            val v = layoutInflater.inflate(R.layout.vpn_list_item, parent, false)
            return ProfileViewHolder(v)
        }

        override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
            val profile = getItem(position)

            holder.title.text = profile.getName()
            holder.row.setOnClickListener { startOrStopVPN(profile) }
            holder.settings.setOnClickListener { editVPN(profile) }

            // Load country flag for this profile
            loadProfileCountryFlag(profile, holder.flag)

            val uuid = profile.getUUIDString()
            // Show/hide default profile star
            holder.star.visibility =
                if (uuid == defaultVPN?.getUUIDString()) View.VISIBLE else View.GONE

            val isConnecting = uuid == connectingProfileUUID
            val isConnected = uuid == VpnStatus.getLastConnectedVPNProfile() &&
                currentConnectionLevel == ConnectionStatus.LEVEL_CONNECTED

            if (isConnecting) {
                // Show a determinate progress bar that advances through the connection steps
                holder.container.background = null
                val wasVisible = holder.progress.visibility == View.VISIBLE
                holder.progress.visibility = View.VISIBLE
                holder.progress.setProgressCompat(progressForState(connectingState), wasVisible)
            } else {
                holder.progress.visibility = View.GONE
                if (isConnected) {
                    // VPN is connected - show solid border
                    holder.container.setBackgroundResource(R.drawable.vpn_item_border)
                } else {
                    // Not connecting and not connected - no border
                    holder.container.background = null
                }
            }
        }
    }

    /** Map an OpenVPN management state to a coarse connection progress (0-100). */
    private fun progressForState(state: String?): Int = when (state) {
        "VPN_GENERATE_CONFIG" -> 5
        "CONNECTING" -> 15
        "RESOLVE" -> 25
        "TCP_CONNECT" -> 40
        "WAIT" -> 50
        "AUTH", "AUTH_PENDING" -> 65
        "GET_CONFIG" -> 75
        "ASSIGN_IP" -> 85
        "ADD_ROUTES" -> 92
        "CONNECTED" -> 100
        else -> 10
    }

    internal inner class MiniImageGetter : Html.ImageGetter {
        override fun getDrawable(source: String?): Drawable? {
            var d: Drawable? = null
            if ("ic_menu_add" == source) d = requireActivity().getResources()
                .getDrawable(R.drawable.ic_menu_add_grey, requireActivity().getTheme())
            else if ("ic_menu_archive" == source) d = requireActivity().getResources()
                .getDrawable(R.drawable.ic_menu_import_grey, requireActivity().getTheme())


            if (d != null) {
                val color = requireContext().getColor(R.color.text_primary)
                DrawableCompat.setTint(d.mutate(), color)
                d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight())
                return d
            } else {
                return null
            }
        }
    }

    companion object {
        val RESULT_VPN_DELETED: Int = Activity.RESULT_FIRST_USER
        val RESULT_VPN_DUPLICATE: Int = Activity.RESULT_FIRST_USER + 1

        // Shortcut version is increased to refresh all shortcuts
        const val SHORTCUT_VERSION: Int = 1
        private val MENU_ADD_PROFILE = Menu.FIRST
        private val MENU_IMPORT_PROFILE = Menu.FIRST + 1
        private val MENU_IMPORT_AS = Menu.FIRST + 3
        private const val PREF_SORT_BY_LRU = "sortProfilesByLRU"

        private val PROFILE_DIFF = object : DiffUtil.ItemCallback<VpnProfile>() {
            override fun areItemsTheSame(oldItem: VpnProfile, newItem: VpnProfile): Boolean =
                oldItem.getUUIDString() == newItem.getUUIDString()

            override fun areContentsTheSame(oldItem: VpnProfile, newItem: VpnProfile): Boolean =
                oldItem.getUUIDString() == newItem.getUUIDString() &&
                    oldItem.getName() == newItem.getName() &&
                    oldItem.mVersion == newItem.mVersion
        }
    }
    
    private fun loadProfileCountryFlag(profile: VpnProfile, flagImageView: ImageView) {
        val prefs = requireContext().getSharedPreferences("profile_countries", Context.MODE_PRIVATE)
        val countryCode = prefs.getString(profile.getUUIDString(), null)
        
        Log.d("VPNProfileList", "Loading flag for profile ${profile.getUUIDString()}, country: $countryCode")
        
        if (countryCode != null) {
            // Load flag for stored country
            try {
                val flagResourceName = "flag_${countryCode.lowercase()}"
                val resourceId = resources.getIdentifier(flagResourceName, "drawable", requireContext().packageName)
                
                Log.d("VPNProfileList", "Looking for flag resource: $flagResourceName, ID: $resourceId")
                
                if (resourceId != 0) {
                    flagImageView.setImageResource(resourceId)
                    Log.d("VPNProfileList", "Loaded flag for $countryCode")
                } else {
                    flagImageView.setImageResource(R.drawable.flag_unknown)
                    Log.d("VPNProfileList", "Flag not found for $countryCode, using placeholder")
                }
            } catch (e: Exception) {
                Log.e("VPNProfileList", "Error loading flag for $countryCode", e)
                flagImageView.setImageResource(R.drawable.flag_unknown)
            }
        } else {
            // Show placeholder flag
            flagImageView.setImageResource(R.drawable.flag_unknown)
            Log.d("VPNProfileList", "No country stored for profile ${profile.getUUIDString()}, using placeholder")
        }
    }
    
    private fun saveProfileCountry(profileUUID: String, countryCode: String) {
        val prefs = requireContext().getSharedPreferences("profile_countries", Context.MODE_PRIVATE)
        Log.d("VPNProfileList", "Saving country $countryCode for profile $profileUUID")
        prefs.edit().putString(profileUUID, countryCode).apply()
    }
    
    fun refreshFlags() {
        Log.d("VPNProfileList", "refreshFlags() called - updating all profile flags")
        mAdapter?.notifyDataSetChanged()
    }
}
