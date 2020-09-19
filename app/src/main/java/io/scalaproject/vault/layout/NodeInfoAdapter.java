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

package io.scalaproject.vault.layout;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import io.scalaproject.vault.R;
import io.scalaproject.vault.data.NodeInfo;

import java.net.HttpURLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class NodeInfoAdapter extends RecyclerView.Adapter<NodeInfoAdapter.ViewHolder> {
    private final SimpleDateFormat TS_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public interface OnNodeSettingsListener {
        void onSettingsNode(View view, NodeInfo item);
    }

    public interface OnSelectNodeListener {
        void onSelectNode(View view, NodeInfo item);
    }

    private final List<NodeInfo> nodeItems = new ArrayList<>();
    private final OnNodeSettingsListener onNodeSettingsListener;
    private final OnSelectNodeListener onSelectNodeListener;

    private Context context;

    public NodeInfoAdapter(Context context, OnNodeSettingsListener onNodeSettingsListener, OnSelectNodeListener onSelectNodeListener) {
        this.context = context;
        this.onNodeSettingsListener = onNodeSettingsListener;
        this.onSelectNodeListener = onSelectNodeListener;

        Calendar cal = Calendar.getInstance();
        TimeZone tz = cal.getTimeZone(); //get the local time zone.
        TS_FORMATTER.setTimeZone(tz);
    }

    @Override
    public @NonNull
    ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_node, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final @NonNull ViewHolder holder, int position) {
        holder.bind(position);
    }

    @Override
    public int getItemCount() {
        return nodeItems.size();
    }

    public void addNode(NodeInfo node) {
        if (!nodeItems.contains(node))
            nodeItems.add(node);

        dataSetChanged(); // in case the nodeinfo has changed
    }

    public void dataSetChanged() {
        Collections.sort(nodeItems, NodeInfo.BestNodeComparator);
        notifyDataSetChanged();
    }

    public List<NodeInfo> getNodes() {
        return nodeItems;
    }

    public void setNodes(Collection<NodeInfo> data) {
        nodeItems.clear();
        if (data != null) {
            for (NodeInfo node : data) {
                if (!nodeItems.contains(node))
                    nodeItems.add(node);
            }
        }

        dataSetChanged();
    }

    private boolean itemsClickable = true;

    public void allowClick(boolean clickable) {
        itemsClickable = clickable;
        notifyDataSetChanged();
    }

    class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        final TextView tvName;
        final TextView tvIp;
        final ImageView ivPing;
        final ImageView ivSettings;
        NodeInfo nodeItem;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvIp = itemView.findViewById(R.id.tvAddress);
            ivPing = itemView.findViewById(R.id.ivPing);
            ivSettings = itemView.findViewById(R.id.ivSettings);
        }

        void bind(final int position) {
            nodeItem = nodeItems.get(position);

            tvName.setText(nodeItem.getName());
            final String ts = TS_FORMATTER.format(new Date(nodeItem.getTimestamp() * 1000));
            ivPing.setImageResource(getPingIcon(nodeItem));

            if (nodeItem.isValid()) {
                tvIp.setText(context.getString(R.string.node_height, ts));
            } else {
                tvIp.setText(getResponseErrorText(context, nodeItem.getResponseCode()));
            }

            itemView.setOnClickListener(this);
            itemView.setClickable(itemsClickable);

            ivSettings.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    onNodeSettingsListener.onSettingsNode(v, nodeItems.get(position));
                }
            });
        }

        @Override
        public void onClick(View view) {
            if (onSelectNodeListener != null) {
                int position = getAdapterPosition(); // gets item position
                if (position != RecyclerView.NO_POSITION) { // Check if an item was deleted, but the user clicked it before the UI removed it
                    onSelectNodeListener.onSelectNode(view, nodeItems.get(position));
                }
            }
        }
    }

    static public int getPingIcon(NodeInfo nodeInfo) {
        if (nodeInfo.isUnauthorized()) {
            return R.drawable.ic_wifi_lock_24dp;
        }

        if (nodeInfo.isValid()) {
            final double ping = nodeInfo.getResponseTime();
            if (ping < NodeInfo.PING_GOOD) {
                return R.drawable.ic_signal_wifi_4_bar_24dp;
            } else if (ping < NodeInfo.PING_MEDIUM) {
                return R.drawable.ic_signal_wifi_3_bar_24dp;
            } else if (ping < NodeInfo.PING_BAD) {
                return R.drawable.ic_signal_wifi_2_bar_24dp;
            } else {
                return R.drawable.ic_signal_wifi_1_bar_24dp;
            }
        } else {
            return R.drawable.ic_signal_wifi_off_24dp;
        }
    }

    static public String getResponseErrorText(Context ctx, int responseCode) {
        if (responseCode == 0) {
            return ctx.getResources().getString(R.string.node_general_error);
        } else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            return ctx.getResources().getString(R.string.node_auth_error);
        } else {
            return ctx.getResources().getString(R.string.node_test_error, responseCode);
        }
    }
}
