// Copyright (c) 2019, Mine2Gether.com
//
// Please see the included LICENSE file for more information.
//
// Copyright (c) 2020 Scala
//
// Please see the included LICENSE file for more information.

package io.scalaproject.vault;

import android.content.SharedPreferences;

import java.util.HashMap;

public class Config {
    private static Config mSettings;
    private SharedPreferences preferences;

    public static final String CONFIG_KEY_CONFIG_VERSION = "config_version";
    public static final String CONFIG_KEY_HIDE_HOME_WIZARD = "hide_home_wizard";
    public static final String CONFIG_KEY_USER_SELECTED_NODE = "user_selected_node";

    public static final String version = "1";

    private HashMap<String,String> mConfigs = new HashMap<String, String>();

    static void initialize(SharedPreferences preferences) {
        mSettings = new Config();
        mSettings.preferences = preferences;
    }

    static void write(String key, String value) {
        if(!key.startsWith("system:")) {
            mSettings.preferences.edit().putString(key, value).apply();
        }

        if(value.isEmpty()) {
            return;
        }

        mSettings.mConfigs.put(key, value);
    }

    static void clear() {
        mSettings.preferences.edit().clear().apply();
        mSettings.mConfigs.clear();
    }

    public static String read(String key) {
        if(!key.startsWith("system:")) {
            return mSettings.preferences.getString(key, "");
        }
        if(!mSettings.mConfigs.containsKey(key)) {
            return "";
        }
        return mSettings.mConfigs.get(key);
    }

    public Config() {
    }
}
