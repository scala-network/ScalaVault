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

package io.scalaproject.vault.layout;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.Bitmap;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.scalaproject.vault.R;
import io.scalaproject.vault.data.Contact;
import io.scalaproject.vault.model.TransactionInfo;
import io.scalaproject.vault.model.Wallet;
import io.scalaproject.vault.util.Helper;
import io.scalaproject.vault.data.UserNotes;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import timber.log.Timber;

public class TransactionInfoAdapter extends RecyclerView.Adapter<TransactionInfoAdapter.ViewHolder> {
    @SuppressLint("SimpleDateFormat")
    private final static SimpleDateFormat DATETIME_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private final int outboundColour;
    private final int inboundColour;
    private final int pendingColour;
    private final int failedColour;

    public interface OnInteractionListener {
        void onInteraction(View view, TransactionInfo item);
    }

    public interface OnFindContactListener {
        Contact onFindContact(final TransactionInfo txInfo);
    }

    private final List<TransactionInfo> infoItems;
    private final OnInteractionListener onInteractionListener;
    private final OnFindContactListener onFindContactListener;

    private final Context context;

    public TransactionInfoAdapter(Context context, OnInteractionListener onInteractionListener,
                                  OnFindContactListener onFindContactListener) {
        this.context = context;
        inboundColour = ContextCompat.getColor(context, R.color.tx_green);
        outboundColour = ContextCompat.getColor(context, R.color.tx_red);
        pendingColour = ContextCompat.getColor(context, R.color.tx_pending);
        failedColour = ContextCompat.getColor(context, R.color.tx_failed);

        infoItems = new ArrayList<>();

        this.onInteractionListener = onInteractionListener;
        this.onFindContactListener = onFindContactListener;

        Calendar cal = Calendar.getInstance();
        TimeZone tz = cal.getTimeZone(); //get the local time zone.
        DATETIME_FORMATTER.setTimeZone(tz);
    }

    public boolean needsTransactionUpdateOnNewBlock() {
        return (infoItems.size() > 0) && !infoItems.get(0).isConfirmed();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.bind(position);
    }

    @Override
    public int getItemCount() {
        return infoItems.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setInfos(List<TransactionInfo> data) {
        // TODO do stuff with data so we can really recycle elements (i.e. add only new tx)
        // as the TransactionInfo items are always recreated, we cannot recycle
        infoItems.clear();
        if (data != null) {
            Timber.d("setInfos %s", data.size());
            infoItems.addAll(data);
            Collections.sort(infoItems);
        } else {
            Timber.d("setInfos null");
        }

        notifyDataSetChanged();
    }

    public void removeItem(int position) {
        infoItems.remove(position);
        notifyItemRemoved(position);
    }

    public TransactionInfo getItem(int position) {
        return infoItems.get(position);
    }

    class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        final ImageView ivTxType;
        final TextView tvAmount;
        final TextView tvFee;
        final TextView tvPaymentId;
        final TextView tvDateTime;
        TransactionInfo infoItem;

        ViewHolder(View itemView) {
            super(itemView);
            ivTxType = itemView.findViewById(R.id.ivTxType);
            tvAmount = itemView.findViewById(R.id.tx_amount);
            tvFee = itemView.findViewById(R.id.tx_fee);
            tvPaymentId = itemView.findViewById(R.id.tx_paymentid);
            tvDateTime = itemView.findViewById(R.id.tx_datetime);
        }

        private String getDateTime(long time) {
            return DATETIME_FORMATTER.format(new Date(time * 1000));
        }

        private void setTxColour(int clr) {
            tvAmount.setTextColor(clr);
        }

        @SuppressLint({"UseCompatLoadingForColorStateLists", "UseCompatLoadingForDrawables", "SetTextI18n"})
        void bind(int position) {
            this.infoItem = infoItems.get(position);

            UserNotes userNotes = new UserNotes(infoItem.notes);

            LinearLayout llTxType = itemView.findViewById(R.id.llTxType);

            // just in case
            llTxType.setBackgroundTintList(itemView.getResources().getColorStateList(R.color.bg_grey));

            int dim = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, itemView.getResources().getDisplayMetrics());
            ivTxType.getLayoutParams().height = dim;
            ivTxType.getLayoutParams().width = dim;

            String displayAmount = Wallet.getDisplayAmount(infoItem.amount);
            if (infoItem.direction == TransactionInfo.Direction.Direction_Out) {
                ivTxType.setImageDrawable(itemView.getResources().getDrawable(R.drawable.ic_tx_out));
                tvAmount.setText(displayAmount.startsWith("-") ? context.getString(R.string.tx_list_amount_negative, displayAmount) : "-" + context.getString(R.string.tx_list_amount_negative, displayAmount));
                tvAmount.setTextColor(context.getResources().getColor(R.color.c_white));
            } else {
                ivTxType.setImageDrawable(itemView.getResources().getDrawable(R.drawable.ic_tx_in));
                tvAmount.setText("+" + context.getString(R.string.tx_list_amount_positive, displayAmount));
                tvAmount.setTextColor(context.getResources().getColor(R.color.c_green));
            }

            if ((infoItem.fee > 0)) {
                String fee = Wallet.getDisplayAmount(infoItem.fee);
                tvFee.setText(context.getString(R.string.tx_list_fee, fee));
                tvFee.setVisibility(View.VISIBLE);
            } else {
                tvFee.setText("");
                tvFee.setVisibility(View.GONE);
            }
            if (infoItem.isFailed) {
                this.tvAmount.setText(context.getString(R.string.tx_list_amount_failed, displayAmount));
                this.tvFee.setText(context.getString(R.string.tx_list_failed_text));
                tvFee.setVisibility(View.VISIBLE);
                setTxColour(failedColour);
            } else if (infoItem.isPending) {
                setTxColour(pendingColour);
            } else if (infoItem.direction == TransactionInfo.Direction.Direction_In) {
                setTxColour(inboundColour);
            } else {
                setTxColour(outboundColour);
            }

            // Check if this address is associated to a contact in address book
            Contact contact = null;
            if (infoItem.direction == TransactionInfo.Direction.Direction_Out)
                contact = onFindContactListener.onFindContact(infoItem);

            if(contact == null) {
                if ((userNotes.note.isEmpty())) {
                    this.tvPaymentId.setText(infoItem.paymentId.equals("0000000000000000") ?
                            (infoItem.subaddress != 0 ?
                                    (context.getString(R.string.tx_subaddress, infoItem.subaddress)) :
                                    Helper.getPrettyAddress(infoItem.hash)) :
                            Helper.getPrettyAddress(infoItem.hash));
                } else {
                    this.tvPaymentId.setText(Helper.getTruncatedString(userNotes.note, 20));
                }
            }
            else {
                this.tvPaymentId.setText(Helper.getTruncatedString(contact.getName(), 20));

                llTxType.setBackgroundTintList(itemView.getResources().getColorStateList(R.color.bg_lighter));

                Bitmap avatar = contact.getAvatar();
                if(avatar != null) {
                    dim = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, itemView.getResources().getDisplayMetrics());
                    ivTxType.getLayoutParams().height = dim;
                    ivTxType.getLayoutParams().width = dim;

                    ivTxType.setImageBitmap(avatar);
                }
                else {
                    dim = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36, itemView.getResources().getDisplayMetrics());
                    ivTxType.getLayoutParams().height = dim;
                    ivTxType.getLayoutParams().width = dim;

                    ivTxType.setImageBitmap(Helper.getBitmap(itemView.getContext(), R.drawable.ic_contact_avatar));
                }
            }

            this.tvDateTime.setText(getDateTime(infoItem.timestamp));

            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            if (onInteractionListener != null) {
                int position = getAdapterPosition(); // gets item position
                if (position != RecyclerView.NO_POSITION) { // Check if an item was deleted, but the user clicked it before the UI removed it
                    onInteractionListener.onInteraction(view, infoItems.get(position));
                }
            }
        }
    }
}
