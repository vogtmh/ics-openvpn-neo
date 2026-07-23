/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package com.mavodev.openvpnneo.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mavodev.openvpnneo.R;
import com.mavodev.openvpnneo.activities.VPNPreferences;
import com.mavodev.openvpnneo.core.Connection;

/**
 * A user friendly "Basic" tab exposing the most commonly used profile settings:
 * profile name, password, default profile, a read-only server overview and the
 * reconnection settings.
 */
public class Settings_Overview extends Settings_Fragment {

    /* keep in sync with @array/crm_values */
    private static final String[] CRM_VALUES = {"1", "2", "5", "50", "-1"};

    private EditText mProfileName;
    private EditText mPassword;
    private CompoundButton mMakeDefaultProfile;
    private Spinner mConnectRetryMax;
    private EditText mConnectRetry;
    private EditText mConnectRetryMaxTime;
    private RecyclerView mServerRecyclerView;
    private ServerOverviewAdapter mServerAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.basic_overview, container, false);

        mProfileName = v.findViewById(R.id.profilename);
        mPassword = v.findViewById(R.id.auth_password);
        mMakeDefaultProfile = v.findViewById(R.id.make_default_profile);
        mConnectRetryMax = v.findViewById(R.id.connectretrymax);
        mConnectRetry = v.findViewById(R.id.connectretry);
        mConnectRetryMaxTime = v.findViewById(R.id.connectretrymaxtime);

        mServerRecyclerView = v.findViewById(R.id.server_recycler_view);
        mServerRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mServerRecyclerView.setNestedScrollingEnabled(false);
        mServerAdapter = new ServerOverviewAdapter();
        mServerRecyclerView.setAdapter(mServerAdapter);

        loadPreferences();

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        // The server list may have been edited in the Server List tab
        if (mServerAdapter != null)
            mServerAdapter.notifyDataSetChanged();
    }

    private void loadPreferences() {
        mProfileName.setText(mProfile.mName);
        mPassword.setText(mProfile.mPassword);

        mConnectRetry.setText(mProfile.mConnectRetry);
        mConnectRetryMaxTime.setText(mProfile.mConnectRetryMaxTime);
        mConnectRetryMax.setSelection(getConnectRetryMaxIndex(mProfile.mConnectRetryMax));

        SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String currentDefaultUUID = defaultPrefs.getString("alwaysOnVpn", "");
        mMakeDefaultProfile.setChecked(mProfile.getUUIDString().equals(currentDefaultUUID));
    }

    @Override
    protected void savePreferences() {
        if (mProfile == null || mProfileName == null)
            return;

        mProfile.mName = mProfileName.getText().toString();
        mProfile.mPassword = mPassword.getText().toString();
        mProfile.mConnectRetry = mConnectRetry.getText().toString();
        mProfile.mConnectRetryMaxTime = mConnectRetryMaxTime.getText().toString();
        mProfile.mConnectRetryMax = CRM_VALUES[mConnectRetryMax.getSelectedItemPosition()];

        SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        SharedPreferences.Editor editor = defaultPrefs.edit();
        if (mMakeDefaultProfile.isChecked()) {
            editor.putString("alwaysOnVpn", mProfile.getUUIDString());
        } else {
            String currentDefaultUUID = defaultPrefs.getString("alwaysOnVpn", "");
            if (mProfile.getUUIDString().equals(currentDefaultUUID))
                editor.putString("alwaysOnVpn", "");
        }
        editor.apply();
    }

    private int getConnectRetryMaxIndex(String value) {
        for (int i = 0; i < CRM_VALUES.length; i++) {
            if (CRM_VALUES[i].equals(value))
                return i;
        }
        // default in the app is "5" reconnection retries
        return 2;
    }

    private void openServerListTab() {
        if (getActivity() instanceof VPNPreferences)
            ((VPNPreferences) getActivity()).showServerListTab();
    }

    private class ServerOverviewAdapter extends RecyclerView.Adapter<ServerOverviewAdapter.ServerHolder> {

        @Override
        public ServerHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View row = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.server_row_simple, parent, false);
            return new ServerHolder(row);
        }

        @Override
        public void onBindViewHolder(ServerHolder holder, int position) {
            Connection connection = mProfile.mConnections[position];
            String name = connection.mServerName;
            if (TextUtils.isEmpty(name))
                name = getString(R.string.no_remote_defined);
            holder.mServerName.setText(name);
            holder.mPort.setText(connection.mServerPort);
            holder.mServerName.setEnabled(connection.mEnabled);
            holder.mPort.setEnabled(connection.mEnabled);
            holder.itemView.setOnClickListener(view -> openServerListTab());
        }

        @Override
        public int getItemCount() {
            if (mProfile == null || mProfile.mConnections == null)
                return 0;
            return mProfile.mConnections.length;
        }

        class ServerHolder extends RecyclerView.ViewHolder {
            final TextView mServerName;
            final TextView mPort;

            ServerHolder(View itemView) {
                super(itemView);
                mServerName = itemView.findViewById(R.id.servername);
                mPort = itemView.findViewById(R.id.portnumber);
            }
        }
    }
}
