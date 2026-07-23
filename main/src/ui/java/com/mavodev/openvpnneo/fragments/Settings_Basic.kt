/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.fragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.VpnProfile
import com.mavodev.openvpnneo.views.FileSelectLayout

internal class Settings_Basic : KeyChainSettingsFragment(), OnItemSelectedListener,
    FileSelectLayout.FileSelectCallback, CompoundButton.OnCheckedChangeListener {

    private lateinit var mClientCert: FileSelectLayout
    private lateinit var mCaCert: FileSelectLayout
    private lateinit var mClientKey: FileSelectLayout
    private lateinit var mUseLzo: CompoundButton
    private lateinit var mUseLegacyProvider: CompoundButton
    private lateinit var mType: Spinner
    private lateinit var mCompatMode: Spinner
    private lateinit var mpkcs12: FileSelectLayout
    private lateinit var mCrlFile: FileSelectLayout
    private lateinit var mPKCS12Password: TextView
    private lateinit var mUserName: EditText
    private lateinit var mPassword: EditText
    private lateinit var mView: View
    private lateinit var mProfileName: EditText
    private lateinit var mKeyPassword: EditText
    private lateinit var mEnablePeerFingerprint: CompoundButton
    private lateinit var mPeerFingerprints: EditText
    private lateinit var mMakeDefaultProfile: CompoundButton

    private val fileselects = SparseArray<FileSelectLayout>()
    private lateinit var mAuthRetry: Spinner

    private fun addFileSelectLayout(fsl: FileSelectLayout, type: Utils.FileType) {
        val i = fileselects.size() + CHOOSE_FILE_OFFSET
        fileselects.put(i, fsl)
        fsl.setCaller(this, i, type)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        mView = inflater.inflate(R.layout.basic_settings, container, false)

        mProfileName = mView.findViewById(R.id.profilename)
        mClientCert = mView.findViewById(R.id.certselect)
        mClientKey = mView.findViewById(R.id.keyselect)
        mCaCert = mView.findViewById(R.id.caselect)
        mpkcs12 = mView.findViewById(R.id.pkcs12select)
        mCrlFile = mView.findViewById(R.id.crlfile)
        mUseLzo = mView.findViewById(R.id.lzo)
        mUseLegacyProvider = mView.findViewById(R.id.legacyprovider)
        mType = mView.findViewById(R.id.type)
        mCompatMode = mView.findViewById(R.id.compatmode)
        mPKCS12Password = mView.findViewById(R.id.pkcs12password)
        mEnablePeerFingerprint = mView.findViewById(R.id.enable_peer_fingerprint)
        mPeerFingerprints = mView.findViewById(R.id.peer_fingerprint)
        mMakeDefaultProfile = mView.findViewById(R.id.make_default_profile)

        mUserName = mView.findViewById(R.id.auth_username)
        mPassword = mView.findViewById(R.id.auth_password)
        mKeyPassword = mView.findViewById(R.id.key_password)
        mAuthRetry = mView.findViewById(R.id.auth_retry)

        addFileSelectLayout(mCaCert, Utils.FileType.CA_CERTIFICATE)
        addFileSelectLayout(mClientCert, Utils.FileType.CLIENT_CERTIFICATE)
        addFileSelectLayout(mClientKey, Utils.FileType.KEYFILE)
        addFileSelectLayout(mpkcs12, Utils.FileType.PKCS12)
        addFileSelectLayout(mCrlFile, Utils.FileType.CRL_FILE)
        mCaCert.setShowClear()
        mCrlFile.setShowClear()

        mType.onItemSelectedListener = this
        mAuthRetry.onItemSelectedListener = this
        mEnablePeerFingerprint.setOnCheckedChangeListener(this)

        initKeychainViews(mView)

        return mView
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(request, result, data)
        if (result == Activity.RESULT_OK && request >= CHOOSE_FILE_OFFSET) {
            val fsl = fileselects.get(request)
            fsl.parseResponse(data, requireActivity())

            savePreferences()

            // Private key files may result in showing/hiding the private key password dialog
            if (fsl === mClientKey) {
                changeType(mType.selectedItemPosition)
            }
        }
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        if (parent === mType) {
            changeType(position)
        }
    }

    private fun changeType(type: Int) {
        // hide everything
        mView.findViewById<View>(R.id.pkcs12).visibility = View.GONE
        mView.findViewById<View>(R.id.certs).visibility = View.GONE
        mView.findViewById<View>(R.id.statickeys).visibility = View.GONE
        mView.findViewById<View>(R.id.keystore).visibility = View.GONE
        mView.findViewById<View>(R.id.cacert).visibility = View.GONE
        mView.findViewById<FileSelectLayout>(R.id.caselect).setClearable(false)
        mView.findViewById<View>(R.id.userpassword).visibility = View.GONE
        mView.findViewById<View>(R.id.key_password_layout).visibility = View.GONE
        mView.findViewById<View>(R.id.external_auth).visibility = View.GONE
        mView.findViewById<View>(R.id.crlfile).visibility = View.VISIBLE

        // Fall through are by design
        when (type) {
            VpnProfile.TYPE_USERPASS_CERTIFICATES, VpnProfile.TYPE_CERTIFICATES -> {
                if (type == VpnProfile.TYPE_USERPASS_CERTIFICATES) {
                    mView.findViewById<View>(R.id.userpassword).visibility = View.VISIBLE
                }
                mView.findViewById<View>(R.id.certs).visibility = View.VISIBLE
                mView.findViewById<View>(R.id.cacert).visibility = View.VISIBLE
                if (mProfile.requireTLSKeyPassword()) {
                    mView.findViewById<View>(R.id.key_password_layout).visibility = View.VISIBLE
                }
            }

            VpnProfile.TYPE_USERPASS_PKCS12, VpnProfile.TYPE_PKCS12 -> {
                if (type == VpnProfile.TYPE_USERPASS_PKCS12) {
                    mView.findViewById<View>(R.id.userpassword).visibility = View.VISIBLE
                }
                mView.findViewById<View>(R.id.pkcs12).visibility = View.VISIBLE
                mView.findViewById<View>(R.id.cacert).visibility = View.VISIBLE
                mView.findViewById<FileSelectLayout>(R.id.caselect).setClearable(true)
            }

            VpnProfile.TYPE_STATICKEYS -> {
                mView.findViewById<View>(R.id.statickeys).visibility = View.VISIBLE
                mView.findViewById<View>(R.id.crlfile).visibility = View.GONE
            }

            VpnProfile.TYPE_USERPASS_KEYSTORE, VpnProfile.TYPE_KEYSTORE -> {
                if (type == VpnProfile.TYPE_USERPASS_KEYSTORE) {
                    mView.findViewById<View>(R.id.userpassword).visibility = View.VISIBLE
                }
                mView.findViewById<View>(R.id.keystore).visibility = View.VISIBLE
                mView.findViewById<View>(R.id.cacert).visibility = View.VISIBLE
                mView.findViewById<FileSelectLayout>(R.id.caselect).setClearable(true)
            }

            VpnProfile.TYPE_USERPASS -> {
                mView.findViewById<View>(R.id.userpassword).visibility = View.VISIBLE
                mView.findViewById<View>(R.id.cacert).visibility = View.VISIBLE
            }

            VpnProfile.TYPE_EXTERNAL_APP -> {
                mView.findViewById<View>(R.id.external_auth).visibility = View.VISIBLE
            }
        }
    }

    override fun loadPreferences() {
        super.loadPreferences()
        mProfileName.setText(mProfile.mName)
        mClientCert.setData(mProfile.mClientCertFilename, requireActivity())
        mClientKey.setData(mProfile.mClientKeyFilename, requireActivity())
        mCaCert.setData(mProfile.mCaFilename, requireActivity())
        mCrlFile.setData(mProfile.mCrlFilename, requireActivity())

        mUseLzo.isChecked = mProfile.mUseLzo
        mUseLegacyProvider.isChecked = mProfile.mUseLegacyProvider
        mType.setSelection(mProfile.mAuthenticationType)
        mCompatMode.setSelection(Utils.mapCompatVer(mProfile.mCompatMode))
        mpkcs12.setData(mProfile.mPKCS12Filename, requireActivity())
        mPKCS12Password.text = mProfile.mPKCS12Password
        mUserName.setText(mProfile.mUsername)
        mPassword.setText(mProfile.mPassword)
        mKeyPassword.setText(mProfile.mKeyPassword)
        mAuthRetry.setSelection(mProfile.mAuthRetry)
        mEnablePeerFingerprint.isChecked = mProfile.mCheckPeerFingerprint
        mPeerFingerprints.setText(mProfile.mPeerFingerPrints)

        // Check if this profile is currently the default
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(activity)
        val currentDefaultUUID = defaultPrefs.getString("alwaysOnVpn", "")
        mMakeDefaultProfile.isChecked = mProfile.getUUIDString() == currentDefaultUUID
    }

    override fun savePreferences() {
        super.savePreferences()
        mProfile.mName = mProfileName.text.toString()
        mProfile.mCaFilename = mCaCert.data
        mProfile.mClientCertFilename = mClientCert.data
        mProfile.mClientKeyFilename = mClientKey.data
        mProfile.mCrlFilename = mCrlFile.data

        mProfile.mUseLzo = mUseLzo.isChecked
        mProfile.mUseLegacyProvider = mUseLegacyProvider.isChecked
        mProfile.mAuthenticationType = mType.selectedItemPosition
        mProfile.mPKCS12Filename = mpkcs12.data
        mProfile.mPKCS12Password = mPKCS12Password.text.toString()

        mProfile.mPassword = mPassword.text.toString()
        mProfile.mUsername = mUserName.text.toString()
        mProfile.mKeyPassword = mKeyPassword.text.toString()
        mProfile.mAuthRetry = mAuthRetry.selectedItemPosition
        mProfile.mCheckPeerFingerprint = mEnablePeerFingerprint.isChecked
        mProfile.mPeerFingerPrints = mPeerFingerprints.text.toString()
        mProfile.mCompatMode = Utils.mapCompatMode(mCompatMode.selectedItemPosition)

        // Save default profile setting
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(activity)
        val editor = defaultPrefs.edit()
        if (mMakeDefaultProfile.isChecked) {
            editor.putString("alwaysOnVpn", mProfile.getUUIDString())
        } else {
            // If unchecked, check if this was the default and remove it
            val currentDefaultUUID = defaultPrefs.getString("alwaysOnVpn", "")
            if (mProfile.getUUIDString() == currentDefaultUUID) {
                editor.putString("alwaysOnVpn", "")
            }
        }
        editor.apply()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        savePreferences()
        outState.putString(requireActivity().packageName + "profileUUID", mProfile.getUUID().toString())
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        if (buttonView === mEnablePeerFingerprint) {
            mPeerFingerprints.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
    }

    companion object {
        private const val CHOOSE_FILE_OFFSET = 1000
    }
}
