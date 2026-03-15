/*
 * Copyright (c) 2012-2015 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package com.mavodev.openvpnneo.fragments;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import com.mavodev.openvpnneo.R;
import com.mavodev.openvpnneo.VpnProfile;
import com.mavodev.openvpnneo.core.ProfileManager;

public abstract class Settings_Fragment extends Fragment {

    protected VpnProfile mProfile;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String profileUuid = getArguments().getString(getActivity().getPackageName() + ".profileUUID");
        mProfile = ProfileManager.get(getActivity(), profileUuid);
        getActivity().setTitle(getString(R.string.edit_profile_title, mProfile.getName()));
    }

    @Override
    public void onPause() {
        super.onPause();
        savePreferences();
    }

    protected abstract void savePreferences();
}
