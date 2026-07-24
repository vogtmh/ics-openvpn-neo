/*
 * Copyright (c) 2012-2025 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package com.mavodev.openvpnneo.core;

/* This class is a data holder for global app preferences */
public class GlobalPreferences {
    boolean forceConnected = false;

    static GlobalPreferences instance = null;

    GlobalPreferences(boolean forceConnected)
    {
        this.forceConnected = forceConnected;
    }

    public static void setInstance(boolean forceConnected)
    {
        instance = new GlobalPreferences(forceConnected);
    }

    static public boolean getForceConnected()
    {
        return getInstance().forceConnected;
    }

    static GlobalPreferences getInstance()
    {
        if (instance == null)
            instance = new GlobalPreferences(false);

        return instance;
    }
}
