/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package com.mavodev.openvpnneo.fragments;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.text.HtmlCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.mavodev.openvpnneo.BuildConfig;
import com.mavodev.openvpnneo.core.NativeUtils;

import java.io.*;
import java.util.Locale;

import com.mavodev.openvpnneo.R;
import kotlin.text.Charsets;

public class AboutFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.about, container, false);
        TextView ver = (TextView) v.findViewById(R.id.version);

        String version;
        String name = "Openvpn";
        try {
            PackageInfo packageinfo = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);
            version = packageinfo.versionName;
            name = getString(R.string.app);
        } catch (NameNotFoundException e) {
            version = "error fetching version";
        }

        ver.setText(getString(R.string.version_info, name, version));

        TextView verO2 = v.findViewById(R.id.version_ovpn2);
        TextView verO3 = v.findViewById(R.id.version_ovpn3);
        TextView osslVer = v.findViewById(R.id.openssl_version);

        verO2.setText(String.format(Locale.US, "OpenVPN version: %s", NativeUtils.getOpenVPN2GitVersion()));
        if (BuildConfig.openvpn3)
            verO3.setText(String.format(Locale.US, "OpenVPN3 core version: %s", NativeUtils.getOpenVPN3GitVersion()));
        else
            verO3.setText("(OpenVPN 2.x only build. No OpenVPN 3.x core in this app)");


        osslVer.setText(String.format(Locale.US, "OpenSSL version: %s", NativeUtils.getOpenSSLVersion()));



        /* recreating view without onCreate/onDestroy cycle */
        TextView translation = (TextView) v.findViewById(R.id.translation);

        // Don't print a text for myself
        if (getString(R.string.translationby).contains("Arne Schwabe"))
            translation.setText("");
        else
            translation.setText(R.string.translationby);

        TextView wv = (TextView) v.findViewById(R.id.full_licenses);
        wv.setText(HtmlCompat.fromHtml(readHtmlFromAssets(), HtmlCompat.FROM_HTML_MODE_LEGACY));



        ViewCompat.setOnApplyWindowInsetsListener(v, (view, windowInsets) ->
                {
                    Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                    view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), insets.bottom);
                    return WindowInsetsCompat.CONSUMED;
                }
        );
        return v;
    }

    String readHtmlFromAssets()
    {
        InputStream mvpn;

        try {
            mvpn = getActivity().getAssets().open("full_licenses.html");
            BufferedReader reader = new BufferedReader(new InputStreamReader(mvpn, Charsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (IOException errabi) {
            return "full_licenses.html not found";
        }
    }
}
