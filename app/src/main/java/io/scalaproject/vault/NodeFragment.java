/*
 * Copyright (c) 2018 m2049r
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
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import io.scalaproject.levin.scanner.Dispatcher;
import io.scalaproject.vault.data.Node;
import io.scalaproject.vault.data.NodeInfo;
import io.scalaproject.vault.layout.NodeInfoAdapter;
import io.scalaproject.vault.model.NetworkType;
import io.scalaproject.vault.model.WalletManager;
import io.scalaproject.vault.util.Helper;
import io.scalaproject.vault.util.Notice;
import io.scalaproject.vault.widget.Toolbar;

import java.io.File;
import java.net.UnknownHostException;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import timber.log.Timber;

public class NodeFragment extends Fragment
        implements NodeInfoAdapter.OnMenuNodeListener, NodeInfoAdapter.OnSelectNodeListener, View.OnClickListener {

    static private final NumberFormat FORMATTER = NumberFormat.getInstance();

    private SwipeRefreshLayout pullToRefresh;
    private View fabAddNode;
    private RecyclerView rvNodes;

    private Set<NodeInfo> allNodes = new HashSet<>();
    private final Set<NodeInfo> userdefinedNodes = new HashSet<>();

    private NodeInfoAdapter nodesAdapter;

    private Listener activityCallback;

    private View selectedNodeView = null;
    private NodeInfo selectedNode = null;

    public NodeInfo getNode() { return selectedNode; }

    public interface Listener {
        File getStorageRoot();

        void setToolbarButton(int type);

        void setSubtitle(String title);

        Set<NodeInfo> getAllNodes();

        NodeInfo getNode();

        void setNode(NodeInfo node);

        void addUserDefinedNodes(Set<NodeInfo> userDefinedNodes);

        void deleteUserDefinedNode(NodeInfo nodeInfo);
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
        Timber.d("onPause() %d", allNodes.size());

        if (asyncFindNodes != null)
            asyncFindNodes.cancel(true);

        if (activityCallback != null) {
            activityCallback.addUserDefinedNodes(userdefinedNodes);
            activityCallback.setNode(selectedNode);
        }

        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        Timber.d("onResume()");
        activityCallback.setSubtitle(getString(R.string.label_nodes));
        updateRefreshElements();
    }

    boolean isRefreshing() {
        return asyncFindNodes != null;
    }

    void updateRefreshElements() {
        if (isRefreshing()) {
            activityCallback.setToolbarButton(Toolbar.BUTTON_NONE);
            fabAddNode.setVisibility(View.GONE);
        } else {
            activityCallback.setToolbarButton(Toolbar.BUTTON_BACK);
            fabAddNode.setVisibility(View.VISIBLE);
        }
    }

    private void updateSelectedNodeLayout() {
        // If recycler view has not been rendered yet
        if(Objects.requireNonNull(rvNodes.getLayoutManager()).getItemCount() <= 0)
            return;

        NodeInfo selectedNode = activityCallback.getNode();
        if(selectedNode != null) {
            List<NodeInfo> allNodes = nodesAdapter.getNodes();
            for (int i = 0; i < allNodes.size(); i++ ) {
                NodeInfo nodeInfo = allNodes.get(i);
                Boolean bSelected = selectedNode.equals(nodeInfo);
                View itemView = rvNodes.getChildAt(i);
                setItemNodeLayout(itemView, bSelected);

                if(bSelected) {
                    selectedNodeView = itemView;
                    selectedNode = nodeInfo;
                }
            }
        }
    }

    private void setItemNodeLayout(View itemView, Boolean selected) {
        if(itemView != null) {
            RelativeLayout rlItemNode = (RelativeLayout) itemView;
            int bottom = rlItemNode.getPaddingBottom();
            int top = rlItemNode.getPaddingTop();
            int right = rlItemNode.getPaddingRight();
            int left = rlItemNode.getPaddingLeft();
            rlItemNode.setBackgroundResource(selected ? R.drawable.corner_radius_lighter_border_blue : R.drawable.corner_radius_lighter);
            rlItemNode.setPadding(left, top, right, bottom);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Timber.d("onCreateView");
        View view = inflater.inflate(R.layout.fragment_node, container, false);

        fabAddNode = view.findViewById(R.id.fabAddNode);
        fabAddNode.setOnClickListener(this);

        rvNodes = view.findViewById(R.id.rvNodes);
        nodesAdapter = new NodeInfoAdapter(getActivity(), this, this);
        rvNodes.setAdapter(nodesAdapter);

        rvNodes.post(this::updateSelectedNodeLayout);

        pullToRefresh = view.findViewById(R.id.pullToRefresh);
        pullToRefresh.setOnRefreshListener(() -> {
            if (WalletManager.getInstance().getNetworkType() == NetworkType.NetworkType_Mainnet) {
                refresh();
            } else {
                Toast.makeText(getActivity(), getString(R.string.node_wrong_net), Toast.LENGTH_LONG).show();
                pullToRefresh.setRefreshing(false);
            }
        });

        Helper.hideKeyboard(getActivity());

        allNodes = new HashSet<>(activityCallback.getAllNodes());
        nodesAdapter.setNodes(allNodes);

        ViewGroup llNotice = view.findViewById(R.id.llNotice);
        Notice.showAll(llNotice, "notice_nodes");

        return view;
    }

    private AsyncFindNodes asyncFindNodes = null;

    private void refresh() {
        if (asyncFindNodes != null) return; // ignore refresh request as one is ongoing

        asyncFindNodes = new AsyncFindNodes();
        updateRefreshElements();
        asyncFindNodes.execute();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.node_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    // Callbacks from NodeInfoAdapter
    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onContextInteraction(MenuItem item, NodeInfo nodeInfo) {
        switch (item.getItemId()) {
            case R.id.action_edit_node:
                EditDialog diag = createEditDialog(nodeInfo);
                if (diag != null) {
                    diag.show();
                }

                break;
            case R.id.action_delete_node:
                onDeleteNode(nodeInfo);
                break;
            default:
                return super.onContextItemSelected(item);
        }

        return true;
    }

    public void onDeleteNode(final NodeInfo nodeInfo) {
        DialogInterface.OnClickListener dialogClickListener = (dialog, action) -> {
            switch (action) {
                case DialogInterface.BUTTON_POSITIVE:
                    if(nodeInfo.isUserDefined()) {
                        nodesAdapter.deleteNode(nodeInfo);
                        allNodes.remove(nodeInfo);
                        userdefinedNodes.remove(nodeInfo);

                        activityCallback.deleteUserDefinedNode(nodeInfo);

                        refresh();
                    }

                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    // do nothing
                    break;
            }
        };

        if(!nodeInfo.isUserDefined()) {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialogCustom);
            builder.setMessage("Default nodes cannot be deleted.")
                    .setTitle(nodeInfo.getName())
                    .setPositiveButton(getString(R.string.label_ok), dialogClickListener)
                    .show();
        } else {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialogCustom);
            builder.setMessage("Do you really want to delete this node?")
                    .setTitle(nodeInfo.getName())
                    .setPositiveButton(getString(R.string.details_alert_yes), dialogClickListener)
                    .setNegativeButton(getString(R.string.details_alert_no), dialogClickListener)
                    .show();
        }
    }

    // Callbacks from NodeInfoAdapter
    @Override
    public void onSelectNode(final View view, final NodeInfo nodeInfo) {
        Timber.d("onSelecteNode");

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity(), R.style.MaterialAlertDialogCustom);
        builder.setMessage(getString(R.string.change_remote_node_conf, nodeInfo.getName()))
                .setCancelable(true)
                .setPositiveButton(getString(R.string.details_alert_yes), (dialogInterface, i) -> {
                    if(nodeInfo.isValid()) {
                        setItemNodeLayout(selectedNodeView, false);
                        selectedNodeView = view;
                        selectedNode = nodeInfo;
                        setItemNodeLayout(selectedNodeView, true);

                        Config.write(Config.CONFIG_KEY_USER_SELECTED_NODE, nodeInfo.toNodeString());
                    }
                    else {
                        MaterialAlertDialogBuilder builder1 = new MaterialAlertDialogBuilder(requireActivity(), R.style.MaterialAlertDialogCustom);
                        builder1.setMessage(getString(R.string.status_wallet_node_invalid))
                                .setCancelable(true)
                                .setNegativeButton(getString(R.string.label_ok), null)
                        .show();
                    }
                })
                .setNegativeButton(getString(R.string.details_alert_no), null)
                .show();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.fabAddNode) {
            EditDialog diag = createEditDialog(null);
            if (diag != null) {
                diag.show();
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class AsyncFindNodes extends AsyncTask<Void, NodeInfo, Boolean> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            nodesAdapter.setNodes(null);
            nodesAdapter.allowClick(false);

            setItemNodeLayout(selectedNodeView, false);
            selectedNodeView = null;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            Timber.d("scanning");
            Set<NodeInfo> seedList = new HashSet<>(allNodes);
            allNodes.clear();

            Timber.d("seed %d", seedList.size());

            Dispatcher d = new Dispatcher(this::publishProgress);
            d.seedPeers(seedList);
            int NODES_TO_FIND = 15;
            d.awaitTermination(NODES_TO_FIND);

            // we didn't find enough because we didn't ask around enough? ask more!
            if ((d.getRpcNodes().size() < NODES_TO_FIND) &&
                    (d.getPeerCount() < NODES_TO_FIND + seedList.size())) {
                // try again
                publishProgress((NodeInfo[]) null);
                d = new Dispatcher(this::publishProgress);
                // also seed with scala seed nodes (see p2p/net_node.inl:410 in scala src)
                //seedList.add(new NodeInfo(new InetSocketAddress("62.171.149.67", 11811)));
                //seedList.add(new NodeInfo(new InetSocketAddress("164.68.117.160", 11811)));
                d.seedPeers(seedList);
                d.awaitTermination(NODES_TO_FIND);
            }

            // final (filtered) result
            allNodes.addAll(d.getRpcNodes());

            return true;
        }

        @Override
        protected void onProgressUpdate(NodeInfo... values) {
            Timber.d("onProgressUpdate");
            if (!isCancelled())
                if (values != null)
                    nodesAdapter.addNode(values[0]);
                else
                    nodesAdapter.setNodes(null);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            Timber.d("done scanning");
            complete();
        }

        @Override
        protected void onCancelled(Boolean result) {
            Timber.d("cancelled scanning");
            complete();
        }

        private void complete() {
            asyncFindNodes = null;
            if (!isAdded()) return;

            pullToRefresh.setRefreshing(false);

            nodesAdapter.setNodes(allNodes);
            nodesAdapter.allowClick(true);

            selectedNodeView = rvNodes.getChildAt(0);
            if(!nodesAdapter.getNodes().isEmpty())
                selectedNode = nodesAdapter.getNodes().get(0);
            setItemNodeLayout(selectedNodeView, true);

            Config.write(Config.CONFIG_KEY_USER_SELECTED_NODE, "");

            updateRefreshElements();
        }
    }

    @Override
    public void onDetach() {
        Timber.d("detached");
        super.onDetach();
    }

    private EditDialog editDialog = null; // for preventing opening of multiple dialogs

    private EditDialog createEditDialog(final NodeInfo nodeInfo) {
        if (editDialog != null) return null; // we are already open
        editDialog = new EditDialog(nodeInfo);
        return editDialog;
    }

    class EditDialog {
        final NodeInfo nodeInfo;
        final NodeInfo nodeBackup;

        private boolean applyChanges() {
            nodeInfo.clear();
            showTestResult();

            final String portString = Objects.requireNonNull(etNodePort.getEditText()).getText().toString().trim();
            int port;
            if (portString.isEmpty()) {
                port = Node.getDefaultRpcPort();
            } else {
                try {
                    port = Integer.parseInt(portString);
                } catch (NumberFormatException ex) {
                    etNodePort.setError(getString(R.string.node_port_numeric));
                    return false;
                }
            }
            etNodePort.setError(null);
            if ((port <= 0) || (port > 65535)) {
                etNodePort.setError(getString(R.string.node_port_range));
                return false;
            }

            final String host = Objects.requireNonNull(etNodeHost.getEditText()).getText().toString().trim();
            if (host.isEmpty()) {
                etNodeHost.setError(getString(R.string.node_host_empty));
                return false;
            }
            final boolean setHostSuccess = Helper.runWithNetwork(() -> {
                try {
                    nodeInfo.setHost(host);
                    return true;
                } catch (UnknownHostException ex) {
                    etNodeHost.setError(getString(R.string.node_host_unresolved));
                    return false;
                }
            });
            if (!setHostSuccess) {
                etNodeHost.setError(getString(R.string.node_host_unresolved));
                return false;
            }
            etNodeHost.setError(null);
            nodeInfo.setRpcPort(port);
            // setName() may trigger reverse DNS
            Helper.runWithNetwork(() -> {
                nodeInfo.setName(Objects.requireNonNull(etNodeName.getEditText()).getText().toString().trim());
                return true;
            });

            nodeInfo.setUsername(Objects.requireNonNull(etNodeUser.getEditText()).getText().toString().trim());
            nodeInfo.setPassword(Objects.requireNonNull(etNodePass.getEditText()).getText().toString()); // no trim for pw

            return true;
        }

        private boolean shutdown = false;

        private void apply() {
            if (applyChanges()) {
                closeDialog();

                if (nodeBackup == null) { // this is a (FAB) new node
                    nodeInfo.setUserDefined(true);
                    allNodes.add(nodeInfo);
                    userdefinedNodes.add(nodeInfo); // just used when saving
                }

                shutdown = true;
                new AsyncTestNode().execute();
            }
        }

        private void closeDialog() {
            if (editDialog == null) throw new IllegalStateException();
            Helper.hideKeyboardAlways(requireActivity());
            editDialog.dismiss();
            editDialog = null;
            NodeFragment.this.editDialog = null;
        }

        private void undoChanges() {
            if (nodeBackup != null)
                nodeInfo.overwriteWith(nodeBackup);
        }

        private void show() {
            editDialog.show();
        }

        private void test() {
            if (applyChanges())
                new AsyncTestNode().execute();
        }

        androidx.appcompat.app.AlertDialog editDialog = null;

        TextInputLayout etNodeName;
        TextInputLayout etNodeHost;
        TextInputLayout etNodePort;
        TextInputLayout etNodeUser;
        TextInputLayout etNodePass;
        TextView tvResult;

        void showTestResult() {
            if (nodeInfo.isSuccessful()) {
                tvResult.setText(getString(R.string.node_result,
                        FORMATTER.format(nodeInfo.getHeight()), nodeInfo.getMajorVersion(),
                        nodeInfo.getResponseTime(), nodeInfo.getHostAddress()));
            } else {
                tvResult.setText(NodeInfoAdapter.getResponseErrorText(getActivity(), nodeInfo.getResponseCode()));
            }
        }

        @SuppressLint("SetTextI18n")
        EditDialog(final NodeInfo nodeInfo) {
            MaterialAlertDialogBuilder alertDialogBuilder = new MaterialAlertDialogBuilder(requireActivity(), R.style.MaterialAlertDialogCustom);
            LayoutInflater li = LayoutInflater.from(alertDialogBuilder.getContext());
            View promptsView = li.inflate(R.layout.prompt_editnode, null);
            alertDialogBuilder.setView(promptsView);

            etNodeName = promptsView.findViewById(R.id.etNodeName);
            etNodeHost = promptsView.findViewById(R.id.etNodeHost);
            etNodePort = promptsView.findViewById(R.id.etNodePort);
            etNodeUser = promptsView.findViewById(R.id.etNodeUser);
            etNodePass = promptsView.findViewById(R.id.etNodePass);
            tvResult = promptsView.findViewById(R.id.tvResult);

            if (nodeInfo != null) {
                boolean isUserDefined = nodeInfo.isUserDefined();
                etNodeName.setEnabled(isUserDefined);
                etNodeHost.setEnabled(isUserDefined);
                etNodePort.setEnabled(isUserDefined);
                etNodeUser.setEnabled(isUserDefined);
                etNodePass.setEnabled(isUserDefined);

                this.nodeInfo = nodeInfo;
                nodeBackup = new NodeInfo(nodeInfo);
                Objects.requireNonNull(etNodeName.getEditText()).setText(nodeInfo.getName());
                Objects.requireNonNull(etNodeHost.getEditText()).setText(nodeInfo.getHost());
                Objects.requireNonNull(etNodePort.getEditText()).setText(Integer.toString(nodeInfo.getRpcPort()));
                Objects.requireNonNull(etNodeUser.getEditText()).setText(nodeInfo.getUsername());
                Objects.requireNonNull(etNodePass.getEditText()).setText(nodeInfo.getPassword());
                showTestResult();
            } else {
                this.nodeInfo = new NodeInfo();
                nodeBackup = null;
            }

            // set dialog message
            alertDialogBuilder
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.label_ok), null)
                    .setNeutralButton(getString(R.string.label_test), null)
                    .setNegativeButton(getString(R.string.label_cancel),
                            (dialog, id) -> {
                                undoChanges();
                                closeDialog();
                                nodesAdapter.dataSetChanged(); // to refresh test results
                            });

            editDialog = alertDialogBuilder.create();
            // these need to be here, since we don't always close the dialog
            editDialog.setOnShowListener(dialog -> {
                Button testButton = editDialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                testButton.setOnClickListener(view -> test());

                Button button = editDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                button.setOnClickListener(view -> apply());
            });

            if (Helper.preventScreenshot()) {
                Objects.requireNonNull(editDialog.getWindow()).setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            }

            Objects.requireNonNull(etNodePass.getEditText()).setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    editDialog.getButton(DialogInterface.BUTTON_NEUTRAL).requestFocus();
                    test();
                    return true;
                }
                return false;
            });
        }

        @SuppressLint("StaticFieldLeak")
        private class AsyncTestNode extends AsyncTask<Void, Void, Boolean> {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                nodeInfo.clear();
                tvResult.setText(getString(R.string.node_testing, nodeInfo.getHostAddress()));
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                nodeInfo.testRpcService();
                return true;
            }

            @Override
            protected void onPostExecute(Boolean result) {
                if (editDialog != null) {
                    showTestResult();
                }
                if (shutdown) {
                    if (nodeBackup == null) {
                        nodesAdapter.addNode(nodeInfo);
                        activityCallback.addUserDefinedNodes(userdefinedNodes);
                    } else {
                        nodesAdapter.dataSetChanged();
                    }
                }
            }
        }
    }
}