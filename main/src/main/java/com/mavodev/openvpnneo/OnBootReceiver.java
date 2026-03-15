/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package com.mavodev.openvpnneo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.mavodev.openvpnneo.core.Preferences;
import com.mavodev.openvpnneo.core.ProfileManager;
import com.mavodev.openvpnneo.core.VPNLaunchHelper;


public class OnBootReceiver extends BroadcastReceiver {
	// Debug: am broadcast -a android.intent.action.BOOT_COMPLETED
	@Override
	public void onReceive(Context context, Intent intent) {

		final String action = intent.getAction();
		SharedPreferences prefs = Preferences.getDefaultSharedPreferences(context);

		boolean alwaysActive = prefs.getBoolean("restartvpnonboot", false);
		if (!alwaysActive)
			return;

		if(Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
			VpnProfile bootProfile = ProfileManager.getAlwaysOnVPN(context);
			if(bootProfile != null) {
				launchVPN(bootProfile, context);
			}		
		}
	}

	void launchVPN(VpnProfile profile, Context context) {
		VPNLaunchHelper.startOpenVpn(profile, context.getApplicationContext(), "on Boot receiver", false);
	}
}
