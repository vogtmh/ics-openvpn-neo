/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.fragments

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.database.DataSetObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.preference.PreferenceManager
import android.text.SpannableString
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.ListAdapter
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.ListFragment
import androidx.lifecycle.lifecycleScope
import com.mavodev.openvpnneo.LaunchVPN
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.VpnProfile
import com.mavodev.openvpnneo.activities.DisconnectVPN
import com.mavodev.openvpnneo.activities.VPNPreferences
import com.mavodev.openvpnneo.core.ConnectionStatus
import com.mavodev.openvpnneo.core.LogItem
import com.mavodev.openvpnneo.core.OpenVPNManagement
import com.mavodev.openvpnneo.core.OpenVPNService
import com.mavodev.openvpnneo.core.OpenVPNService.humanReadableByteCount
import com.mavodev.openvpnneo.core.Preferences
import com.mavodev.openvpnneo.core.ProfileManager
import com.mavodev.openvpnneo.core.VpnStatus
import com.mavodev.openvpnneo.core.VpnStatus.LogListener
import com.mavodev.openvpnneo.core.VpnStatus.StateListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.Vector

class LogFragment : ListFragment(), StateListener, SeekBar.OnSeekBarChangeListener,
    RadioGroup.OnCheckedChangeListener, VpnStatus.ByteCountListener {

    private lateinit var mLogLevelSlider: SeekBar
    private var mOptionsLayout: LinearLayout? = null
    private lateinit var mTimeRadioGroup: RadioGroup
    private var mUpStatus: TextView? = null
    private var mDownStatus: TextView? = null
    private var mConnectStatus: TextView? = null
    private var mStartPendingIntent: TextView? = null
    private var mShowOptionsLayout = false
    private lateinit var mClearLogCheckBox: CompoundButton
    private var mPendingIntent: Intent? = null

    private lateinit var ladapter: LogWindowListAdapter
    private var mSpeedView: TextView? = null

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        ladapter.setLogLevel(progress + 1)
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {}

    override fun onStopTrackingTouch(seekBar: SeekBar?) {}

    override fun onCheckedChanged(group: RadioGroup, checkedId: Int) {
        when (checkedId) {
            R.id.radioISO -> ladapter.setTimeFormat(TIME_FORMAT_ISO)
            R.id.radioNone -> ladapter.setTimeFormat(TIME_FORMAT_NONE)
            R.id.radioShort -> ladapter.setTimeFormat(TIME_FORMAT_SHORT)
        }
    }

    override fun updateByteCount(bytesIn: Long, bytesOut: Long, diffIn: Long, diffOut: Long) {
        //%2$s/s %1$s - ↑%4$s/s %3$s
        val res = requireActivity().resources
        val down = String.format(
            "%2\$s %1\$s",
            humanReadableByteCount(bytesIn, false, res),
            humanReadableByteCount(diffIn / OpenVPNManagement.mBytecountInterval, true, res)
        )
        val up = String.format(
            "%2\$s %1\$s",
            humanReadableByteCount(bytesOut, false, res),
            humanReadableByteCount(diffOut / OpenVPNManagement.mBytecountInterval, true, res)
        )

        if (mUpStatus != null && mDownStatus != null) {
            activity?.runOnUiThread {
                mUpStatus?.text = up
                mDownStatus?.text = down
            }
        }
    }

    inner class LogWindowListAdapter : ListAdapter, LogListener, Handler.Callback {

        private var allEntries = Vector<LogItem>()
        private val currentLevelEntries = Vector<LogItem>()
        private val mHandler: Handler
        private val observers = Vector<DataSetObserver>()

        var mTimeFormat = 0
        var mLogLevel = 3

        init {
            initLogBuffer()
            mHandler = Handler(Looper.getMainLooper(), this)
            VpnStatus.addLogListener(this)
        }

        private fun initLogBuffer() {
            allEntries.clear()
            Collections.addAll(allEntries, *VpnStatus.getlogbuffer())
            initCurrentMessages()
        }

        fun getLogStr(): String {
            var str = ""
            for (entry in allEntries) {
                str += getTime(entry, TIME_FORMAT_ISO) + entry.getString(requireActivity()) + '\n'
            }
            return str
        }

        fun shareLog() {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.putExtra(Intent.EXTRA_TEXT, getLogStr())
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.ics_openvpn_log_file))
            shareIntent.type = "text/plain"
            startActivity(Intent.createChooser(shareIntent, "Send Logfile"))
        }

        override fun registerDataSetObserver(observer: DataSetObserver) {
            observers.add(observer)
        }

        override fun unregisterDataSetObserver(observer: DataSetObserver) {
            observers.remove(observer)
        }

        override fun getCount(): Int = currentLevelEntries.size

        override fun getItem(position: Int): Any = currentLevelEntries[position]

        override fun getItemId(position: Int): Long = (currentLevelEntries[position] as Any).hashCode().toLong()

        override fun hasStableIds(): Boolean = true

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val v: TextView = if (convertView == null) TextView(activity) else convertView as TextView

            val le = currentLevelEntries[position]
            val msg = le.getString(requireActivity())
            val time = getTime(le, mTimeFormat)
            val full = time + msg

            val t = SpannableString(full)
            v.text = t
            return v
        }

        private fun getTime(le: LogItem, time: Int): String {
            return if (time != TIME_FORMAT_NONE) {
                val d = Date(le.logtime)
                val timeformat: java.text.DateFormat = if (time == TIME_FORMAT_ISO) {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                } else {
                    DateFormat.getTimeFormat(requireActivity())
                }
                timeformat.format(d) + " "
            } else {
                ""
            }
        }

        override fun getItemViewType(position: Int): Int = 0

        override fun getViewTypeCount(): Int = 1

        override fun isEmpty(): Boolean = currentLevelEntries.isEmpty()

        override fun areAllItemsEnabled(): Boolean = true

        override fun isEnabled(position: Int): Boolean = true

        override fun newLog(logMessage: LogItem) {
            val msg = Message.obtain()
            msg.what = MESSAGE_NEWLOG
            val bundle = Bundle()
            bundle.putParcelable("logmessage", logMessage)
            msg.data = bundle
            mHandler.sendMessage(msg)
        }

        override fun handleMessage(msg: Message): Boolean {
            // We have been called
            when (msg.what) {
                MESSAGE_NEWLOG -> {
                    @Suppress("DEPRECATION")
                    val logMessage = msg.data.getParcelable<LogItem>("logmessage")
                    if (logMessage != null && addLogMessage(logMessage)) {
                        for (observer in observers) observer.onChanged()
                    }
                }
                MESSAGE_CLEARLOG -> {
                    for (observer in observers) observer.onInvalidated()
                    initLogBuffer()
                }
                MESSAGE_NEWTS -> {
                    for (observer in observers) observer.onInvalidated()
                }
                MESSAGE_NEWLOGLEVEL -> {
                    initCurrentMessages()
                    for (observer in observers) observer.onChanged()
                }
            }
            return true
        }

        private fun initCurrentMessages() {
            currentLevelEntries.clear()
            for (li in allEntries) {
                if (li.verbosityLevel <= mLogLevel || mLogLevel == VpnProfile.MAXLOGLEVEL) {
                    currentLevelEntries.add(li)
                }
            }
        }

        /**
         * @return True if the current entries have changed
         */
        private fun addLogMessage(logmessage: LogItem): Boolean {
            allEntries.add(logmessage)

            if (allEntries.size > MAX_STORED_LOG_ENTRIES) {
                val oldAllEntries = allEntries
                allEntries = Vector(oldAllEntries.size)
                for (i in 50 until oldAllEntries.size) {
                    allEntries.add(oldAllEntries.elementAt(i))
                }
                initCurrentMessages()
                return true
            } else {
                return if (logmessage.verbosityLevel <= mLogLevel) {
                    currentLevelEntries.add(logmessage)
                    true
                } else {
                    false
                }
            }
        }

        fun clearLog() {
            // Actually is probably called from GUI Thread as result of the user
            // pressing a button. But better safe than sorry
            VpnStatus.clearLog()
            VpnStatus.logInfo(R.string.logCleared)
            mHandler.sendEmptyMessage(MESSAGE_CLEARLOG)
        }

        fun setTimeFormat(newTimeFormat: Int) {
            mTimeFormat = newTimeFormat
            mHandler.sendEmptyMessage(MESSAGE_NEWTS)
        }

        fun setLogLevel(logLevel: Int) {
            mLogLevel = logLevel
            mHandler.sendEmptyMessage(MESSAGE_NEWLOGLEVEL)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.clearlog -> {
                ladapter.clearLog()
                return true
            }
            R.id.cancel -> {
                val intent = Intent(activity, DisconnectVPN::class.java)
                startActivity(intent)
                return true
            }
            R.id.send -> ladapter.shareLog()
            R.id.edit_vpn -> {
                val act = activity ?: return true
                // Loading a profile deserializes it from disk (and may decrypt via the Android
                // Keystore), which can block for seconds, so do it off the main thread.
                viewLifecycleOwner.lifecycleScope.launch {
                    val lastConnectedprofile = withContext(Dispatchers.IO) {
                        ProfileManager.get(act, VpnStatus.getLastConnectedVPNProfile())
                    }
                    if (!isAdded) return@launch
                    if (lastConnectedprofile != null) {
                        val vprefintent = Intent(act, VPNPreferences::class.java)
                            .putExtra(VpnProfile.EXTRA_PROFILEUUID, lastConnectedprofile.getUUIDString())
                        @Suppress("DEPRECATION")
                        startActivityForResult(vprefintent, START_VPN_CONFIG)
                    } else {
                        Toast.makeText(act, R.string.log_no_last_vpn, Toast.LENGTH_LONG).show()
                    }
                }
            }
            R.id.toggle_time -> showHideOptionsPanel()
            android.R.id.home -> return super.onOptionsItemSelected(item)
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showHideOptionsPanel() {
        val ol = mOptionsLayout ?: return
        val optionsVisible = ol.visibility != View.GONE

        val anim: ObjectAnimator
        if (optionsVisible) {
            anim = ObjectAnimator.ofFloat(ol, "alpha", 1.0f, 0f)
            anim.addListener(collapseListener)
        } else {
            ol.visibility = View.VISIBLE
            anim = ObjectAnimator.ofFloat(ol, "alpha", 0f, 1.0f)
        }
        anim.start()
    }

    private val collapseListener: AnimatorListenerAdapter = object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            mOptionsLayout?.visibility = View.GONE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.logmenu, menu)
        if (resources.getBoolean(R.bool.logSildersAlwaysVisible)) {
            menu.removeItem(R.id.toggle_time)
        }
    }

    override fun onResume() {
        super.onResume()
        val intent = Intent(activity, OpenVPNService::class.java)
        intent.action = OpenVPNService.START_SERVICE
    }

    override fun onStart() {
        super.onStart()
        VpnStatus.addStateListener(this)
        VpnStatus.addByteCountListener(this)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == START_VPN_CONFIG && resultCode == Activity.RESULT_OK) {
            val configuredVPN = data?.getStringExtra(VpnProfile.EXTRA_PROFILEUUID)

            val profile = ProfileManager.get(activity, configuredVPN)
            ProfileManager.saveProfile(activity, profile)
            // Name could be modified, reset List adapter

            val dialog = MaterialAlertDialogBuilder(requireActivity())
            dialog.setTitle(R.string.configuration_changed)
            dialog.setMessage(R.string.restart_vpn_after_change)

            dialog.setPositiveButton(R.string.restart) { _, _ ->
                val intent = Intent(activity, LaunchVPN::class.java)
                intent.putExtra(LaunchVPN.EXTRA_KEY, profile.getUUIDString())
                intent.putExtra(OpenVPNService.EXTRA_START_REASON, "restart from logwindow")
                intent.action = Intent.ACTION_MAIN
                startActivity(intent)
            }
            dialog.setNegativeButton(R.string.ignore, null)
            dialog.create().show()
        }
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onStop() {
        super.onStop()
        VpnStatus.removeStateListener(this)
        VpnStatus.removeByteCountListener(this)

        requireActivity().getPreferences(0).edit().putInt(LOGTIMEFORMAT, ladapter.mTimeFormat)
            .putInt(VERBOSITYLEVEL, ladapter.mLogLevel).apply()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        @Suppress("DEPRECATION")
        super.onActivityCreated(savedInstanceState)
        val lv = listView

        lv.setOnItemLongClickListener { _, view, _, _ ->
            val clipboard = requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Log Entry", (view as TextView).text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(activity, R.string.copied_entry, Toast.LENGTH_SHORT).show()
            true
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.log_fragment, container, false)

        @Suppress("DEPRECATION")
        setHasOptionsMenu(true)

        ladapter = LogWindowListAdapter()
        ladapter.mTimeFormat = requireActivity().getPreferences(0).getInt(LOGTIMEFORMAT, 1)
        val logLevel = requireActivity().getPreferences(0).getInt(VERBOSITYLEVEL, 1)
        ladapter.setLogLevel(logLevel)

        listAdapter = ladapter

        mTimeRadioGroup = v.findViewById(R.id.timeFormatRadioGroup)
        mTimeRadioGroup.setOnCheckedChangeListener(this)

        when (ladapter.mTimeFormat) {
            TIME_FORMAT_ISO -> mTimeRadioGroup.check(R.id.radioISO)
            TIME_FORMAT_NONE -> mTimeRadioGroup.check(R.id.radioNone)
            TIME_FORMAT_SHORT -> mTimeRadioGroup.check(R.id.radioShort)
        }

        mClearLogCheckBox = v.findViewById(R.id.clearlogconnect)
        mClearLogCheckBox.isChecked =
            PreferenceManager.getDefaultSharedPreferences(requireActivity()).getBoolean(LaunchVPN.CLEARLOG, true)
        mClearLogCheckBox.setOnCheckedChangeListener { _, isChecked ->
            Preferences.getDefaultSharedPreferences(requireActivity()).edit()
                .putBoolean(LaunchVPN.CLEARLOG, isChecked).apply()
        }

        mSpeedView = v.findViewById(R.id.speed)

        mOptionsLayout = v.findViewById(R.id.logOptionsLayout)
        mLogLevelSlider = v.findViewById(R.id.LogLevelSlider)
        mLogLevelSlider.max = VpnProfile.MAXLOGLEVEL - 1
        mLogLevelSlider.progress = logLevel - 1

        mLogLevelSlider.setOnSeekBarChangeListener(this)

        if (resources.getBoolean(R.bool.logSildersAlwaysVisible)) {
            mOptionsLayout?.visibility = View.VISIBLE
        }

        mUpStatus = v.findViewById(R.id.speedUp)
        mDownStatus = v.findViewById(R.id.speedDown)
        mConnectStatus = v.findViewById(R.id.speedStatus)
        mStartPendingIntent = v.findViewById(R.id.trigger_pending_action)
        mStartPendingIntent?.setOnClickListener {
            mPendingIntent?.let { startActivity(it) }
        }
        if (mShowOptionsLayout) {
            mOptionsLayout?.visibility = View.VISIBLE
        }

        Utils.applyInsetListener(v)

        return v
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (resources.getBoolean(R.bool.logSildersAlwaysVisible)) {
            mShowOptionsLayout = true
            mOptionsLayout?.visibility = View.VISIBLE
        }
    }

    override fun updateState(
        status: String?, logMessage: String?, resId: Int, level: ConnectionStatus?, intent: Intent?
    ) {
        if (isAdded) {
            val cleanLogMessage = VpnStatus.getLastCleanLogMessage(requireActivity())

            requireActivity().runOnUiThread {
                if (isAdded) {
                    mSpeedView?.text = cleanLogMessage
                    mConnectStatus?.text = cleanLogMessage
                }
                mStartPendingIntent?.visibility = if (intent == null) View.GONE else View.VISIBLE
                mPendingIntent = intent
            }
        }
    }

    override fun setConnectedVPN(uuid: String?) {}

    override fun onDestroy() {
        VpnStatus.removeLogListener(ladapter)
        super.onDestroy()
    }

    companion object {
        private const val LOGTIMEFORMAT = "logtimeformat"
        private const val START_VPN_CONFIG = 0
        private const val VERBOSITYLEVEL = "verbositylevel"

        private const val MESSAGE_NEWLOG = 0
        private const val MESSAGE_CLEARLOG = 1
        private const val MESSAGE_NEWTS = 2
        private const val MESSAGE_NEWLOGLEVEL = 3

        const val TIME_FORMAT_NONE = 0
        const val TIME_FORMAT_SHORT = 1
        const val TIME_FORMAT_ISO = 2
        private const val MAX_STORED_LOG_ENTRIES = 1000
    }
}
