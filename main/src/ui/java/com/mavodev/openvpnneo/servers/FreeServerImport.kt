/*
 * Copyright (c) 2026 Maximilian Vogt
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.servers

import android.content.Context
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.VpnProfile
import com.mavodev.openvpnneo.core.ConfigParser
import com.mavodev.openvpnneo.core.ProfileManager
import com.mavodev.openvpnneo.country.CountryInfoRepository
import java.io.StringReader

/**
 * Shared logic for turning a decoded VPNGate .ovpn into a [VpnProfile], used by both the
 * server browser (permanent import) and the connection tester (temporary profile).
 */
object FreeServerImport {

    /** Retry cap for imported free servers — they are often dead, so fail after a few tries. */
    const val IMPORT_CONNECT_RETRY_MAX = "3"

    /**
     * Retry cap for the connection test. Two bounded attempts so a spurious first-attempt
     * AUTH_FAILED (some VPNGate servers reject the first handshake) gets one more chance,
     * while still giving up quickly on genuinely dead servers.
     */
    const val TEST_CONNECT_RETRY_MAX = "2"

    /**
     * Per-attempt server-poll timeout (seconds) for the test. Short so an unreachable
     * server ("no route to host") fails its attempt in seconds instead of the 120s default,
     * and the whole test finishes within ~2 attempts.
     */
    const val TEST_CONNECT_TIMEOUT_SECONDS = 15

    /**
     * Parses [configText] into a named [VpnProfile] with [connectRetryMax] applied.
     * Throws [ConfigParser.ConfigParseError] or [java.io.IOException] on a bad config.
     */
    fun buildProfile(
        context: Context,
        configText: String,
        countryName: String,
        host: String,
        connectRetryMax: String,
    ): VpnProfile {
        val parser = ConfigParser()
        parser.parseConfig(StringReader(configText))
        val profile = parser.convertProfile()
        profile.mName = context.getString(R.string.free_servers_profile_name, countryName, host)
        profile.mConnectRetryMax = connectRetryMax
        return profile
    }

    /**
     * Adds [profile] to the permanent profile list and persists it. [countryCode] pre-seeds
     * the profile-list flag with the server's advertised country; this is corrected
     * automatically on the next successful connect via the geo lookup.
     */
    fun savePermanent(context: Context, profile: VpnProfile, countryCode: String) {
        val pm = ProfileManager.getInstance(context)
        pm.addProfile(profile)
        profile.addChangeLogEntry("Imported from VPNGate free servers")
        ProfileManager.saveProfile(context, profile)
        pm.saveProfileList(context)
        if (countryCode.isNotBlank()) {
            CountryInfoRepository(context).saveProfileCountry(profile.uuidString, countryCode)
        }
    }
}
