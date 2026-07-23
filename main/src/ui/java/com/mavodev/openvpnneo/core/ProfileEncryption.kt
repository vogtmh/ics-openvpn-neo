/*
 * Copyright (c) 2012-2022 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

/*
 * Uses the deprecated androidx.security.crypto (Jetpack Security) library. It has no drop-in
 * replacement (Google recommends Tink directly), still works, and — importantly — owns the
 * on-disk format of existing encrypted profiles (.cp files). Migrating away requires a
 * dedicated, backward-compatible effort, so the deprecation is suppressed intentionally here.
 */
@file:Suppress("DEPRECATION")

package com.mavodev.openvpnneo.core

import android.content.Context
import android.os.Build
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.GeneralSecurityException

internal class ProfileEncryption {

    companion object {
        @JvmStatic
        fun encryptionEnabled(): Boolean {
            return mMasterKey != null
        }

        private var mMasterKey: MasterKey? = null
        @JvmStatic
        fun initMasterCryptAlias(context:Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
                return
            try {
                mMasterKey = MasterKey.Builder(context)
                      .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                      .build()
            } catch (e: GeneralSecurityException) {
                VpnStatus.logException("Could not initialise file encryption key.", e)
            } catch (e: IOException) {
                VpnStatus.logException("Could not initialise file encryption key.", e)
            }
        }

        @JvmStatic
        @Throws(GeneralSecurityException::class, IOException::class)
        fun getEncryptedVpInput(context: Context, file: File): FileInputStream {
            val encryptedFile = EncryptedFile.Builder(
                context,
                file,
                mMasterKey!!,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()
            return encryptedFile.openFileInput()
        }

        @JvmStatic
        @Throws(GeneralSecurityException::class, IOException::class)
        fun getEncryptedVpOutput(context: Context, file: File): FileOutputStream {
            val encryptedFile = EncryptedFile.Builder(
                context,
                file,
                mMasterKey!!,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()
            return encryptedFile.openFileOutput()
        }
    }
}