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

package io.scalaproject.vault.fragment.send;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import io.scalaproject.vault.R;
import io.scalaproject.vault.data.PendingTx;
import io.scalaproject.vault.data.TxDataBtc;
import io.scalaproject.vault.util.Helper;
import io.scalaproject.vault.util.OkHttpHelper;
import io.scalaproject.vault.xlato.XlaToException;
import io.scalaproject.vault.xlato.api.QueryOrderStatus;
import io.scalaproject.vault.xlato.api.XlaToApi;
import io.scalaproject.vault.xlato.api.XlaToCallback;
import io.scalaproject.vault.xlato.network.XlaToApiImpl;

import java.text.NumberFormat;
import java.util.Locale;

import timber.log.Timber;

public class SendBtcSuccessWizardFragment extends SendWizardFragment {

    public static SendBtcSuccessWizardFragment newInstance(SendSuccessWizardFragment.Listener listener) {
        SendBtcSuccessWizardFragment instance = new SendBtcSuccessWizardFragment();
        instance.setSendListener(listener);
        return instance;
    }

    SendSuccessWizardFragment.Listener sendListener;

    public SendBtcSuccessWizardFragment setSendListener(SendSuccessWizardFragment.Listener listener) {
        this.sendListener = listener;
        return this;
    }

    ImageButton bCopyTxId;
    private TextView tvTxId;
    private TextView tvTxAddress;
    private TextView tvTxPaymentId;
    private TextView tvTxAmount;
    private TextView tvTxFee;
    private TextView tvxlaToAmount;
    private TextView tvxlaToStatus;
    private ImageView ivxlaToStatus;
    private ImageView ivxlaToStatusBig;
    private ProgressBar pbxlato;
    private TextView tvTxxlaToKey;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        Timber.d("onCreateView() %s", (String.valueOf(savedInstanceState)));

        View view = inflater.inflate(
                R.layout.fragment_send_btc_success, container, false);

        bCopyTxId = view.findViewById(R.id.bCopyTxId);
        bCopyTxId.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Helper.clipBoardCopy(getActivity(), getString(R.string.label_send_txid), tvTxId.getText().toString());
                Toast.makeText(getActivity(), getString(R.string.message_copy_txid), Toast.LENGTH_SHORT).show();
            }
        });

        tvxlaToAmount = view.findViewById(R.id.tvxlaToAmount);
        tvxlaToStatus = view.findViewById(R.id.tvxlaToStatus);
        ivxlaToStatus = view.findViewById(R.id.ivxlaToStatus);
        ivxlaToStatusBig = view.findViewById(R.id.ivxlaToStatusBig);

        tvTxId = view.findViewById(R.id.tvTxId);
        tvTxAddress = view.findViewById(R.id.tvTxAddress);
        tvTxPaymentId = view.findViewById(R.id.tvTxPaymentId);
        tvTxAmount = view.findViewById(R.id.tvTxAmount);
        tvTxFee = view.findViewById(R.id.tvTxFee);

        pbxlato = view.findViewById(R.id.pbxlato);
        pbxlato.getIndeterminateDrawable().setColorFilter(0x61000000, android.graphics.PorterDuff.Mode.MULTIPLY);

        tvTxxlaToKey = view.findViewById(R.id.tvTxxlaToKey);
        tvTxxlaToKey.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Helper.clipBoardCopy(getActivity(), getString(R.string.label_copy_xlatokey), tvTxxlaToKey.getText().toString());
                Toast.makeText(getActivity(), getString(R.string.message_copy_xlatokey), Toast.LENGTH_SHORT).show();
            }
        });

        return view;
    }

    @Override
    public boolean onValidateFields() {
        return true;
    }

    private boolean isResumed = false;

    @Override
    public void onPauseFragment() {
        isResumed = false;
        super.onPauseFragment();
    }

    TxDataBtc btcData = null;

    @Override
    public void onResumeFragment() {
        super.onResumeFragment();
        Timber.d("onResumeFragment()");
        Helper.hideKeyboard(getActivity());
        isResumed = true;

        btcData = (TxDataBtc) sendListener.getTxData();
        tvTxAddress.setText(btcData.getDestinationAddress());

        final PendingTx committedTx = sendListener.getCommittedTx();
        if (committedTx != null) {
            tvTxId.setText(committedTx.txId);
            bCopyTxId.setEnabled(true);
            bCopyTxId.setImageResource(R.drawable.ic_content_copy_24dp);
            tvTxAmount.setText(getString(R.string.send_amount, Helper.getDisplayAmount(committedTx.amount)));
            tvTxFee.setText(getString(R.string.send_fee, Helper.getDisplayAmount(committedTx.fee)));
            if (btcData != null) {
                NumberFormat df = NumberFormat.getInstance(Locale.US);
                df.setMaximumFractionDigits(12);
                String btcAmount = df.format(btcData.getBtcAmount());
                tvxlaToAmount.setText(getString(R.string.info_send_xlato_success_btc, btcAmount));
                //TODO         btcData.getBtcAddress();
                tvTxxlaToKey.setText(btcData.getxlatoUuid());
                queryOrder();
            } else {
                throw new IllegalStateException("btcData is null");
            }
        }
        sendListener.enableDone();
    }

    private final int QUERY_INTERVAL = 1000; // ms

    private void processQueryOrder(final QueryOrderStatus status) {
        Timber.d("processQueryOrder %s for %s", status.getState().toString(), status.getUuid());
        if (!btcData.getxlatoUuid().equals(status.getUuid()))
            throw new IllegalStateException("UUIDs do not match!");
        if (isResumed && (getView() != null))
            getView().post(new Runnable() {
                @Override
                public void run() {
                    showxlaToStatus(status);
                    if (!status.isTerminal()) {
                        getView().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                queryOrder();
                            }
                        }, QUERY_INTERVAL);
                    }
                }
            });
    }

    private void queryOrder() {
        Timber.d("queryOrder(%s)", btcData.getxlatoUuid());
        if (!isResumed) return;
        getxlaToApi().queryOrderStatus(btcData.getxlatoUuid(), new XlaToCallback<QueryOrderStatus>() {
            @Override
            public void onSuccess(QueryOrderStatus status) {
                if (!isAdded()) return;
                processQueryOrder(status);
            }

            @Override
            public void onError(final Exception ex) {
                if (!isResumed) return;
                Timber.e(ex);
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (ex instanceof XlaToException) {
                            Toast.makeText(getActivity(), ((XlaToException) ex).getError().getErrorMsg(), Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(getActivity(), ex.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        });
    }

    private int statusResource = 0;

    void showxlaToStatus(final QueryOrderStatus status) {
        if (status.isError()) {
            tvxlaToStatus.setText(getString(R.string.info_send_xlato_error, status.toString()));
            statusResource = R.drawable.ic_error_red_24dp;
            pbxlato.getIndeterminateDrawable().setColorFilter(0xff8b0000, android.graphics.PorterDuff.Mode.MULTIPLY);
        } else if (status.isSent()) {
            tvxlaToStatus.setText(getString(R.string.info_send_xlato_sent));
            statusResource = R.drawable.ic_success_green_24dp;
            pbxlato.getIndeterminateDrawable().setColorFilter(0xFF417505, android.graphics.PorterDuff.Mode.MULTIPLY);
        } else if (status.isPending()) {
            if (status.isPaid()) {
                tvxlaToStatus.setText(getString(R.string.info_send_xlato_paid));
            } else {
                tvxlaToStatus.setText(getString(R.string.info_send_xlato_unpaid));
            }
            statusResource = R.drawable.ic_pending_24dp;
            pbxlato.getIndeterminateDrawable().setColorFilter(0xFFFF6105, android.graphics.PorterDuff.Mode.MULTIPLY);
        } else {
            throw new IllegalStateException("status is broken: " + status.toString());
        }
        ivxlaToStatus.setImageResource(statusResource);
        if (status.isTerminal()) {
            pbxlato.setVisibility(View.INVISIBLE);
            ivxlaToStatus.setVisibility(View.GONE);
            ivxlaToStatusBig.setImageResource(statusResource);
            ivxlaToStatusBig.setVisibility(View.VISIBLE);
        }
    }

    private XlaToApi xlaToApi = null;

    private final XlaToApi getxlaToApi() {
        if (xlaToApi == null) {
            synchronized (this) {
                if (xlaToApi == null) {
                    xlaToApi = new XlaToApiImpl(OkHttpHelper.getOkHttpClient(),
                            Helper.getxlaToBaseUrl());
                }
            }
        }
        return xlaToApi;
    }
}
