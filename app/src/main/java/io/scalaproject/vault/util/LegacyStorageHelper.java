/*
 * Copyright 2018 m2049r
 * Copyright 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ////////////////
 *
 * Copyright (c) 2023 Scala
 *
 * Please see the included LICENSE file for more information.*/

package io.scalaproject.vault.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.preference.PreferenceManager;

import io.scalaproject.vault.BuildConfig;
import io.scalaproject.vault.model.WalletManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

public class LegacyStorageHelper {
    private final File srcDir;
    private final File dstDir;

    @SuppressLint("StaticFieldLeak")
    static private Context ctx = null;

    LegacyStorageHelper(File src, File dest) {
        srcDir = src;
        dstDir = dest;
    }

    static public void migrateWallets(Context context) {
        try {

            ctx = context;

            if (isStorageMigrated(context)) return;

            /*if (!getReadPermission(activity)) {
                Toast.makeText(context, "NO READ PERMISSIONS!!!", Toast.LENGTH_SHORT).show();
                // can't migrate - don't remember this, as the user may turn on permissions later
                return;
            }*/

            final File oldRoot = getWalletRoot();
            if (!oldRoot.exists()) {
                // nothing to migrate, so don't try again
                setStorageMigrated(context);
                return;
            }

            final File newRoot = Helper.getWalletRoot(context);
            (new LegacyStorageHelper(oldRoot, newRoot)).migrate();

            File[] wallets = newRoot.listFiles((dir, filename) -> filename.endsWith(".keys"));

            if (wallets == null) {
                return;
            }

            setStorageMigrated(context); // done it once - don't try again
        } catch (IllegalStateException ex) {
            Timber.d(ex);
            // nothing we can do here
        }
    }

    public void migrate() {
        String addressPrefix = WalletManager.getInstance().addressPrefix();

        File[] wallets = srcDir.listFiles((dir, filename) -> filename.endsWith(".keys"));

        if (wallets == null) {
            return;
        }
        for (File wallet : wallets) {
            final String walletName = wallet.getName().substring(0, wallet.getName().length() - ".keys".length());
            if (addressPrefix.indexOf(getAddress(walletName).charAt(0)) < 0) {
                Timber.d("skipping %s", walletName);
                continue;
            }
            try {
                copy(walletName);
            } catch (IOException ex) { // something failed - try to clean up
                deleteDst(walletName);
            }
        }
    }

    // return "@" by default so we don't need to deal with null stuff
    private String getAddress(String walletName) {
        File addressFile = new File(srcDir, walletName + ".address.txt");
        if (!addressFile.exists()) return "@";
        try (BufferedReader addressReader = new BufferedReader(new FileReader(addressFile))) {
            return addressReader.readLine();
        } catch (IOException ex) {
            Timber.d(ex.getLocalizedMessage());
        }
        return "@";
    }

    private void copy(String walletName) throws IOException {
        final String dstName = getUniqueName(dstDir, walletName);
        copyFile(new File(srcDir, walletName), new File(dstDir, dstName));
        copyFile(new File(srcDir, walletName + ".keys"), new File(dstDir, dstName + ".keys"));
    }

    private void deleteDst(String walletName) {
        // do our best, but if it fails, it fails
        (new File(dstDir, walletName)).delete();
        (new File(dstDir, walletName + ".keys")).delete();
        (new File(dstDir, walletName + ".address.txt")).delete();
    }

    private void copyFile(File src, File dst) throws IOException {
        if (!src.exists()) return;
        Timber.d("%s => %s", src.getAbsolutePath(), dst.getAbsolutePath());

        try (FileChannel inChannel = new FileInputStream(src).getChannel();
             FileChannel outChannel = new FileOutputStream(dst).getChannel()) {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        }
    }

    private static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    // Checks if a volume containing external storage is available to at least read.
    private boolean isExternalStorageReadable() {
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) ||
                Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED_READ_ONLY);
    }

    private static File getWalletRoot() {
        if (!isExternalStorageWritable())
            throw new IllegalStateException();

        // wallet folder for legacy (pre-Q) installations
        final String FLAVOR_SUFFIX =
                "." + BuildConfig.FLAVOR
                        + (BuildConfig.DEBUG ? "-debug" : "");

        final String WALLET_DIR = "scala" + FLAVOR_SUFFIX;

        File dir = new File(Environment.getExternalStorageDirectory(), WALLET_DIR);
        if (!dir.exists() || !dir.isDirectory())
            throw new IllegalStateException();
        return dir;
    }

    static public final int PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1;

    static public boolean getReadPermission(Activity context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                Timber.w("Permission denied to READ_EXTERNAL_STORAGE - requesting it");
                String[] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE};
                context.requestPermissions(permissions, PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
                return false;
            } else {
                return true;
            }
        } else {
            return true;
        }
    }

    private static final Pattern WALLET_PATTERN = Pattern.compile("^(.+) \\(([0-9]+)\\).keys$");

    private static String getUniqueName(File root, String name) {
        if (!(new File(root, name + ".keys")).exists()) // <name> does not exist => it's ok to use
            return name;

        File[] wallets = root.listFiles(
                (dir, filename) -> {
                    Matcher m = WALLET_PATTERN.matcher(filename);
                    if (m.find())
                        return Objects.equals(m.group(1), name);
                    else return false;
                });
        assert wallets != null;
        if (wallets.length == 0) return name + " (1)";
        int maxIndex = 0;
        for (File wallet : wallets) {
            try {
                final Matcher m = WALLET_PATTERN.matcher(wallet.getName());
                if (!m.find())
                    throw new IllegalStateException("this must match as it did before");
                final int index = Integer.parseInt(m.group(2));
                if (index > maxIndex) maxIndex = index;
            } catch (NumberFormatException ex) {
                // this cannot happen & we can ignore it if it does
            }
        }
        return name + " (" + (maxIndex + 1) + ")";
    }

    private static final String MIGRATED_KEY = "migrated_legacy_storage";

    public static boolean isStorageMigrated(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(MIGRATED_KEY, false);
    }

    public static void setStorageMigrated(Context context) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(MIGRATED_KEY, true).apply();
    }
}
