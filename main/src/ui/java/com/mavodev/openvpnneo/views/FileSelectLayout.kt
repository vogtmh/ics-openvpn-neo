/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.views

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.VpnProfile
import com.mavodev.openvpnneo.activities.FileSelect
import com.mavodev.openvpnneo.core.VpnStatus
import com.mavodev.openvpnneo.core.X509Utils
import com.mavodev.openvpnneo.fragments.Utils
import java.io.IOException

class FileSelectLayout : LinearLayout, View.OnClickListener {

    interface FileSelectCallback {
        fun getString(res: Int): String
        fun startActivityForResult(intent: Intent, requestCode: Int)
    }

    private var mIsCertificate = false
    private lateinit var mDataView: TextView
    private var mData: String? = null
    val data: String? get() = mData
    private var mFragment: FileSelectCallback? = null
    private var mTaskId = 0
    private lateinit var mSelectButton: View
    private var fileType: Utils.FileType? = null
    private var mTitle: String? = null
    private var mShowClear = false
    private lateinit var mDataDetails: TextView
    private lateinit var mShowClearButton: Button

    constructor(context: Context, attrset: AttributeSet?) : super(context, attrset) {
        val ta = context.obtainStyledAttributes(attrset, R.styleable.FileSelectLayout)
        setupViews(
            ta.getString(R.styleable.FileSelectLayout_fileTitle),
            ta.getBoolean(R.styleable.FileSelectLayout_certificate, true)
        )
        ta.recycle()
    }

    constructor(context: Context, title: String?, isCertificate: Boolean, showClear: Boolean) : super(context) {
        setupViews(title, isCertificate)
        mShowClear = showClear
    }

    fun parseResponse(data: Intent?, c: Context) {
        try {
            val newData = Utils.getFilePickerResult(fileType, data, c)
            if (newData != null) {
                setData(newData, c)
            } else {
                val fileData = data?.getStringExtra(FileSelect.RESULT_DATA)
                setData(fileData, c)
            }
        } catch (e: IOException) {
            VpnStatus.logException(e)
        } catch (e: SecurityException) {
            VpnStatus.logException(e)
        }
    }

    private fun setupViews(title: String?, isCertificate: Boolean) {
        inflate(context, R.layout.file_select, this)

        mTitle = title
        mIsCertificate = isCertificate

        val tView = findViewById<TextView>(R.id.file_title)
        tView.text = mTitle

        mDataView = findViewById(R.id.file_selected_item)
        mDataDetails = findViewById(R.id.file_selected_description)
        mSelectButton = findViewById(R.id.file_select_button)
        mSelectButton.setOnClickListener(this)

        mShowClearButton = findViewById(R.id.file_clear_button)
        mShowClearButton.setOnClickListener(this)
    }

    fun setClearable(clearable: Boolean) {
        mShowClear = clearable
        if (mData != null) {
            mShowClearButton.visibility = if (mShowClear) VISIBLE else GONE
        }
    }

    fun setCaller(fragment: FileSelectCallback, i: Int, ft: Utils.FileType) {
        mTaskId = i
        mFragment = fragment
        fileType = ft
    }

    fun getCertificateFileDialog() {
        val startFC = Intent(context, FileSelect::class.java)
        startFC.putExtra(FileSelect.START_DATA, mData)
        startFC.putExtra(FileSelect.WINDOW_TITLE, mTitle)
        if (fileType == Utils.FileType.PKCS12) {
            startFC.putExtra(FileSelect.DO_BASE64_ENCODE, true)
        }
        if (mShowClear) {
            startFC.putExtra(FileSelect.SHOW_CLEAR_BUTTON, true)
        }
        mFragment?.startActivityForResult(startFC, mTaskId)
    }

    fun setData(data: String?, c: Context) {
        mData = data
        if (data == null) {
            mDataView.text = c.getString(R.string.no_data)
            mDataDetails.text = ""
            mShowClearButton.visibility = GONE
        } else {
            when {
                data.startsWith(VpnProfile.DISPLAYNAME_TAG) ->
                    mDataView.text = c.getString(R.string.imported_from_file, VpnProfile.getDisplayName(data))
                data.startsWith(VpnProfile.INLINE_TAG) ->
                    mDataView.setText(R.string.inline_file_data)
                else -> mDataView.text = data
            }
            if (mIsCertificate) {
                mDataDetails.text = X509Utils.getCertificateFriendlyName(c, data)
            }
            // Show clear button if it should be shown
            mShowClearButton.visibility = if (mShowClear) VISIBLE else GONE
        }
    }

    override fun onClick(v: View) {
        if (v === mSelectButton) {
            val startFilePicker = Utils.getFilePickerIntent(context, fileType)
            if (startFilePicker == null || Utils.alwaysUseOldFileChooser(v.context)) {
                getCertificateFileDialog()
            } else {
                mFragment?.startActivityForResult(startFilePicker, mTaskId)
            }
        } else if (v === mShowClearButton) {
            setData(null, context)
        }
    }

    fun setShowClear() {
        mShowClear = true
    }
}
