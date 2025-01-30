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

package io.scalaproject.vault;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.MediaScannerConnection;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.acra.ACRA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.scalaproject.vault.data.Node;
import io.scalaproject.vault.data.NodeInfo;
import io.scalaproject.vault.dialog.AboutFragment;
import io.scalaproject.vault.dialog.CreditsFragment;
import io.scalaproject.vault.dialog.HelpFragment;
import io.scalaproject.vault.dialog.PrivacyFragment;
import io.scalaproject.vault.ledger.Ledger;
import io.scalaproject.vault.ledger.LedgerProgressDialog;
import io.scalaproject.vault.model.NetworkType;
import io.scalaproject.vault.model.Wallet;
import io.scalaproject.vault.model.WalletManager;
import io.scalaproject.vault.service.WalletService;
import io.scalaproject.vault.util.Helper;
import io.scalaproject.vault.util.KeyStoreHelper;
import io.scalaproject.vault.util.LocaleHelper;
import io.scalaproject.vault.util.Notice;
import io.scalaproject.vault.util.ScalaThreadPoolExecutor;
import io.scalaproject.vault.widget.Toolbar;
import io.scalaproject.vault.util.LegacyStorageHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import timber.log.Timber;

public class LoginActivity extends BaseActivity
        implements LoginFragment.Listener, GenerateFragment.Listener,
        GenerateReviewFragment.Listener, GenerateReviewFragment.AcceptListener,
        ReceiveFragment.Listener, NodeFragment.Listener {
    private static final String GENERATE_STACK = "gen";

    static private final String DEFAULT_NODES_REPOSITORY = "https://raw.githubusercontent.com/scala-network/ScalaVault/master/app.json";

    // USAGE: The GitHub raw file might not be available in some countries, so the IPFS option
    //        is a fallback. When the DEFAULT_NODES_REPOSITORY file is modified, we need to upload the new file
    //        to the IPNS gateway as well.
    static private final String IPNS_NAME = "node-list.scala.network";
    static private final String[] NODES_REPOSITORY_IPNS_GATEWAYS = {
            "https://cloudflare-ipfs.com/ipns/",
            "https://ipfs.io/ipns/",
            "https://gateway.ipfs.io/ipns/",
            "https://dweb.link/ipns/"
    };

    static private final String DEFAULT_NODE = "{\n" +
            "\"nodes\": [\n" +
            "{\n" +
            "\"host\": \"remote.one.scala.network\",\n" +
            "\"port\": \"11812\"\n" +
            "} ]\n" +
            "}";

    private static final String NODES_USERDEFINED_NAME = "userdefined_nodes";
    private static final Logger log = LoggerFactory.getLogger(LoginActivity.class);

    static public boolean useDefaultNode = false;

    private NodeInfo node = null;

    Set<NodeInfo> defaultNodes = new HashSet<>();
    Set<NodeInfo> userDefinedNodes = new HashSet<>();

    private boolean startCreateWalletFragment = false;
    private String generateFragmentType = "";


    @Override
    public NodeInfo getNode() {
        return node;
    }

    @Override
    public void setNode(NodeInfo node) {
        if ((node != null) && (node.getNetworkType() != WalletManager.getInstance().getNetworkType())) {
            throw new IllegalArgumentException("network type does not match");
        }

        this.node = node;

        WalletManager.getInstance().setDaemon(node);
    }

    public Set<NodeInfo> getAllNodes() {
        Set<NodeInfo> allNodes = new HashSet<>();

        allNodes.addAll(defaultNodes);
        allNodes.addAll(userDefinedNodes);

        return allNodes;
    }

    @Override
    public void addUserDefinedNodes(Set<NodeInfo> nodes) {
        Timber.d("adding %d nodes", nodes.size());

        for (NodeInfo node : nodes) {
            Timber.d("adding %s %b", node, node.isUserDefined());
            if (node.isUserDefined()) // just to be sure
                userDefinedNodes.add(node);
        }

        saveUserDefinedNodes();
    }

    @Override
    public void deleteUserDefinedNode(NodeInfo node) {
        Timber.d("deleting %s node", node.getName());

        if (node.isUserDefined()) // just to be sure
            userDefinedNodes.remove(node);

        saveUserDefinedNodes();
    }

    public void loadNodesWithNetwork() {
        Helper.runWithNetwork(() -> {
            loadAllNodes();
            return true;
        });
    }

    private void loadAllNodes() {
        Timber.d("loadAllNodes");

        loadUserDefinedNodes();
        loadDefaultNodes();
    }

    private void loadUserDefinedNodes() {
        userDefinedNodes.clear();

        // Load Userdefined nodes
        Map<String, ?> userdefinedNodes = getSharedPreferences(NODES_USERDEFINED_NAME, Context.MODE_PRIVATE).getAll();
        for (Map.Entry<String, ?> nodeEntry : userdefinedNodes.entrySet()) {
            if (nodeEntry != null) // just in case, ignore possible future errors
                addNode((String) nodeEntry.getValue(), true);
        }
    }

    private void saveUserDefinedNodes() {
        Timber.d("Save Userdefined nodes");

        if(userDefinedNodes.isEmpty())
            return;

        SharedPreferences.Editor editor = getSharedPreferences(NODES_USERDEFINED_NAME, Context.MODE_PRIVATE).edit();
        editor.clear();

        int i = 1;
        for (Node info : userDefinedNodes) {
            if(info.isUserDefined()) {
                String nodeString = info.toNodeString();
                editor.putString(Integer.toString(i), nodeString);
                Timber.d("saved %d:%s", i, nodeString);
                i++;
            }
        }

        editor.apply();
    }

    private void addNode(String nodeString) { addNode(nodeString, false); }

    private void addNode(String nodeString, Boolean userdefined) {
        NodeInfo nodeInfo = NodeInfo.fromString(nodeString);
        if (nodeInfo != null) {
            nodeInfo.setUserDefined(userdefined);

            if(userdefined)
                userDefinedNodes.add(nodeInfo);
            else
                defaultNodes.add(nodeInfo);
        } else
            Timber.w("nodeString invalid: %s", nodeString);
    }

    public void loadDefaultNodes() {
        defaultNodes.clear();
        showProgressDialog(R.string.about_version);
        String jsonString = "";
        Timber.tag("loadDefaultNodes").d("loadDefaultNodes start");
        // Load Pools data from the GitHub repository by default
        if(Helper.isURLReachable(DEFAULT_NODES_REPOSITORY))
            jsonString  = Helper.fetchJSON(DEFAULT_NODES_REPOSITORY);
        Timber.tag("loadDefaultNodes").d("loadDefaultNodes from GitHub");

        // If GitHub file is not available or is blocked by firewalls, use IPFS
        if(jsonString.isEmpty()) {
            for (String strNodeURLDir : NODES_REPOSITORY_IPNS_GATEWAYS) {
                String strNodeURL = strNodeURLDir + IPNS_NAME;
                if(Helper.isURLReachable(strNodeURL)) {
                    Timber.tag("loadDefaultNodes").d("loadDefaultNodes from IPFS");
                    jsonString = Helper.fetchJSON(strNodeURL);
                    if (!jsonString.isEmpty())
                        break;
                }
            }
        }

        // None of the URL can be reached. Load default data but don't cache it.
        if(jsonString.isEmpty()) {
            useDefaultNode = true;
            jsonString = DEFAULT_NODE;
            Timber.tag("loadDefaultNodes").d("loadDefaultNodes from default");
        } else {
            useDefaultNode = false;
        }

        try {
            JSONObject data = new JSONObject(jsonString);
            JSONArray pools = data.getJSONArray("nodes");

            for(int i = 0; i < pools.length(); i++) {
                JSONObject node = pools.getJSONObject(i);

                if(node.has("host") && node.has("port")) {
                    addNode(node.getString("host") + ":" + node.getString("port"));
                    //setNode(NodeInfo.fromString(node.getString("host") + ":" + node.getString("port")));
                }
            }
        } catch (JSONException e) {
            //e.printStackTrace();
        }
        Timber.tag("loadDefaultNodes").d("loadDefaultNodes end");
    }

    private Toolbar toolbar;

    @Override
    public void setToolbarButton(int type) {
        toolbar.setButton(type);
    }

    @Override
    public void setTitle(String title) {
        toolbar.setTitle(title);
    }

    @Override
    public void setSubtitle(String subtitle) {
        toolbar.setSubtitle(subtitle);
    }

    @Override
    public void setTitle(String title, String subtitle) {
        toolbar.setTitle(title, subtitle);
    }

    @Override
    public boolean hasLedger() {
        return Ledger.isConnected();
    }

    @Override
    public void showProgressDialog(final Integer msg_id) {
        super.showProgressDialog(msg_id);
    }

    @Override
    public void hideProgressDialog() {
        super.dismissProgressDialog();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Timber.d("onCreate()");
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            // we don't store anything ourselves
        }

        setContentView(R.layout.activity_login);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(false);

        toolbar.setOnButtonListener(type -> {
            switch (type) {
                case Toolbar.BUTTON_BACK:
                    onBackPressed();
                    break;
                case Toolbar.BUTTON_CLOSE:
                    finish();
                    break;
                case Toolbar.BUTTON_CREDITS:
                    CreditsFragment.display(getSupportFragmentManager());
                    break;
                case Toolbar.BUTTON_NONE:
                    break;
                default:
                    Timber.e("Button " + type + " pressed - how can this be?");
            }
        });

        // try intents
        Intent intent = getIntent();

        // Get the connection state
        int connectionState = intent.getIntExtra("connection_state", -1);

        // Use the connection state as needed
        if (connectionState == 1) {
            Timber.tag("LoginActivity").d("Connection available");
            // Add logic for connection available
            LoginFragment.setConnectionStatus(connectionState);
        } else if (connectionState == 0) {
            Timber.tag("LoginActivity").d("No connection");
            // Add logic for no connection

            // Notice to user we require internet to work properly
            // Make the notice persistance...
            LoginFragment.setConnectionStatus(connectionState);
        } else {
            Timber.tag("LoginActivity").d("Connection state unknown");
            // Handle unknown connection state
            LoginFragment.setConnectionStatus(connectionState);
        }

        if (!processUsbIntent(intent))
            processUriIntent(intent);

        if (LegacyStorageHelper.getReadPermission(this)) {
            if (savedInstanceState == null) {
                startLoginFragment();
            }
        } else {
            Timber.i("Waiting for permissions");
        }

        // If activity is created from Home Wizard
        String generateFragmentTypeTmp = intent.getStringExtra("GenerateFragmentType");
        if (generateFragmentTypeTmp != null && !generateFragmentTypeTmp.isEmpty()) {
            startCreateWalletFragment = true;
            generateFragmentType = generateFragmentTypeTmp;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        Timber.d("onRequestPermissionsResult()");
        if (requestCode == LegacyStorageHelper.PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE) {// If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                isPermissionGranted = true;
            } else {
                String msg = getString(R.string.message_strorage_not_permitted);
                Timber.e(msg);
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            }
        }
    }

    // TODO check all reasons to call this.
    boolean checkServiceRunning() {
        if (WalletService.Running) {
            Toast.makeText(this, getString(R.string.service_busy), Toast.LENGTH_SHORT).show();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onWalletSelected(String walletName, boolean stealthMode) {
        if (node == null) {
            Toast.makeText(this, getString(R.string.prompt_daemon_missing), Toast.LENGTH_SHORT).show();
            return;
        }
        if (checkServiceRunning()) return;
        try {
            new AsyncOpenWallet(walletName, node, stealthMode).execute();
        } catch (IllegalArgumentException ex) {
            Timber.e(ex.getLocalizedMessage());
            Toast.makeText(this, ex.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onWalletDetails(final String walletName) {
        Timber.d("details for wallet .%s.", walletName);
        if (checkServiceRunning()) return;
        DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    final File walletFile = Helper.getWalletFile(LoginActivity.this, walletName);
                    if (WalletManager.getInstance().walletExists(walletFile)) {
                        Helper.promptPassword(LoginActivity.this, walletName, true, (walletName1, password, fingerprintUsed) -> {
                            if (checkDevice(walletName1, password))
                                startDetails(walletFile, password, GenerateReviewFragment.VIEW_TYPE_DETAILS);
                        });
                    } else { // this cannot really happen as we prefilter choices
                        Timber.e("Wallet missing: %s", walletName);
                        Toast.makeText(LoginActivity.this, getString(R.string.bad_wallet), Toast.LENGTH_SHORT).show();
                    }
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    // do nothing
                    break;
            }
        };

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogCustom);
        builder.setMessage(getString(R.string.details_alert_message))
                .setPositiveButton(getString(R.string.details_alert_yes), dialogClickListener)
                .setNegativeButton(getString(R.string.details_alert_no), dialogClickListener)
                .show();
    }

    @Override
    public void onWalletReceive(String walletName) {
        Timber.d("receive for wallet .%s.", walletName);
        if (checkServiceRunning()) return;
        final File walletFile = Helper.getWalletFile(this, walletName);
        if (WalletManager.getInstance().walletExists(walletFile)) {
            Helper.promptPassword(LoginActivity.this, walletName, false, (walletName1, password, fingerprintUsed) -> {
                if (checkDevice(walletName1, password))
                    startReceive(walletFile, password);
            });
        } else { // this cannot really happen as we prefilter choices
            Toast.makeText(this, getString(R.string.bad_wallet), Toast.LENGTH_SHORT).show();
        }
    }

    private void renameWallet(String oldName, String newName) {
        File walletFile = Helper.getWalletFile(this, oldName);
        boolean success = renameWallet(walletFile, newName);
        if (success) {
            reloadWalletList();
        } else {
            Toast.makeText(LoginActivity.this, getString(R.string.rename_failed), Toast.LENGTH_LONG).show();
        }
    }

    // copy + delete seems safer than rename because we can rollback easily
    boolean renameWallet(File walletFile, String newName) {
        if (copyWallet(walletFile, new File(walletFile.getParentFile(), newName), false)) {
            try {
                KeyStoreHelper.copyWalletUserPass(this, walletFile.getName(), newName);
            } catch (KeyStoreHelper.BrokenPasswordStoreException ex) {
                Timber.w(ex);
            }
            deleteWallet(walletFile); // also deletes the keystore entry
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onWalletRename(final String walletName) {
        Timber.d("rename for wallet ." + walletName + ".");
        if (checkServiceRunning()) return;
        LayoutInflater li = LayoutInflater.from(this);
        View promptsView = li.inflate(R.layout.prompt_rename, null);

        MaterialAlertDialogBuilder alertDialogBuilder = new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogCustom);
        alertDialogBuilder.setView(promptsView);

        final EditText etRename = promptsView.findViewById(R.id.etRename);
        final TextView tvRenameLabel = promptsView.findViewById(R.id.tvRenameLabel);

        tvRenameLabel.setText(getString(R.string.prompt_rename, walletName));

        // set dialog message
        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton(getString(R.string.label_ok),
                        (dialog, id) -> {
                            Helper.hideKeyboardAlways(LoginActivity.this);
                            String newName = etRename.getText().toString();
                            renameWallet(walletName, newName);
                        })
                .setNegativeButton(getString(R.string.label_cancel),
                        (dialog, id) -> {
                            Helper.hideKeyboardAlways(LoginActivity.this);
                            dialog.cancel();
                        });

        final androidx.appcompat.app.AlertDialog dialog = alertDialogBuilder.create();
        Helper.showKeyboard(dialog);

        // accept keyboard "ok"
        etRename.setOnEditorActionListener((v, actionId, event) -> {
            if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) && (event.getAction() == KeyEvent.ACTION_DOWN))
                    || (actionId == EditorInfo.IME_ACTION_DONE)) {
                Helper.hideKeyboardAlways(LoginActivity.this);
                String newName = etRename.getText().toString();
                dialog.cancel();
                renameWallet(walletName, newName);
                return false;
            }
            return false;
        });

        dialog.show();
    }

    @SuppressLint("StaticFieldLeak")
    private class AsyncBackup extends AsyncTask<String, Void, Boolean> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showProgressDialog(R.string.backup_progress);
        }

        @Override
        protected Boolean doInBackground(String... params) {
            if (params.length != 1) return false;
            return backupWallet(params[0]);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (isDestroyed()) {
                return;
            }
            dismissProgressDialog();
            if (!result) {
                Toast.makeText(LoginActivity.this, getString(R.string.backup_failed), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(LoginActivity.this, getString(R.string.backup_success), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean backupWallet(String walletName) {
        File backupFolder = new File(getStorageRoot(), "backups");
        if (!backupFolder.exists()) {
            if (!backupFolder.mkdir()) {
                Timber.e("Cannot create backup dir %s", backupFolder.getAbsolutePath());
                return false;
            }
            // make folder visible over USB/MTP
            MediaScannerConnection.scanFile(this, new String[]{backupFolder.toString()}, null, null);
        }
        File walletFile = Helper.getWalletFile(LoginActivity.this, walletName);
        File backupFile = new File(backupFolder, walletName);
        Timber.d("backup " + walletFile.getAbsolutePath() + " to " + backupFile.getAbsolutePath());
        // TODO probably better to copy to a new file and then rename
        // then if something fails we have the old backup at least
        // or just create a new backup every time and keep n old backups
        boolean success = copyWallet(walletFile, backupFile, true);
        Timber.d("copyWallet is %s", success);
        return success;
    }

    @Override
    public void onWalletBackup(String walletName) {
        Timber.d("backup for wallet ." + walletName + ".");
        new AsyncBackup().execute(walletName);
    }

    @SuppressLint("StaticFieldLeak")
    private class AsyncArchive extends AsyncTask<String, Void, Boolean> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showProgressDialog(R.string.archive_progress);
        }

        @Override
        protected Boolean doInBackground(String... params) {
            if (params.length != 1) return false;
            String walletName = params[0];
            if (backupWallet(walletName) && deleteWallet(Helper.getWalletFile(LoginActivity.this, walletName))) {
                KeyStoreHelper.removeWalletUserPass(LoginActivity.this, walletName);
                return true;
            } else {
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (isDestroyed()) {
                return;
            }
            dismissProgressDialog();
            if (result) {
                reloadWalletList();
            } else {
                Toast.makeText(LoginActivity.this, getString(R.string.archive_failed), Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onWalletArchive(final String walletName) {
        Timber.d("archive for wallet ." + walletName + ".");
        if (checkServiceRunning()) return;
        DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    Helper.promptPassword(LoginActivity.this, walletName, false, (walletName1, password, fingerprintUsed) -> {
                        if (checkDevice(walletName1, password)) {
                            runOnUiThread(() -> new AsyncArchive().execute(walletName1));
                        }
                    });

                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    // do nothing
                    break;
            }
        };

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogCustom);
        builder.setMessage(getString(R.string.archive_alert_message))
                .setTitle(walletName)
                .setPositiveButton(getString(R.string.archive_alert_yes), dialogClickListener)
                .setNegativeButton(getString(R.string.archive_alert_no), dialogClickListener)
                .show();
    }

    void reloadWalletList() {
        Timber.d("reloadWalletList()");
        try {
            LoginFragment loginFragment = (LoginFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (loginFragment != null) {
                loginFragment.loadList();
            }
        } catch (ClassCastException ignored) {
        }
    }

    public void onWalletChangePassword() {//final String walletName, final String walletPassword) {
        try {
            GenerateReviewFragment detailsFragment = (GenerateReviewFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            assert detailsFragment != null;
            AlertDialog dialog = detailsFragment.createChangePasswordDialog();
            if (dialog != null) {
                Helper.showKeyboard(dialog);
                dialog.show();
            }
        } catch (ClassCastException ex) {
            Timber.w("onWalletChangePassword() called, but no GenerateReviewFragment active");
        }
    }

    @Override
    public void onAddWallet(String type) {
        if (checkServiceRunning()) return;
        startGenerateFragment(type);
    }

    @Override
    public void onNodePrefs() {
        Timber.d("node prefs");

        if (checkServiceRunning()) return;

        startNodeFragment();
    }

    ////////////////////////////////////////
    // LoginFragment.Listener
    ////////////////////////////////////////

    @Override
    public File getStorageRoot() {
        return Helper.getWalletRoot(getApplicationContext());
    }

    ////////////////////////////////////////
    ////////////////////////////////////////

    @Override
    public void showNet() {
        showNet(WalletManager.getInstance().getNetworkType());
    }

    private void showNet(NetworkType net) {
        switch (net) {
            case NetworkType_Mainnet:
                toolbar.setSubtitle(null);
                toolbar.setBackgroundResource(R.drawable.backgound_toolbar_mainnet);
                break;
            case NetworkType_Testnet:
                toolbar.setSubtitle(getString(R.string.connect_testnet));
                toolbar.setBackgroundResource(R.color.colorPrimaryDark);
                break;
            case NetworkType_Stagenet:
                /*toolbar.setSubtitle(getString(R.string.connect_stagenet));
                toolbar.setBackgroundResource(R.color.colorPrimaryDark);*/
                break;
            default:
                throw new IllegalStateException("NetworkType unknown: " + net);
        }
    }

    @Override
    protected void onPause() {
        Timber.d("onPause()");
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Timber.d("onDestroy");
        dismissProgressDialog();
        unregisterDetachReceiver();
        Ledger.disconnect();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Timber.d("onResume()");
        // wait for WalletService to finish
        if (WalletService.Running && (progressDialog == null)) {
            // and show a progress dialog, but only if there isn't one already
            new AsyncWaitForService().execute();
        }

        if (!Ledger.isConnected()) attachLedger();
    }

    @SuppressLint("StaticFieldLeak")
    private class AsyncWaitForService extends AsyncTask<Void, Void, Void> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showProgressDialog(R.string.service_progress);
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                while (WalletService.Running & !isCancelled()) {
                    Thread.sleep(250);
                }
            } catch (InterruptedException ex) {
                // oh well ...
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            if (isDestroyed()) {
                return;
            }

            dismissProgressDialog();
        }
    }

    void startWallet(String walletName, String walletPassword, boolean fingerprintUsed, boolean stealthMode) {
        Timber.d("startWallet()");
        Intent intent = new Intent(getApplicationContext(), WalletActivity.class);
        intent.putExtra(WalletActivity.REQUEST_ID, walletName);
        intent.putExtra(WalletActivity.REQUEST_PW, walletPassword);
        intent.putExtra(WalletActivity.REQUEST_FINGERPRINT_USED, fingerprintUsed);
        intent.putExtra(WalletActivity.REQUEST_STEALTHMODE, stealthMode);
        if (uri != null) {
            intent.putExtra(WalletActivity.REQUEST_URI, uri);
            uri = null; // use only once
        }
        startActivity(intent);
    }

    void startDetails(File walletFile, String password, String type) {
        Timber.d("startDetails()");
        Bundle b = new Bundle();
        b.putString("path", walletFile.getAbsolutePath());
        b.putString("password", password);
        b.putString("type", type);
        startReviewFragment(b);
    }

    void startReceive(File walletFile, String password) {
        Timber.d("startReceive()");
        Bundle b = new Bundle();
        b.putString("path", walletFile.getAbsolutePath());
        b.putString("password", password);
        startReceiveFragment(b);
    }

    private boolean isPermissionGranted = false;

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();

        if (isPermissionGranted) {
            startLoginFragment();

            if (startCreateWalletFragment && !generateFragmentType.isEmpty()) {
                onAddWallet(generateFragmentType);
                startCreateWalletFragment = false;
                generateFragmentType = "";
            }

            isPermissionGranted = false;
        }
    }

    void startLoginFragment() {
        // we set these here because we cannot be certain we have permissions for storage before
        Helper.setScalaHome(this);
        Helper.initLogger(this);

        Fragment fragment = new LoginFragment();
        getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, fragment).commit();

        Timber.d("LoginFragment added");
    }

    void startGenerateFragment(String type) {
        Bundle extras = new Bundle();
        extras.putString(GenerateFragment.TYPE, type);

        replaceFragment(new GenerateFragment(), GENERATE_STACK, extras);

        Timber.d("GenerateFragment placed");
    }

    void startReviewFragment(Bundle extras) {
        replaceFragment(new GenerateReviewFragment(), null, extras);

        Timber.d("GenerateReviewFragment placed");
    }

    void startNodeFragment() {
        replaceFragment(new NodeFragment(), null, null);

        Timber.d("NodeFragment placed");
    }

    void startReceiveFragment(Bundle extras) {
        replaceFragment(new ReceiveFragment(), null, extras);

        Timber.d("ReceiveFragment placed");
    }

    void replaceFragment(Fragment newFragment, String stackName, Bundle extras) {
        if (extras != null) {
            newFragment.setArguments(extras);
        }

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, newFragment);
        transaction.addToBackStack(stackName);
        transaction.commit();
    }

    void popFragmentStack(String name) {
        getSupportFragmentManager().popBackStack(name, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    //////////////////////////////////////////
    // GenerateFragment.Listener
    //////////////////////////////////////////
    static final String MNEMONIC_LANGUAGE = "English"; // see mnemonics/electrum-words.cpp for more

    @SuppressLint("StaticFieldLeak")
    private class AsyncCreateWallet extends AsyncTask<Void, Void, Boolean> {
        final String walletName;
        final String walletPassword;
        final WalletCreator walletCreator;

        File newWalletFile;

        AsyncCreateWallet(final String name, final String password, final WalletCreator walletCreator) {
            super();
            this.walletName = name;
            this.walletPassword = password;
            this.walletCreator = walletCreator;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            acquireWakeLock();
            if (walletCreator.isLedger()) {
                showLedgerProgressDialog(LedgerProgressDialog.TYPE_RESTORE);
            } else {
                showProgressDialog(R.string.generate_wallet_creating);
            }
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            // check if the wallet we want to create already exists
            File walletFolder = getStorageRoot();
            if (!walletFolder.isDirectory()) {
                Timber.e("Wallet dir " + walletFolder.getAbsolutePath() + "is not a directory");
                return false;
            }
            File cacheFile = new File(walletFolder, walletName);
            File keysFile = new File(walletFolder, walletName + ".keys");
            File addressFile = new File(walletFolder, walletName + ".address.txt");

            if (cacheFile.exists() || keysFile.exists() || addressFile.exists()) {
                Timber.e("Some wallet files already exist for %s", cacheFile.getAbsolutePath());
                return false;
            }

            newWalletFile = new File(walletFolder, walletName);

            boolean success = walletCreator.createWallet(newWalletFile, walletPassword);
            if (success) {
                return true;
            } else {
                Timber.e("Could not create new wallet in %s", newWalletFile.getAbsolutePath());
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            releaseWakeLock(RELEASE_WAKE_LOCK_DELAY);
            if (isDestroyed()) {
                return;
            }

            dismissProgressDialog();

            if (result) {
                startDetails(newWalletFile, walletPassword, GenerateReviewFragment.VIEW_TYPE_ACCEPT);
            } else {
                walletGenerateError();
            }
        }
    }

    public void createWallet(final String name, final String password, final WalletCreator walletCreator) {
        new AsyncCreateWallet(name, password, walletCreator).executeOnExecutor(ScalaThreadPoolExecutor.SCALA_THREAD_POOL_EXECUTOR);
    }

    void walletGenerateError() {
        try {
            GenerateFragment genFragment = (GenerateFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            assert genFragment != null;
            genFragment.walletGenerateError();
        } catch (ClassCastException ex) {
            Timber.e("walletGenerateError() but not in GenerateFragment");
        }
    }

    public interface WalletCreator {
        boolean createWallet(File aFile, String password);
        boolean isLedger();
    }

    boolean checkAndCloseWallet(Wallet aWallet) {
        Wallet.Status walletStatus = aWallet.getStatus();
        if (!walletStatus.isOk()) {
            Timber.e(walletStatus.getErrorString());
            toast(walletStatus.getErrorString());
        }

        aWallet.close();
        return walletStatus.isOk();
    }

    @Override
    public void onGenerate(final String name, final String password) {
        createWallet(name, password,
                new WalletCreator() {
                    @Override
                    public boolean isLedger() {
                        return false;
                    }

                    @Override
                    public boolean createWallet(File aFile, String password) {
                        NodeInfo currentNode = getNode();
                        // get it from the connected node if we have one, and go back ca. 4 days
                        final long restoreHeight = (currentNode != null) ? currentNode.getHeight() - 2000 : -1;
                        Wallet newWallet = WalletManager.getInstance().createWallet(aFile, password, MNEMONIC_LANGUAGE, restoreHeight);
                        return checkAndCloseWallet(newWallet);
                    }
                });
    }

    @Override
    public void onGenerate(final String name, final String password, final String seed, final long restoreHeight) {
        createWallet(name, password,
                new WalletCreator() {
                    @Override
                    public boolean isLedger() {
                        return false;
                    }

                    @Override
                    public boolean createWallet(File aFile, String password) {
                        Wallet newWallet = WalletManager.getInstance()
                                .recoveryWallet(aFile, password, seed, restoreHeight);
                        return checkAndCloseWallet(newWallet);
                    }
                });
    }

    @Override
    public void onGenerateLedger(final String name, final String password, final long restoreHeight) {
        createWallet(name, password,
                new WalletCreator() {
                    @Override
                    public boolean isLedger() {
                        return true;
                    }

                    @Override
                    public boolean createWallet(File aFile, String password) {
                        Wallet newWallet = WalletManager.getInstance()
                                .createWalletFromDevice(aFile, password,
                                        restoreHeight, "Ledger");
                        return checkAndCloseWallet(newWallet);
                    }
                });
    }

    @Override
    public void onGenerate(final String name, final String password,
                           final String address, final String viewKey, final String spendKey,
                           final long restoreHeight) {
        createWallet(name, password,
                new WalletCreator() {
                    @Override
                    public boolean isLedger() {
                        return false;
                    }

                    @Override
                    public boolean createWallet(File aFile, String password) {
                        Wallet newWallet = WalletManager.getInstance()
                                .createWalletWithKeys(aFile, password, MNEMONIC_LANGUAGE, restoreHeight,
                                        address, viewKey, spendKey);
                        return checkAndCloseWallet(newWallet);
                    }
                });
    }

    void toast(final String msg) {
        runOnUiThread(() -> Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_LONG).show());
    }

    void toast(final int msgId) {
        runOnUiThread(() -> Toast.makeText(LoginActivity.this, getString(msgId), Toast.LENGTH_LONG).show());
    }

    @Override
    public void onAccept(final String name, final String password) {
        File walletFolder = getStorageRoot();
        if(walletFolder == null) {
            Toast.makeText(LoginActivity.this, "Error while creating wallet", Toast.LENGTH_SHORT).show();
            return;
        }

        File walletFile = new File(walletFolder, name);
        Timber.d("New Wallet %s", walletFile.getAbsolutePath());
        walletFile.delete(); // when recovering wallets, the cache seems corrupt

        popFragmentStack(GENERATE_STACK);
        Toast.makeText(LoginActivity.this, getString(R.string.generate_wallet_created), Toast.LENGTH_SHORT).show();
    }

    boolean walletExists(File walletFile, boolean any) {
        File dir = walletFile.getParentFile();
        String name = walletFile.getName();
        if (any) {
            return new File(dir, name).exists() || new File(dir, name + ".keys").exists();
        } else {
            return new File(dir, name).exists() && new File(dir, name + ".keys").exists();
        }
    }

    boolean copyWallet(File srcWallet, File dstWallet, boolean overwrite) {
        if (walletExists(dstWallet, true) && !overwrite) return false;
        boolean success = false;
        File srcDir = srcWallet.getParentFile();
        String srcName = srcWallet.getName();
        File dstDir = dstWallet.getParentFile();
        String dstName = dstWallet.getName();
        try {
            try {
                copyFile(new File(srcDir, srcName), new File(dstDir, dstName));
            } catch (IOException ex) {
                Timber.d("CACHE %s", true);
            }
            copyFile(new File(srcDir, srcName + ".keys"), new File(dstDir, dstName + ".keys"));
            success = true;
        } catch (IOException ex) {
            Timber.e("wallet copy failed: %s", ex.getMessage());
            // try to rollback
            deleteWallet(dstWallet);
        }
        return success;
    }

    // do our best to delete as much as possible of the wallet files
    boolean deleteWallet(File walletFile) {
        Timber.d("deleteWallet %s", walletFile.getAbsolutePath());
        File dir = walletFile.getParentFile();
        String name = walletFile.getName();
        boolean success = true;
        File cacheFile = new File(dir, name);
        if (cacheFile.exists()) {
            success = cacheFile.delete();
        }
        success = new File(dir, name + ".keys").delete() && success;
        File addressFile = new File(dir, name + ".address.txt");
        if (addressFile.exists()) {
            success = addressFile.delete() && success;
        }
        Timber.d("deleteWallet is %s", success);
        return success;
    }

    void copyFile(File src, File dst) throws IOException {
        try (FileChannel inChannel = new FileInputStream(src).getChannel(); FileChannel outChannel = new FileOutputStream(dst).getChannel()) {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        }
    }

    public void onChangeLocale() {
        final ArrayList<Locale> availableLocales = LocaleHelper.getAvailableLocales(LoginActivity.this);
        String[] localeDisplayName = new String[1 + availableLocales.size()];

        Collections.sort(availableLocales, (locale1, locale2) -> {
            String localeString1 = LocaleHelper.getDisplayName(locale1, true);
            String localeString2 = LocaleHelper.getDisplayName(locale2, true);
            return localeString1.compareTo(localeString2);
        });

        localeDisplayName[0] = getString(R.string.language_system_default);
        for (int i = 1; i < localeDisplayName.length; i++) {
            Locale locale = availableLocales.get(i - 1);
            localeDisplayName[i] = LocaleHelper.getDisplayName(locale, true);
        }

        int currentLocaleIndex = 0;
        String currentLocaleName = LocaleHelper.getLocale(LoginActivity.this);
        if (!currentLocaleName.isEmpty()) {
            Locale currentLocale = Locale.forLanguageTag(currentLocaleName);
            String currentLocalizedString = LocaleHelper.getDisplayName(currentLocale, true);
            currentLocaleIndex = Arrays.asList(localeDisplayName).indexOf(currentLocalizedString);
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(LoginActivity.this, R.style.MaterialAlertDialogCustom);
        builder.setTitle(getString(R.string.menu_language));
        builder.setSingleChoiceItems(localeDisplayName, currentLocaleIndex, new DialogInterface.OnClickListener() {
            @SuppressLint("UnsafeIntentLaunch")
            @Override
            public void onClick(DialogInterface dialog, int i) {
                dialog.dismiss();

                LocaleHelper.setLocale(LoginActivity.this,
                        (i == 0) ? "" : availableLocales.get(i - 1).toLanguageTag());
                startActivity(getIntent().addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        });

        builder.show();
    }

    @Override
    public void onBackPressed() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (f instanceof GenerateReviewFragment) {
            if (((GenerateReviewFragment) f).backOk()) {
                super.onBackPressed();
            }
        } else if (f instanceof NodeFragment) {
            if (!((NodeFragment) f).isRefreshing()) {
                super.onBackPressed();
            } else {
                Toast.makeText(LoginActivity.this, getString(R.string.node_refresh_wait), Toast.LENGTH_LONG).show();
            }
        } else if (f instanceof LoginFragment) {
            if (((LoginFragment) f).isFabOpen()) {
                ((LoginFragment) f).animateFAB();
            } else {
                super.onBackPressed();
            }
        } else {
            super.onBackPressed();
        }
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return switch (item.getItemId()) {
            case R.id.action_create_help_new -> {
                HelpFragment.display(getSupportFragmentManager(), R.string.help_create_new);
                yield true;
            }
            case R.id.action_create_help_keys -> {
                HelpFragment.display(getSupportFragmentManager(), R.string.help_create_keys);
                yield true;
            }
            case R.id.action_create_help_view -> {
                HelpFragment.display(getSupportFragmentManager(), R.string.help_create_view);
                yield true;
            }
            case R.id.action_create_help_seed -> {
                HelpFragment.display(getSupportFragmentManager(), R.string.help_create_seed);
                yield true;
            }
            case R.id.action_create_help_ledger -> {
                HelpFragment.display(getSupportFragmentManager(), R.string.help_create_ledger);
                yield true;
            }
            case R.id.action_details_help -> {
                HelpFragment.display(getSupportFragmentManager(), R.string.help_details);
                yield true;
            }
            case R.id.action_details_changepw -> {
                onWalletChangePassword();
                yield true;
            }
            case R.id.action_license_info -> {
                AboutFragment.display(getSupportFragmentManager());
                yield true;
            }
            case R.id.action_help_list -> {
                HelpFragment.display(getSupportFragmentManager(), R.string.help_list);
                yield true;
            }
            case R.id.action_help_node -> {
                HelpFragment.display(getSupportFragmentManager(), R.string.help_node_2);
                yield true;
            }
            case R.id.action_privacy_policy -> {
                PrivacyFragment.display(getSupportFragmentManager());
                yield true;
            }
            case R.id.action_language -> {
                onChangeLocale();
                yield true;
            }
            case R.id.action_nodes -> {
                onNodePrefs();
                yield true;
            }
            case R.id.action_mobile_miner -> {
                startActivity(new Intent(this, MobileMinerActivity.class));
                yield true;
            }
            case R.id.action_debug -> {
                toggleDebugInfo();
                yield true;
            }
            default -> super.onOptionsItemSelected(item);
        };
    }

    private void toggleDebugInfo() {
        boolean sendDebugInfo = Config.read(Config.CONFIG_SEND_DEBUG_INFO, "1").equals("1");
        if(sendDebugInfo) {
            Config.write(Config.CONFIG_SEND_DEBUG_INFO, "0");
            ACRA.getErrorReporter().setEnabled(false);
        } else {
            Config.write(Config.CONFIG_SEND_DEBUG_INFO, "1");
            ACRA.getErrorReporter().setEnabled(true);
        }

        try {
            LoginFragment loginFragment = (LoginFragment)getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (loginFragment != null) {
                loginFragment.updateDebugMenu();
            }
        } catch (ClassCastException ignored) {
        }
    }

    // an AsyncTask which tests the node before trying to open the wallet
    @SuppressLint("StaticFieldLeak")
    private class AsyncOpenWallet extends AsyncTask<Void, Void, Boolean> {
        private final String walletName;
        //private final String walletAddress;
        private final NodeInfo node;
        private final boolean stealthMode;

        AsyncOpenWallet(String walletName, NodeInfo node, boolean stealthMode) {
            this.walletName = walletName;
            //this.walletAddress = walletAddress;
            this.node = node;
            this.stealthMode = stealthMode;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            Timber.d("checking %s", node.getAddress());
            return node.testRpcService();
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);

            if (isDestroyed()) {
                return;
            }
            if (result) {
                Timber.d("selected wallet is .%s.", node.getName());
                // now it's getting real, onValidateFields if wallet exists
                promptAndStart(walletName, node, stealthMode);
            } else {
                if (node.getResponseCode() == 0) { // IOException
                    Toast.makeText(LoginActivity.this, getString(R.string.status_wallet_node_invalid), Toast.LENGTH_LONG).show();
                } else { // connected but broken
                    Toast.makeText(LoginActivity.this, getString(R.string.status_wallet_connect_ioex), Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    boolean checkDevice(String walletName, String password) {
        String keyPath = new File(Helper.getWalletRoot(LoginActivity.this),
                walletName + ".keys").getAbsolutePath();
        // check if we need connected hardware
        Wallet.Device device = WalletManager.getInstance().queryWalletDevice(keyPath, password);
        if (Objects.requireNonNull(device) == Wallet.Device.Device_Ledger) {
            if (!hasLedger()) {
                toast(R.string.open_wallet_ledger_missing);
            } else {
                return true;
            }
        } else {// device could be undefined meaning the password is wrong
            // this gets dealt with later
            return true;
        }
        return false;
    }

    void promptAndStart(String walletName, Node node, final boolean stealthMode) {
        File walletFile = Helper.getWalletFile(this, walletName);
        if (WalletManager.getInstance().walletExists(walletFile)) {
            Helper.promptPassword(LoginActivity.this, walletName, false,
                    (walletName1, password, fingerprintUsed) -> {
                        if (checkDevice(walletName1, password))
                            startWallet(walletName1, password, fingerprintUsed, stealthMode);
                    });
        } else { // this cannot really happen as we prefilter choices
            Toast.makeText(this, getString(R.string.bad_wallet), Toast.LENGTH_SHORT).show();
        }
    }

    // USB Stuff - (Ledger)

    private static final String ACTION_USB_PERMISSION = "io.scalaproject.vault.USB_PERMISSION";

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    void attachLedger() {
        final UsbManager usbManager = getUsbManager();
        UsbDevice device = Ledger.findDevice(usbManager);
        if (device != null) {
            if (usbManager.hasPermission(device)) {
                connectLedger(usbManager, device);
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    registerReceiver(usbPermissionReceiver, new IntentFilter(ACTION_USB_PERMISSION));
                }
                usbManager.requestPermission(device, PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE));
            }
        } else {
            Timber.d("no ledger device found");
        }
    }

    private final BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                unregisterReceiver(usbPermissionReceiver);
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            connectLedger(getUsbManager(), device);
                        }
                    } else {
                        assert device != null;
                        Timber.w("User denied permission for device %s", device.getProductName());
                    }
                }
            }
        }
    };

    private void connectLedger(UsbManager usbManager, final UsbDevice usbDevice) {
        if (Ledger.ENABLED)
            try {
                Ledger.connect(usbManager, usbDevice);
                if (!Ledger.check()) {
                    Ledger.disconnect();
                    runOnUiThread(() -> Toast.makeText(LoginActivity.this, getString(R.string.toast_ledger_start_app, usbDevice.getProductName()), Toast.LENGTH_SHORT).show());
                } else {
                    registerDetachReceiver();
                    onLedgerAction();
                    runOnUiThread(() -> Toast.makeText(LoginActivity.this, getString(R.string.toast_ledger_attached, usbDevice.getProductName()), Toast.LENGTH_SHORT).show());
                }
            } catch (IOException ex) {
                runOnUiThread(() -> Toast.makeText(LoginActivity.this, getString(R.string.open_wallet_ledger_missing), Toast.LENGTH_SHORT).show());
            }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        processUsbIntent(intent);
    }

    private boolean processUsbIntent(Intent intent) {
        String action = intent.getAction();
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            synchronized (this) {
                final UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    final UsbManager usbManager = getUsbManager();
                    if (usbManager.hasPermission(device)) {
                        Timber.d("Ledger attached by intent");
                        connectLedger(usbManager, device);
                    }
                }
            }
            return true;
        }
        return false;
    }

    private String uri = null;

    private void processUriIntent(Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            synchronized (this) {
                uri = intent.getDataString();
                Timber.d("URI Intent %s", uri);
                HelpFragment.display(getSupportFragmentManager(), R.string.help_uri);
            }
        }
    }

    BroadcastReceiver detachReceiver;

    private void unregisterDetachReceiver() {
        if (detachReceiver != null) {
            unregisterReceiver(detachReceiver);
            detachReceiver = null;
        }
    }

    private void registerDetachReceiver() {
        detachReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                if (Objects.equals(intent.getAction(), UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                    unregisterDetachReceiver();
                    final UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    Timber.i("Ledger detached!");
                    if (device != null)
                        runOnUiThread(() -> Toast.makeText(LoginActivity.this, getString(R.string.toast_ledger_detached, device.getProductName()), Toast.LENGTH_SHORT).show());
                    Ledger.disconnect();
                    onLedgerAction();
                }
            }
        };

        registerReceiver(detachReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED));
    }

    public void onLedgerAction() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (f instanceof GenerateFragment) {
            onBackPressed();
        } else if (f instanceof LoginFragment) {
            if (((LoginFragment) f).isFabOpen()) {
                ((LoginFragment) f).animateFAB();
            }
        }
    }

    // get UsbManager or die trying
    @NonNull
    private UsbManager getUsbManager() {
        final UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            throw new IllegalStateException("no USB_SERVICE");
        }
        return usbManager;
    }
}