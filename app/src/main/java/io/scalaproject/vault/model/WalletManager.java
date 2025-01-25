/*
 * Copyright (c) 2017 m2049r
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ////////////////
 *
 * Copyright (c) 2020 Scala
 *
 * Please see the included LICENSE file for more information.*/

package io.scalaproject.vault.model;

import io.scalaproject.vault.ScalaVaultApplication;
import io.scalaproject.vault.data.Node;
import io.scalaproject.vault.ledger.Ledger;
import io.scalaproject.vault.util.RestoreHeight;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import timber.log.Timber;

public class WalletManager {

    static {
        System.loadLibrary("monerujo");
    }

    // no need to keep a reference to the REAL WalletManager (we get it every tvTime we need it)
    private static WalletManager Instance = null;

    public static synchronized WalletManager getInstance() {
        if (WalletManager.Instance == null) {
            WalletManager.Instance = new WalletManager();
        }

        return WalletManager.Instance;
    }

    public String addressPrefix() {
        return addressPrefix(getNetworkType());
    }

    static public String addressPrefix(NetworkType networkType) {
        return switch (networkType) {
            case NetworkType_Testnet -> "9A-";
            case NetworkType_Mainnet -> "S-";
            case NetworkType_Stagenet -> "5-";
            default -> throw new IllegalStateException("Unsupported Network: " + networkType);
        };
    }

    private Wallet managedWallet = null;

    public Wallet getWallet() {
        return managedWallet;
    }

    private void manageWallet(Wallet wallet) {
        Timber.d("Managing %s", wallet.getName());
        managedWallet = wallet;
    }

    private void unmanageWallet(Wallet wallet) {
        if (wallet == null) {
            throw new IllegalArgumentException("Cannot unmanage null!");
        }
        if (getWallet() == null) {
            throw new IllegalStateException("No wallet under management!");
        }
        if (getWallet() != wallet) {
            throw new IllegalStateException(wallet.getName() + " not under management!");
        }
        Timber.d("Unmanaging %s", managedWallet.getName());
        managedWallet = null;
    }

    public Wallet createWallet(File aFile, String password, String language, long height) {
        long walletHandle = createWalletJ(aFile.getAbsolutePath(), password, language, getNetworkType().getValue());
        Wallet wallet = new Wallet(walletHandle);
        manageWallet(wallet);
        if (wallet.getStatus().isOk()) {
            // (Re-)Estimate restore height based on what we know
            final long oldHeight = wallet.getRestoreHeight();
            final long restoreHeight =
                    (height > -1) ? height : RestoreHeight.getInstance().getHeight(new Date());
            wallet.setRestoreHeight(restoreHeight);
            Timber.d("Changed Restore Height from %d to %d", oldHeight, wallet.getRestoreHeight());
            wallet.setPassword(password); // this rewrites the keys file (which contains the restore height)
        }
        return wallet;
    }

    private native long createWalletJ(String path, String password, String language, int networkType);

    public Wallet openAccount(String path, int accountIndex, String password) {
        long walletHandle = openWalletJ(path, password, getNetworkType().getValue());
        Wallet wallet = new Wallet(walletHandle, accountIndex);
        manageWallet(wallet);
        return wallet;
    }

    public Wallet openWallet(String path, String password) {
        long walletHandle = openWalletJ(path, password, getNetworkType().getValue());
        Wallet wallet = new Wallet(walletHandle);
        manageWallet(wallet);
        return wallet;
    }

    private native long openWalletJ(String path, String password, int networkType);

    public Wallet recoveryWallet(File aFile, String password, String mnemonic) {
        return recoveryWallet(aFile, password, mnemonic, 0);
    }

    public Wallet recoveryWallet(File aFile, String password, String mnemonic, long restoreHeight) {
        long walletHandle = recoveryWalletJ(aFile.getAbsolutePath(), password, mnemonic,
                getNetworkType().getValue(), restoreHeight);
        Wallet wallet = new Wallet(walletHandle);
        manageWallet(wallet);
        return wallet;
    }

    private native long recoveryWalletJ(String path, String password, String mnemonic,
                                        int networkType, long restoreHeight);

    public Wallet createWalletWithKeys(File aFile, String password, String language, long restoreHeight,
                                       String addressString, String viewKeyString, String spendKeyString) {
        long walletHandle = createWalletFromKeysJ(aFile.getAbsolutePath(), password,
                language, getNetworkType().getValue(), restoreHeight,
                addressString, viewKeyString, spendKeyString);
        Wallet wallet = new Wallet(walletHandle);
        manageWallet(wallet);
        return wallet;
    }

    private native long createWalletFromKeysJ(String path, String password,
                                              String language,
                                              int networkType,
                                              long restoreHeight,
                                              String addressString,
                                              String viewKeyString,
                                              String spendKeyString);

    public Wallet createWalletFromDevice(File aFile, String password, long restoreHeight,
                                         String deviceName) {
        long walletHandle = createWalletFromDeviceJ(aFile.getAbsolutePath(), password,
                getNetworkType().getValue(), deviceName, restoreHeight,
                Ledger.SUBADDRESS_LOOKAHEAD);
        Wallet wallet = new Wallet(walletHandle);
        manageWallet(wallet);
        return wallet;
    }

    private native long createWalletFromDeviceJ(String path, String password,
                                                int networkType,
                                                String deviceName,
                                                long restoreHeight,
                                                String subaddressLookahead);


    public native boolean closeJ(Wallet wallet);

    public boolean close(Wallet wallet) {
        unmanageWallet(wallet);
        boolean closed = closeJ(wallet);
        if (!closed) {
            // in case we could not close it
            // we manage it again
            manageWallet(wallet);
        }
        return closed;
    }

    public boolean walletExists(File aFile) {
        return walletExists(aFile.getAbsolutePath());
    }

    public native boolean walletExists(String path);

    public native boolean verifyWalletPassword(String keys_file_name, String password, boolean watch_only);

    public boolean verifyWalletPasswordOnly(String keys_file_name, String password) {
        return queryWalletDeviceJ(keys_file_name, password) >= 0;
    }

    public Wallet.Device queryWalletDevice(String keys_file_name, String password) {
        int device = queryWalletDeviceJ(keys_file_name, password);
        return Wallet.Device.values()[device + 1]; // mapping is scala+1=android
    }

    private native int queryWalletDeviceJ(String keys_file_name, String password);



    public static class WalletInfo implements Comparable<WalletInfo> {
        public File path;
        public String name;

        public WalletInfo(File wallet) {
            path = wallet.getParentFile();
            name = wallet.getName();
        }

        @Override
        public int compareTo(WalletInfo another) {
            return name.toLowerCase().compareTo(another.name.toLowerCase());
        }
    }

    public List<WalletInfo> findWallets(File path) {
        List<WalletInfo> wallets = new ArrayList<>();
        Timber.d("Scanning: %s", path.getAbsolutePath());
        File[] found = path.listFiles((dir, filename) -> filename.endsWith(".keys"));

        assert found != null;
        for (File file : found) {
            String filename = file.getName();
            File f = new File(file.getParent(), filename.substring(0, filename.length() - 5)); // 5 is length of ".keys"+1
            wallets.add(new WalletInfo(f));
        }
        return wallets;
    }

    private String daemonAddress = null;
    private final NetworkType networkType = ScalaVaultApplication.getNetworkType();

    public NetworkType getNetworkType() {
        return networkType;
    }

    public void setDaemon(Node node) {
        if (node != null) {
            this.daemonAddress = node.getAddress();
            if (networkType != node.getNetworkType())
                throw new IllegalArgumentException("network type does not match");
            this.daemonUsername = node.getUsername();
            this.daemonPassword = node.getPassword();
            setDaemonAddressJ(daemonAddress);
        } else {
            this.daemonAddress = null;
            this.daemonUsername = "";
            this.daemonPassword = "";
        }
    }

    public String getDaemonAddress() {
        if (daemonAddress == null) {
            throw new IllegalStateException("use setDaemon() to initialise daemon and net first!");
        }
        return this.daemonAddress;
    }

    private native void setDaemonAddressJ(String address);

    private String daemonUsername = "";

    public String getDaemonUsername() {
        return daemonUsername;
    }

    private String daemonPassword = "";

    public String getDaemonPassword() {
        return daemonPassword;
    }

    public native boolean isMining();


    static public native void initLogger(String argv0, String defaultLogBaseName);

    //TODO: maybe put these in an enum like in scala core - but why?
    static public int LOGLEVEL_SILENT = -1;
    static public int LOGLEVEL_WARN = 0;
    static public int LOGLEVEL_INFO = 1;
    static public int LOGLEVEL_DEBUG = 2;
    static public int LOGLEVEL_TRACE = 3;
    static public int LOGLEVEL_MAX = 4;

    static public native void setLogLevel(int level);

    static public native String scalaVersion();
}