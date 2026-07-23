/*
 * Copyright (c) 2012-2019 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.views

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.preference.PreferenceDialogFragmentCompat
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.VpnProfile
import com.mavodev.openvpnneo.VpnProfile.X509_VERIFY_TLSREMOTE_COMPAT_NOREMAPPING

class RemoteCNPreferenceDialog : PreferenceDialogFragmentCompat() {
    private lateinit var mSpinner: Spinner
    private lateinit var mEditText: EditText
    private lateinit var mRemoteTLSNote: TextView

    override fun onBindDialogView(view: View) {
        val pref = preference as RemoteCNPreference
        val mDn = pref.cnText
        val mDNType = pref.authtype

        mEditText = view.findViewById(R.id.tlsremotecn)
        mSpinner = view.findViewById(R.id.x509verifytype)
        mRemoteTLSNote = view.findViewById(R.id.tlsremotenote)
        mEditText.setText(mDn)

        populateSpinner(mDn, mDNType)
    }

    private fun populateSpinner(mDn: String?, mDNType: Int) {
        val authtypes = ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item)
        authtypes.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        authtypes.add(requireContext().getString(R.string.complete_dn))
        authtypes.add(requireContext().getString(R.string.rdn))
        authtypes.add(requireContext().getString(R.string.rdn_prefix))
        if ((mDNType == VpnProfile.X509_VERIFY_TLSREMOTE || mDNType == X509_VERIFY_TLSREMOTE_COMPAT_NOREMAPPING)
            && !mDn.isNullOrEmpty()
        ) {
            authtypes.add(requireContext().getString(R.string.tls_remote_deprecated))
            mRemoteTLSNote.visibility = View.VISIBLE
        } else {
            mRemoteTLSNote.visibility = View.GONE
        }
        mSpinner.adapter = authtypes
        mSpinner.setSelection(getSpinnerPositionFromAuthTYPE(mDNType, mDn))
    }

    private fun getSpinnerPositionFromAuthTYPE(mDNType: Int, mDn: String?): Int {
        return when (mDNType) {
            VpnProfile.X509_VERIFY_TLSREMOTE_DN -> 0
            VpnProfile.X509_VERIFY_TLSREMOTE_RDN -> 1
            VpnProfile.X509_VERIFY_TLSREMOTE_RDN_PREFIX -> 2
            X509_VERIFY_TLSREMOTE_COMPAT_NOREMAPPING, VpnProfile.X509_VERIFY_TLSREMOTE ->
                if (mDn.isNullOrEmpty()) 1 else 3
            else -> 0
        }
    }

    private fun getAuthTypeFromSpinner(): Int {
        return when (mSpinner.selectedItemPosition) {
            0 -> VpnProfile.X509_VERIFY_TLSREMOTE_DN
            1 -> VpnProfile.X509_VERIFY_TLSREMOTE_RDN
            2 -> VpnProfile.X509_VERIFY_TLSREMOTE_RDN_PREFIX
            // This is the tls-remote entry, only visible if mDntype is a tls-remote type
            3 -> X509_VERIFY_TLSREMOTE_COMPAT_NOREMAPPING
            else -> VpnProfile.X509_VERIFY_TLSREMOTE
        }
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            val pref = preference as RemoteCNPreference
            val dn = mEditText.text.toString()
            val authtype = getAuthTypeFromSpinner()
            pref.setDN(dn)
            pref.setAuthType(authtype)
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(key: String): RemoteCNPreferenceDialog {
            val f = RemoteCNPreferenceDialog()
            val args = Bundle()
            args.putString(ARG_KEY, key)
            f.arguments = args
            return f
        }
    }
}
