/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.country

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.TrafficStats
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Immutable snapshot of the current public IP and its country code. */
data class CountryInfo(val ip: String, val countryCode: String)

/**
 * Owns everything related to the "current country" feature:
 *  - the (single-provider) geo lookup over OkHttp, with retries
 *  - caching of the last known result so the UI can render instantly on cold start
 *  - per-profile country persistence (used for the flags in the profile list)
 *  - resolving flag drawables and human-readable country names
 *  - network availability monitoring
 *
 * Extracted out of MainActivity so the geo/network logic is testable and no longer
 * tangled with the Activity lifecycle.
 */
class CountryInfoRepository(context: Context) {

    private val appContext = context.applicationContext
    private val cachePrefs = appContext.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
    private val profilePrefs = appContext.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // region Geo lookup

    /**
     * Fetches the current public IP/country from the single configured provider,
     * retrying up to [maxRetries] times with a short delay. On success the result is
     * cached. Returns null if all attempts fail. Safe to call from any coroutine.
     */
    suspend fun fetchCountryInfo(maxRetries: Int = DEFAULT_MAX_RETRIES): CountryInfo? {
        var attempt = 0
        while (true) {
            val info = tryFetchOnce()
            if (info != null) {
                cacheCurrentCountryInfo(info)
                return info
            }
            if (attempt >= maxRetries) return null
            attempt++
            delay(RETRY_DELAY_MS)
        }
    }

    private suspend fun tryFetchOnce(): CountryInfo? = withContext(Dispatchers.IO) {
        try {
            // Tag the socket for traffic stats to avoid StrictMode warnings.
            TrafficStats.setThreadStatsTag(TRAFFIC_STATS_TAG)
            val request = Request.Builder()
                .url(API_URL)
                .header("User-Agent", USER_AGENT)
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Country API returned ${response.code}")
                    return@withContext null
                }
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                CountryInfo(json.getString("ip"), json.getString("country"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Country API request failed", e)
            null
        } finally {
            TrafficStats.clearThreadStatsTag()
        }
    }

    // endregion

    // region Caching (last known current country)

    /** Last known current IP/country, or null if never fetched. */
    fun getCachedCountryInfo(): CountryInfo? {
        val ip = cachePrefs.getString(KEY_CURRENT_IP, null) ?: return null
        val country = cachePrefs.getString(KEY_CURRENT_COUNTRY, null) ?: return null
        return CountryInfo(ip, country)
    }

    private fun cacheCurrentCountryInfo(info: CountryInfo) {
        cachePrefs.edit()
            .putString(KEY_CURRENT_IP, info.ip)
            .putString(KEY_CURRENT_COUNTRY, info.countryCode)
            .apply()
    }

    // endregion

    // region Per-profile country persistence

    fun saveProfileCountry(profileUUID: String, countryCode: String) {
        profilePrefs.edit().putString(profileUUID, countryCode).apply()
    }

    fun getProfileCountry(profileUUID: String): String? =
        profilePrefs.getString(profileUUID, null)

    // endregion

    // region Flag & name lookup

    /** Drawable resource id of the bundled (offline) flag for [countryCode], or 0 if none. */
    fun flagResourceId(countryCode: String): Int {
        val name = "flag_${countryCode.lowercase()}"
        return appContext.resources.getIdentifier(name, "drawable", appContext.packageName)
    }

    fun countryName(countryCode: String): String = countryDisplayName(countryCode)

    // endregion

    // region Network monitoring

    /**
     * Registers a network callback. [listener] is invoked on the main thread with
     * `true` when a network becomes available (after a short settle delay) and
     * `false` when a network is lost.
     */
    fun startNetworkMonitoring(listener: (available: Boolean) -> Unit) {
        stopNetworkMonitoring()
        val cm = appContext.getSystemService(ConnectivityManager::class.java)
        connectivityManager = cm
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Delay to let DHCP/routing settle before querying.
                Handler(Looper.getMainLooper()).postDelayed(
                    { listener(true) }, NETWORK_SETTLE_DELAY_MS
                )
            }

            override fun onLost(network: Network) {
                Handler(Looper.getMainLooper()).post { listener(false) }
            }
        }
        networkCallback = callback
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
        cm.registerNetworkCallback(request, callback)
    }

    fun stopNetworkMonitoring() {
        val cm = connectivityManager
        val cb = networkCallback
        if (cm != null && cb != null) {
            try {
                cm.unregisterNetworkCallback(cb)
            } catch (e: IllegalArgumentException) {
                // Callback was already unregistered; ignore.
            }
        }
        networkCallback = null
        connectivityManager = null
    }

    // endregion

    companion object {
        private const val TAG = "CountryInfoRepository"

        private const val API_URL = "https://api.country.is/"
        private const val USER_AGENT = "OpenVPN-Neo/1.0"
        private const val TIMEOUT_SECONDS = 5L
        private const val TRAFFIC_STATS_TAG = 0x12345678
        private const val DEFAULT_MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 2000L
        private const val NETWORK_SETTLE_DELAY_MS = 1500L

        private const val CACHE_PREFS = "country_cache"
        private const val PROFILE_PREFS = "profile_countries"
        private const val KEY_CURRENT_IP = "current_ip"
        private const val KEY_CURRENT_COUNTRY = "current_country"
    }
}

private fun countryDisplayName(countryCode: String): String {
    return when (countryCode.uppercase()) {
        "AD" -> "Andorra"
        "AE" -> "United Arab Emirates"
        "AF" -> "Afghanistan"
        "AG" -> "Antigua and Barbuda"
        "AI" -> "Anguilla"
        "AL" -> "Albania"
        "AM" -> "Armenia"
        "AO" -> "Angola"
        "AQ" -> "Antarctica"
        "AR" -> "Argentina"
        "AS" -> "American Samoa"
        "AT" -> "Austria"
        "AU" -> "Australia"
        "AW" -> "Aruba"
        "AX" -> "Åland Islands"
        "AZ" -> "Azerbaijan"
        "BA" -> "Bosnia and Herzegovina"
        "BB" -> "Barbados"
        "BD" -> "Bangladesh"
        "BE" -> "Belgium"
        "BF" -> "Burkina Faso"
        "BG" -> "Bulgaria"
        "BH" -> "Bahrain"
        "BI" -> "Burundi"
        "BJ" -> "Benin"
        "BL" -> "Saint Barthélemy"
        "BM" -> "Bermuda"
        "BN" -> "Brunei"
        "BO" -> "Bolivia"
        "BQ" -> "Caribbean Netherlands"
        "BR" -> "Brazil"
        "BS" -> "Bahamas"
        "BT" -> "Bhutan"
        "BV" -> "Bouvet Island"
        "BW" -> "Botswana"
        "BY" -> "Belarus"
        "BZ" -> "Belize"
        "CA" -> "Canada"
        "CC" -> "Cocos Islands"
        "CD" -> "DR Congo"
        "CF" -> "Central African Republic"
        "CG" -> "Republic of the Congo"
        "CH" -> "Switzerland"
        "CI" -> "Côte d'Ivoire"
        "CK" -> "Cook Islands"
        "CL" -> "Chile"
        "CM" -> "Cameroon"
        "CN" -> "China"
        "CO" -> "Colombia"
        "CR" -> "Costa Rica"
        "CU" -> "Cuba"
        "CV" -> "Cape Verde"
        "CW" -> "Curaçao"
        "CX" -> "Christmas Island"
        "CY" -> "Cyprus"
        "CZ" -> "Czechia"
        "DE" -> "Germany"
        "DJ" -> "Djibouti"
        "DK" -> "Denmark"
        "DM" -> "Dominica"
        "DO" -> "Dominican Republic"
        "DZ" -> "Algeria"
        "EC" -> "Ecuador"
        "EE" -> "Estonia"
        "EG" -> "Egypt"
        "EH" -> "Western Sahara"
        "ER" -> "Eritrea"
        "ES" -> "Spain"
        "ET" -> "Ethiopia"
        "FI" -> "Finland"
        "FJ" -> "Fiji"
        "FK" -> "Falkland Islands"
        "FM" -> "Micronesia"
        "FO" -> "Faroe Islands"
        "FR" -> "France"
        "GA" -> "Gabon"
        "GB" -> "United Kingdom"
        "GB_ENG" -> "England"
        "GB_NIR" -> "Northern Ireland"
        "GB_SCT" -> "Scotland"
        "GB_WLS" -> "Wales"
        "GD" -> "Grenada"
        "GE" -> "Georgia"
        "GF" -> "French Guiana"
        "GG" -> "Guernsey"
        "GH" -> "Ghana"
        "GI" -> "Gibraltar"
        "GL" -> "Greenland"
        "GM" -> "Gambia"
        "GN" -> "Guinea"
        "GP" -> "Guadeloupe"
        "GQ" -> "Equatorial Guinea"
        "GR" -> "Greece"
        "GS" -> "South Georgia"
        "GT" -> "Guatemala"
        "GU" -> "Guam"
        "GW" -> "Guinea-Bissau"
        "GY" -> "Guyana"
        "HK" -> "Hong Kong"
        "HM" -> "Heard Island"
        "HN" -> "Honduras"
        "HR" -> "Croatia"
        "HT" -> "Haiti"
        "HU" -> "Hungary"
        "ID" -> "Indonesia"
        "IE" -> "Ireland"
        "IL" -> "Israel"
        "IM" -> "Isle of Man"
        "IN" -> "India"
        "IO" -> "British Indian Ocean Territory"
        "IQ" -> "Iraq"
        "IR" -> "Iran"
        "IS" -> "Iceland"
        "IT" -> "Italy"
        "JE" -> "Jersey"
        "JM" -> "Jamaica"
        "JO" -> "Jordan"
        "JP" -> "Japan"
        "KE" -> "Kenya"
        "KG" -> "Kyrgyzstan"
        "KH" -> "Cambodia"
        "KI" -> "Kiribati"
        "KM" -> "Comoros"
        "KN" -> "Saint Kitts and Nevis"
        "KP" -> "North Korea"
        "KR" -> "South Korea"
        "KW" -> "Kuwait"
        "KY" -> "Cayman Islands"
        "KZ" -> "Kazakhstan"
        "LA" -> "Laos"
        "LB" -> "Lebanon"
        "LC" -> "Saint Lucia"
        "LI" -> "Liechtenstein"
        "LK" -> "Sri Lanka"
        "LR" -> "Liberia"
        "LS" -> "Lesotho"
        "LT" -> "Lithuania"
        "LU" -> "Luxembourg"
        "LV" -> "Latvia"
        "LY" -> "Libya"
        "MA" -> "Morocco"
        "MC" -> "Monaco"
        "MD" -> "Moldova"
        "ME" -> "Montenegro"
        "MF" -> "Saint Martin"
        "MG" -> "Madagascar"
        "MH" -> "Marshall Islands"
        "MK" -> "North Macedonia"
        "ML" -> "Mali"
        "MM" -> "Myanmar"
        "MN" -> "Mongolia"
        "MO" -> "Macau"
        "MP" -> "Northern Mariana Islands"
        "MQ" -> "Martinique"
        "MR" -> "Mauritania"
        "MS" -> "Montserrat"
        "MT" -> "Malta"
        "MU" -> "Mauritius"
        "MV" -> "Maldives"
        "MW" -> "Malawi"
        "MX" -> "Mexico"
        "MY" -> "Malaysia"
        "MZ" -> "Mozambique"
        "NA" -> "Namibia"
        "NC" -> "New Caledonia"
        "NE" -> "Niger"
        "NF" -> "Norfolk Island"
        "NG" -> "Nigeria"
        "NI" -> "Nicaragua"
        "NL" -> "Netherlands"
        "NO" -> "Norway"
        "NP" -> "Nepal"
        "NR" -> "Nauru"
        "NU" -> "Niue"
        "NZ" -> "New Zealand"
        "OM" -> "Oman"
        "PA" -> "Panama"
        "PE" -> "Peru"
        "PF" -> "French Polynesia"
        "PG" -> "Papua New Guinea"
        "PH" -> "Philippines"
        "PK" -> "Pakistan"
        "PL" -> "Poland"
        "PM" -> "Saint Pierre and Miquelon"
        "PN" -> "Pitcairn Islands"
        "PR" -> "Puerto Rico"
        "PS" -> "Palestine"
        "PT" -> "Portugal"
        "PW" -> "Palau"
        "PY" -> "Paraguay"
        "QA" -> "Qatar"
        "RE" -> "Réunion"
        "RO" -> "Romania"
        "RS" -> "Serbia"
        "RU" -> "Russia"
        "RW" -> "Rwanda"
        "SA" -> "Saudi Arabia"
        "SB" -> "Solomon Islands"
        "SC" -> "Seychelles"
        "SD" -> "Sudan"
        "SE" -> "Sweden"
        "SG" -> "Singapore"
        "SH" -> "Saint Helena"
        "SI" -> "Slovenia"
        "SJ" -> "Svalbard and Jan Mayen"
        "SK" -> "Slovakia"
        "SL" -> "Sierra Leone"
        "SM" -> "San Marino"
        "SN" -> "Senegal"
        "SO" -> "Somalia"
        "SR" -> "Suriname"
        "SS" -> "South Sudan"
        "ST" -> "São Tomé and Príncipe"
        "SV" -> "El Salvador"
        "SX" -> "Sint Maarten"
        "SY" -> "Syria"
        "SZ" -> "Eswatini"
        "TC" -> "Turks and Caicos Islands"
        "TD" -> "Chad"
        "TF" -> "French Southern Territories"
        "TG" -> "Togo"
        "TH" -> "Thailand"
        "TJ" -> "Tajikistan"
        "TK" -> "Tokelau"
        "TL" -> "Timor-Leste"
        "TM" -> "Turkmenistan"
        "TN" -> "Tunisia"
        "TO" -> "Tonga"
        "TR" -> "Turkey"
        "TT" -> "Trinidad and Tobago"
        "TV" -> "Tuvalu"
        "TW" -> "Taiwan"
        "TZ" -> "Tanzania"
        "UA" -> "Ukraine"
        "UG" -> "Uganda"
        "UM" -> "U.S. Minor Outlying Islands"
        "US" -> "United States"
        "UY" -> "Uruguay"
        "UZ" -> "Uzbekistan"
        "VA" -> "Vatican City"
        "VC" -> "Saint Vincent and the Grenadines"
        "VE" -> "Venezuela"
        "VG" -> "British Virgin Islands"
        "VI" -> "U.S. Virgin Islands"
        "VN" -> "Vietnam"
        "VU" -> "Vanuatu"
        "WF" -> "Wallis and Futuna"
        "WS" -> "Samoa"
        "XK" -> "Kosovo"
        "YE" -> "Yemen"
        "YT" -> "Mayotte"
        "ZA" -> "South Africa"
        "ZM" -> "Zambia"
        "ZW" -> "Zimbabwe"
        else -> countryCode.uppercase()
    }
}
