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
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import io.scalaproject.vault.data.NodeInfo;
import io.scalaproject.vault.layout.NodeInfoAdapter;
import io.scalaproject.vault.layout.WalletInfoAdapter;
import io.scalaproject.vault.model.WalletManager;
import io.scalaproject.vault.util.Helper;
import io.scalaproject.vault.util.KeyStoreHelper;
import io.scalaproject.vault.util.LegacyStorageHelper;
import io.scalaproject.vault.util.Notice;
import io.scalaproject.vault.widget.Toolbar;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import timber.log.Timber;

public class LoginFragment extends Fragment implements WalletInfoAdapter.OnInteractionListener, View.OnClickListener {

    private WalletInfoAdapter adapter;
    private final List<WalletManager.WalletInfo> walletList = new ArrayList<>();
    private ImageButton ibNode;
    private TextView tvNodeName;
    private TextView tvNodeAddress;
    private View pbNode;
    private View llNode;
    private RecyclerView recyclerView;
    private LinearLayout llNoWallet;
    private Listener activityCallback;
    private Menu loginMenu;
    private static int connectionStatus = -1;

    public static void setConnectionStatus(int connectionState) {
        connectionStatus = connectionState;
    }

    // Container Activity must implement this interface
    public interface Listener {
        File getStorageRoot();
        void onWalletSelected(String wallet, boolean stealthMode);
        void onWalletDetails(String wallet);
        void onWalletReceive(String wallet);
        void onWalletRename(String name);
        void onWalletBackup(String name);
        void onWalletArchive(String walletName);
        void onAddWallet(String type);
        void onNodePrefs();
        void showNet();
        void setToolbarButton(int type);
        void setTitle(String title);
        void setNode(NodeInfo node);
        NodeInfo getNode();
        Set<NodeInfo> getAllNodes();
        boolean hasLedger();
        void showProgressDialog(final Integer msg_id);
        void hideProgressDialog();

        void loadNodesWithNetwork();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Listener) {
            this.activityCallback = (Listener) context;
        } else {
            throw new ClassCastException(context.toString() + " must implement Listener");
        }
    }

    @Override
    public void onPause() {
        Timber.d("onPause()");
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        Timber.d("onResume() %s", activityCallback.getAllNodes().size());
        activityCallback.setTitle(null);
        activityCallback.setToolbarButton(Toolbar.BUTTON_CREDITS);
        activityCallback.showNet();
        NodeInfo node = activityCallback.getNode();
        if (node == null)
            findBestNode();
        else
            showNode(node);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Timber.d("onCreateView");
        View view = inflater.inflate(R.layout.fragment_login, container, false);

        fabScreen = view.findViewById(R.id.fabScreen);
        fab = view.findViewById(R.id.fab);
        fabNew = view.findViewById(R.id.fabNew);
        fabView = view.findViewById(R.id.fabView);
        fabKey = view.findViewById(R.id.fabKey);
        fabSeed = view.findViewById(R.id.fabSeed);
        fabLedger = view.findViewById(R.id.fabLedger);

        fabNewL = view.findViewById(R.id.fabNewL);
        fabViewL = view.findViewById(R.id.fabViewL);
        fabKeyL = view.findViewById(R.id.fabKeyL);
        fabSeedL = view.findViewById(R.id.fabSeedL);
        fabLedgerL = view.findViewById(R.id.fabLedgerL);

        // Set up animations
        fab_pulse = AnimationUtils.loadAnimation(getContext(), R.anim.fab_pulse);
        fab_open_screen = AnimationUtils.loadAnimation(getContext(), R.anim.fab_open_screen);
        fab_close_screen = AnimationUtils.loadAnimation(getContext(), R.anim.fab_close_screen);
        fab_open = AnimationUtils.loadAnimation(getContext(), R.anim.fab_open);
        fab_close = AnimationUtils.loadAnimation(getContext(), R.anim.fab_close);
        rotate_forward = AnimationUtils.loadAnimation(getContext(), R.anim.rotate_forward);
        rotate_backward = AnimationUtils.loadAnimation(getContext(), R.anim.rotate_backward);

        // Set up click listeners
        fab.setOnClickListener(this);
        fabNew.setOnClickListener(this);
        fabView.setOnClickListener(this);
        fabKey.setOnClickListener(this);
        fabSeed.setOnClickListener(this);
        fabLedger.setOnClickListener(this);
        fabScreen.setOnClickListener(this);

        // Set up wallets
        llNoWallet = view.findViewById(R.id.llNoWallet);
        recyclerView = view.findViewById(R.id.listWallets);
        registerForContextMenu(recyclerView);
        this.adapter = new WalletInfoAdapter(getActivity(), this);
        recyclerView.setAdapter(adapter);

        // Check network and notice user
        // Allow to do basic stuffs without network and sync when is online
        if (connectionStatus == 0) {
            ViewGroup lafNotice = view.findViewById(R.id.llNotice);
            Notice.showAll(lafNotice, "notice_network");
            lafNotice.setOnClickListener(v -> activityCallback.showNet());
        } else {
            // Show mobile miner aviability info notice
            ViewGroup llNotice = view.findViewById(R.id.llNotice);
            llNotice.setOnClickListener(v -> startActivity(new Intent(getActivity(), MobileMinerActivity.class)));
            Notice.showAll(llNotice, "notice_miner", false);
        }

        pbNode = view.findViewById(R.id.pbNode);
        llNode = view.findViewById(R.id.llNode);
        llNode.setOnClickListener(v -> {
            if (activityCallback.getAllNodes().isEmpty())
                startNodePrefs();
            else
                findBestNode();
        });

        ibNode = view.findViewById(R.id.ibNode);
        tvNodeName = view.findViewById(R.id.tvNodeName);
        tvNodeAddress = view.findViewById(R.id.tvNodeAddress);

        view.findViewById(R.id.ibOption).setOnClickListener(v -> startNodePrefs());
        Helper.hideKeyboard(getActivity());
        loadList();
        return view;
    }

    // Callbacks from WalletInfoAdapter

    // Wallet touched
    @Override
    public void onInteraction(final View view, final WalletManager.WalletInfo infoItem) {
        openWallet(infoItem.name, false);
    }

    private void openWallet(String name, boolean stealthMode) {
        activityCallback.onWalletSelected(name, stealthMode);
    }

    // Create wallets action menu list
    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onContextInteraction(MenuItem item, WalletManager.WalletInfo listItem) {
        switch (item.getItemId()) {
            case R.id.action_stealthmode:
                openWallet(listItem.name, true);
                break;
            case R.id.action_info:
                showInfo(listItem.name);
                break;
            case R.id.action_receive:
                showReceive(listItem.name);
                break;
            case R.id.action_rename:
                activityCallback.onWalletRename(listItem.name);
                break;
            case R.id.action_backup:
                activityCallback.onWalletBackup(listItem.name);
                break;
            case R.id.action_archive:
                activityCallback.onWalletArchive(listItem.name);
                break;
            default:
                return super.onContextItemSelected(item);
        }
        return true;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void loadList() {
        Timber.d("loadList");

        LegacyStorageHelper.migrateWallets(getContext());

        WalletManager mgr = WalletManager.getInstance();
        List<WalletManager.WalletInfo> walletInfos = mgr.findWallets(activityCallback.getStorageRoot());

        if(walletInfos.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            llNoWallet.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            llNoWallet.setVisibility(View.GONE);
        }

        walletList.clear();
        walletList.addAll(walletInfos);
        adapter.setInfos(walletInfos);
        adapter.notifyDataSetChanged();

        // deal with Gunther & FAB animation
        if (walletInfos.isEmpty()) {
            fab.startAnimation(fab_pulse);
        } else {
            fab.clearAnimation();
        }

        // remove information of non-existent wallet
        Set<String> removedWallets = requireActivity()
                .getSharedPreferences(KeyStoreHelper.SecurityConstants.WALLET_PASS_PREFS_NAME, Context.MODE_PRIVATE)
                .getAll().keySet();

        if(!removedWallets.isEmpty()) {
            for (WalletManager.WalletInfo s : walletList) {
                removedWallets.remove(s.name);
            }
            for (String name : removedWallets) {
                KeyStoreHelper.removeWalletUserPass(getActivity(), name);
            }
        }

        // This handles post render layout loading network nodes. better ui experience.
        if (activityCallback.getAllNodes().isEmpty() && connectionStatus != -1) {
            //requireActivity().runOnUiThread(() -> Toast.makeText(this.getContext(), "something", Toast.LENGTH_SHORT).show());
            Handler handler = new Handler();
            handler.postDelayed(() -> {
                activityCallback.loadNodesWithNetwork();
                findBestNode();
            }, 2000);
        }
    }

    private void showInfo(@NonNull String name) {
        activityCallback.onWalletDetails(name);
    }

    private void showReceive(@NonNull String name) {
        activityCallback.onWalletReceive(name);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.list_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);

        loginMenu = menu;
        updateDebugMenu();
    }

    // Update menu debug information
    // Todo remove this in production version and debug features
    public void updateDebugMenu() {
        boolean sendDebugInfo = Config.read(Config.CONFIG_SEND_DEBUG_INFO, "1").equals("1");

        if(sendDebugInfo) {
            loginMenu.findItem(R.id.action_debug).setTitle(R.string.disable_debug_info);
        } else {
            loginMenu.findItem(R.id.action_debug).setTitle(R.string.enable_debug_info);
        }
    }

    private boolean isFabOpen = false;
    private FloatingActionButton fab, fabNew, fabView, fabKey, fabSeed, fabLedger;
    private FrameLayout fabScreen;
    private RelativeLayout fabNewL, fabViewL, fabKeyL, fabSeedL, fabLedgerL;
    private Animation fab_open, fab_close, rotate_forward, rotate_backward, fab_open_screen, fab_close_screen;
    private Animation fab_pulse;

    public boolean isFabOpen() {
        return isFabOpen;
    }

    public void animateFAB() {
        if (isFabOpen) { // close the fab
            fabScreen.setClickable(false);
            fabScreen.startAnimation(fab_close_screen);
            fab.startAnimation(rotate_backward);
            if (fabLedgerL.getVisibility() == View.VISIBLE) {
                fabLedgerL.startAnimation(fab_close);
                fabLedger.setClickable(false);
            } else {
                fabNewL.startAnimation(fab_close);
                fabNew.setClickable(false);
                fabViewL.startAnimation(fab_close);
                fabView.setClickable(false);
                fabKeyL.startAnimation(fab_close);
                fabKey.setClickable(false);
                fabSeedL.startAnimation(fab_close);
                fabSeed.setClickable(false);
            }
            isFabOpen = false;
        } else { // open the fab
            fabScreen.setClickable(true);
            fabScreen.startAnimation(fab_open_screen);
            fab.startAnimation(rotate_forward);
            if (activityCallback.hasLedger()) {
                fabLedgerL.setVisibility(View.VISIBLE);
                fabNewL.setVisibility(View.GONE);
                fabViewL.setVisibility(View.GONE);
                fabKeyL.setVisibility(View.GONE);
                fabSeedL.setVisibility(View.GONE);

                fabLedgerL.startAnimation(fab_open);
                fabLedger.setClickable(true);
            } else {
                fabLedgerL.setVisibility(View.GONE);
                fabNewL.setVisibility(View.VISIBLE);
                fabViewL.setVisibility(View.VISIBLE);
                fabKeyL.setVisibility(View.VISIBLE);
                fabSeedL.setVisibility(View.VISIBLE);

                fabNewL.startAnimation(fab_open);
                fabNew.setClickable(true);
                fabViewL.startAnimation(fab_open);
                fabView.setClickable(true);
                fabKeyL.startAnimation(fab_open);
                fabKey.setClickable(true);
                fabSeedL.startAnimation(fab_open);
                fabSeed.setClickable(true);
            }
            isFabOpen = true;
        }
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View v) {
        int id = v.getId();
        Timber.d("onClick %d/%d", id, R.id.fabLedger);
        switch (id) {
            case R.id.fab:
                animateFAB();
                break;
            case R.id.fabNew:
                fabScreen.setVisibility(View.INVISIBLE);
                isFabOpen = false;
                activityCallback.onAddWallet(GenerateFragment.TYPE_NEW);
                break;
            case R.id.fabView:
                animateFAB();
                activityCallback.onAddWallet(GenerateFragment.TYPE_VIEWONLY);
                break;
            case R.id.fabKey:
                animateFAB();
                activityCallback.onAddWallet(GenerateFragment.TYPE_KEY);
                break;
            case R.id.fabSeed:
                animateFAB();
                activityCallback.onAddWallet(GenerateFragment.TYPE_SEED);
                break;
            case R.id.fabLedger:
                Timber.d("FAB_LEDGER");
                animateFAB();
                activityCallback.onAddWallet(GenerateFragment.TYPE_LEDGER);
                break;
            case R.id.fabScreen:
                animateFAB();
                break;
        }
    }

    public void findBestNode() {
        new AsyncFindBestNode().execute();
    }

    @SuppressLint("StaticFieldLeak")
    private class AsyncFindBestNode extends AsyncTask<Void, Void, NodeInfo> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            pbNode.setVisibility(View.VISIBLE);
            llNode.setVisibility(View.INVISIBLE);
            ibNode.setVisibility(View.INVISIBLE);

            activityCallback.showProgressDialog(R.string.connection_remote_daemon);

            activityCallback.setNode(null);
        }

        @Override
        protected NodeInfo doInBackground(Void... params) {
            List<NodeInfo> nodesToTest = new ArrayList<>(activityCallback.getAllNodes());

            Timber.d("testing best node from %d", nodesToTest.size());

            if (nodesToTest.isEmpty()) return null;

            for (NodeInfo node : nodesToTest) {
                node.testRpcService();
            }

            String userSelectedNode = Config.read(Config.CONFIG_KEY_USER_SELECTED_NODE);
            if(userSelectedNode.isEmpty()) {
                Collections.sort(nodesToTest, NodeInfo.BestNodeComparator);
                NodeInfo bestNode = nodesToTest.get(0);

                if (bestNode.isValid()) {
                    activityCallback.setNode(bestNode);
                    return bestNode;
                } else {
                    activityCallback.setNode(null);
                    return null;
                }
            }
            else {
                NodeInfo nodeInfo = new NodeInfo(userSelectedNode);
                nodeInfo.testRpcService();

                if(nodeInfo.isValid()) {
                    activityCallback.setNode(nodeInfo);
                    return nodeInfo;
                }
                else {
                    Config.write(Config.CONFIG_KEY_USER_SELECTED_NODE, "");

                    Collections.sort(nodesToTest, NodeInfo.BestNodeComparator);
                    NodeInfo bestNode = nodesToTest.get(0);

                    if (bestNode.isValid()) {
                        activityCallback.setNode(bestNode);
                        return bestNode;
                    } else {
                        activityCallback.setNode(null);
                        return null;
                    }
                }
            }
        }

        @SuppressLint("UseCompatLoadingForDrawables")
        @Override
        protected void onPostExecute(NodeInfo result) {
            activityCallback.hideProgressDialog();

            if (!isAdded()) return;

            pbNode.setVisibility(View.INVISIBLE);
            llNode.setVisibility(View.VISIBLE);
            ibNode.setVisibility(View.VISIBLE);

            if (result != null) {
                Timber.d("found a good node %s", result.toString());
                showNode(result);
            } else {
                if (!activityCallback.getAllNodes().isEmpty()) {
                    tvNodeName.setText(getResources().getText(R.string.node_refresh_hint));
                    ibNode.setImageDrawable(getResources().getDrawable(R.drawable.ic_refresh_black_24dp));
                    tvNodeAddress.setText(null);
                    tvNodeAddress.setVisibility(View.GONE);
                } else {
                    tvNodeName.setText(getResources().getText(R.string.node_create_hint));
                    ibNode.setVisibility(View.INVISIBLE);
                    tvNodeAddress.setText(null);
                    tvNodeAddress.setVisibility(View.GONE);
                }
            }
        }

        @Override
        protected void onCancelled(NodeInfo result) { //TODO: cancel this on exit from fragment
            Timber.d("cancelled with %s", result);
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private void showNode(NodeInfo nodeInfo) {
        tvNodeName.setText(nodeInfo.getName());
        ibNode.setVisibility(View.VISIBLE);
        ibNode.setImageDrawable(getResources().getDrawable(NodeInfoAdapter.getPingIcon(nodeInfo)));
        tvNodeAddress.setText(nodeInfo.getAddress());
        tvNodeAddress.setVisibility(View.VISIBLE);
    }

    private void startNodePrefs() {
        activityCallback.setNode(null);
        activityCallback.onNodePrefs();
    }
}
