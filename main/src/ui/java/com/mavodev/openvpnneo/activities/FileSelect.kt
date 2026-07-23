/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.activities

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Base64
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.VpnProfile
import com.mavodev.openvpnneo.fragments.FileSelectionFragment
import com.mavodev.openvpnneo.fragments.InlineFileTab
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class FileSelect : BaseActivity() {

    private lateinit var mFSFragment: FileSelectionFragment
    private var mInlineFragment: InlineFileTab? = null
    private var mData: String? = null
    private var inlineFileTab: ActionBar.Tab? = null
    private var fileExplorerTab: ActionBar.Tab? = null
    private var mNoInline = false
    private var mShowClear = false
    private var mBase64Encode = false

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.file_dialog)

        // READ_EXTERNAL_STORAGE was deprecated in API 33 (Tiramisu); on API 33+ the
        // permission is never granted, which caused the file-explorer tab to be silently
        // removed or the activity to be cancelled, breaking config import for all modern
        // Android users.  SAF (ACTION_OPEN_DOCUMENT) works without this permission.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            checkPermission()
        }

        mData = intent.getStringExtra(START_DATA)

        var title = intent.getStringExtra(WINDOW_TITLE)
        val titleId = intent.getIntExtra(WINDOW_TITLE, 0)
        if (titleId != 0) title = getString(titleId)
        if (title != null) setTitle(title)

        mNoInline = intent.getBooleanExtra(NO_INLINE_SELECTION, false)
        mShowClear = intent.getBooleanExtra(SHOW_CLEAR_BUTTON, false)
        mBase64Encode = intent.getBooleanExtra(DO_BASE64_ENCODE, false)

        val bar = supportActionBar!!
        bar.navigationMode = ActionBar.NAVIGATION_MODE_TABS
        fileExplorerTab = bar.newTab().setText(R.string.file_explorer_tab)
        inlineFileTab = bar.newTab().setText(R.string.inline_file_tab)

        mFSFragment = FileSelectionFragment()
        fileExplorerTab!!.setTabListener(MyTabsListener(mFSFragment))
        bar.addTab(fileExplorerTab!!)

        if (!mNoInline) {
            val inlineFragment = InlineFileTab()
            mInlineFragment = inlineFragment
            inlineFileTab!!.setTabListener(MyTabsListener(inlineFragment))
            bar.addTab(inlineFileTab!!)
        } else {
            mFSFragment.setNoInLine()
        }
    }

    @Suppress("DEPRECATION")
    private fun checkPermission() {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), PERMISSION_REQUEST)
        }
    }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
            if (mNoInline) {
                setResult(RESULT_CANCELED)
                finish()
            } else {
                fileExplorerTab?.let { supportActionBar!!.removeTab(it) }
            }
        } else {
            mFSFragment.refresh()
        }
    }

    fun showClear(): Boolean {
        return if (mData == null || mData == "") false else mShowClear
    }

    @Suppress("DEPRECATION")
    inner class MyTabsListener(private val mFragment: Fragment) : ActionBar.TabListener {
        private var mAdded = false

        override fun onTabSelected(tab: ActionBar.Tab, ft: FragmentTransaction) {
            // Check if the fragment is already initialized
            if (!mAdded) {
                // If not, instantiate and add it to the activity
                ft.add(android.R.id.content, mFragment)
                mAdded = true
            } else {
                // If it exists, simply attach it in order to show it
                ft.attach(mFragment)
            }
        }

        override fun onTabUnselected(tab: ActionBar.Tab, ft: FragmentTransaction) {
            ft.detach(mFragment)
        }

        override fun onTabReselected(tab: ActionBar.Tab, ft: FragmentTransaction) {}
    }

    fun importFile(path: String) {
        val ifile = File(path)
        var fe: Exception? = null
        try {
            var data = ""

            val fileData = readBytesFromFile(ifile)
            data += if (mBase64Encode) {
                Base64.encodeToString(fileData, Base64.DEFAULT)
            } else {
                String(fileData)
            }

            mData = data

            saveInlineData(ifile.name, data)
        } catch (e: IOException) {
            fe = e
        }
        if (fe != null) {
            val ab = AlertDialog.Builder(this)
            ab.setTitle(R.string.error_importing_file)
            ab.setMessage(getString(R.string.import_error_message) + "\n" + fe.localizedMessage)
            ab.setPositiveButton(android.R.string.ok, null)
            ab.show()
        }
    }

    fun setFile(path: String) {
        val intent = Intent()
        intent.putExtra(RESULT_DATA, path)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    val selectPath: String?
        get() = if (VpnProfile.isEmbedded(mData)) mData else null

    val inlineData: CharSequence
        get() = if (VpnProfile.isEmbedded(mData)) VpnProfile.getEmbeddedContent(mData) else ""

    fun clearData() {
        val intent = Intent()
        intent.putExtra(RESULT_DATA, null as String?)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    fun saveInlineData(fileName: String?, string: String) {
        val intent = Intent()

        if (fileName == null) {
            intent.putExtra(RESULT_DATA, VpnProfile.INLINE_TAG + string)
        } else {
            intent.putExtra(RESULT_DATA, VpnProfile.DISPLAYNAME_TAG + fileName + VpnProfile.INLINE_TAG + string)
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    companion object {
        const val RESULT_DATA = "RESULT_PATH"
        const val START_DATA = "START_DATA"
        const val WINDOW_TITLE = "WINDOW_TILE"
        const val NO_INLINE_SELECTION = "com.mavodev.openvpnneo.NO_INLINE_SELECTION"
        const val SHOW_CLEAR_BUTTON = "com.mavodev.openvpnneo.SHOW_CLEAR_BUTTON"
        const val DO_BASE64_ENCODE = "com.mavodev.openvpnneo.BASE64ENCODE"
        private const val PERMISSION_REQUEST = 23621

        @Throws(IOException::class)
        private fun readBytesFromFile(file: File): ByteArray {
            val len = file.length()
            if (len > VpnProfile.MAX_EMBED_FILE_SIZE) {
                throw IOException("selected file size too big to embed into profile")
            }

            // Create the byte array to hold the data
            val bytes = ByteArray(len.toInt())

            FileInputStream(file).use { input ->
                // Read in the bytes
                var offset = 0
                var bytesRead = 0
                while (offset < bytes.size &&
                    input.read(bytes, offset, bytes.size - offset).also { bytesRead = it } >= 0
                ) {
                    offset += bytesRead
                }
            }
            return bytes
        }
    }
}
