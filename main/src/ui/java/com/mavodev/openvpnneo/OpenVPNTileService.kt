/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo

import android.annotation.TargetApi
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.mavodev.openvpnneo.core.ConnectionStatus
import com.mavodev.openvpnneo.core.IOpenVPNServiceInternal
import com.mavodev.openvpnneo.core.OpenVPNService
import com.mavodev.openvpnneo.core.ProfileManager
import com.mavodev.openvpnneo.core.VPNLaunchHelper
import com.mavodev.openvpnneo.core.VpnStatus

/**
 * Created by arne on 22.04.16.
 */
@TargetApi(Build.VERSION_CODES.N)
class OpenVPNTileService : TileService(), VpnStatus.StateListener {

    override fun onClick() {
        super.onClick()
        val bootProfile = getQSVPN()
        if (bootProfile == null) {
            Toast.makeText(this, R.string.novpn_selected, Toast.LENGTH_SHORT).show()
        } else {
            if (!isLocked) {
                clickAction(bootProfile)
            } else {
                unlockAndRun { clickAction(bootProfile) }
            }
        }
    }

    private fun clickAction(bootProfile: VpnProfile) {
        if (VpnStatus.isVPNActive()) {
            val intent = Intent(this, OpenVPNService::class.java)
            intent.action = OpenVPNService.START_SERVICE
            bindService(intent, object : ServiceConnection {
                override fun onServiceConnected(componentName: ComponentName?, binder: IBinder?) {
                    val service = IOpenVPNServiceInternal.Stub.asInterface(binder)
                    if (service != null) {
                        try {
                            service.stopVPN(false)
                        } catch (e: RemoteException) {
                            VpnStatus.logException(e)
                        }
                    }
                    unbindService(this)
                }

                override fun onServiceDisconnected(componentName: ComponentName?) {}
            }, Context.BIND_AUTO_CREATE)
        } else {
            launchVPN(bootProfile, this)
        }
    }

    fun launchVPN(profile: VpnProfile, context: Context) {
        VPNLaunchHelper.startOpenVpn(profile, baseContext, "QuickTile", true)
    }

    override fun onTileAdded() {}

    override fun onStartListening() {
        super.onStartListening()
        VpnStatus.addStateListener(this)
    }

    fun getQSVPN(): VpnProfile? = ProfileManager.getAlwaysOnVPN(this)

    override fun updateState(
        state: String?, logmessage: String?, localizedResId: Int, level: ConnectionStatus?, intent: Intent?
    ) {
        val t = qsTile ?: return

        // Set icon for the tile
        t.icon = Icon.createWithResource(this, R.drawable.ic_quick)

        t.label = getString(R.string.app_launcher)

        if (level == ConnectionStatus.LEVEL_AUTH_FAILED || level == ConnectionStatus.LEVEL_NOTCONNECTED) {
            // No VPN connected, use standard VPN
            if (getQSVPN() == null) {
                t.state = Tile.STATE_UNAVAILABLE
            } else {
                t.state = Tile.STATE_INACTIVE
            }
        } else {
            t.state = Tile.STATE_ACTIVE
        }

        t.updateTile()
    }

    override fun setConnectedVPN(uuid: String?) {}

    override fun onStopListening() {
        VpnStatus.removeStateListener(this)
        super.onStopListening()
    }
}
