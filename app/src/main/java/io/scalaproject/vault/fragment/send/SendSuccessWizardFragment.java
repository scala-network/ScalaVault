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

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Objects;

import io.scalaproject.vault.R;
import io.scalaproject.vault.data.PendingTx;
import io.scalaproject.vault.data.TxData;
import io.scalaproject.vault.model.Wallet;
import io.scalaproject.vault.util.Helper;

import timber.log.Timber;

public class SendSuccessWizardFragment extends SendWizardFragment {

    public static SendSuccessWizardFragment newInstance(Listener listener) {
        SendSuccessWizardFragment instance = new SendSuccessWizardFragment();
        instance.setSendListener(listener);
        return instance;
    }

    Listener sendListener;

    public SendSuccessWizardFragment setSendListener(Listener listener) {
        this.sendListener = listener;
        return this;
    }

    interface Listener {
        TxData getTxData();

        PendingTx getCommittedTx();

        void enableDone();

        SendFragment.Mode getMode();

        SendFragment.Listener getActivityCallback();
    }

    ImageButton bCopyTxId;
    private TextView tvTxId;
    private TextView tvTxAddress;
    private TextView tvTxPaymentId;
    private TextView tvTxAmount;
    private TextView tvTxFee;

    @SuppressLint({"ResourceType", "MissingInflatedId"})
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        Timber.d("onCreateView() %s", (String.valueOf(savedInstanceState)));

        View view = inflater.inflate(
                R.layout.fragment_send_success, container, false);

        bCopyTxId = view.findViewById(R.id.bCopyTxId);
        bCopyTxId.setOnClickListener(v -> {
            Helper.clipBoardCopy(requireActivity(), getString(R.string.label_send_txid), tvTxId.getText().toString());
            Toast.makeText(getActivity(), getString(R.string.message_copy_txid), Toast.LENGTH_SHORT).show();
        });

        tvTxId = view.findViewById(R.id.tvTxId);
        tvTxAddress = view.findViewById(R.id.tvTxAddress);
        tvTxPaymentId = view.findViewById(R.id.tvTxPaymentId);
        tvTxAmount = view.findViewById(R.id.tvTxAmount);
        tvTxFee = view.findViewById(R.id.tvTxFee);

        return view;
    }

    @Override
    public boolean onValidateFields() {
        return true;
    }

    @Override
    public void onPauseFragment() {
        super.onPauseFragment();
    }

    @Override
    public void onResumeFragment() {
        super.onResumeFragment();
        Timber.d("onResumeFragment()");
        Helper.hideKeyboard(getActivity());

        final TxData txData = sendListener.getTxData();
        tvTxAddress.setText(txData.getDestinationAddress());

        final PendingTx committedTx = sendListener.getCommittedTx();
        if (committedTx != null) {
            tvTxId.setText(committedTx.txId);
            tvTxId.setTextColor(getResources().getColor(R.color.c_blue));
            tvTxId.setPaintFlags(tvTxId.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

            tvTxId.setOnClickListener(v -> {
                String paymentURL = "https://explorer.scala.network/tx/" + tvTxId.getText();
                Uri uri = Uri.parse(paymentURL);
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            });

            bCopyTxId.setEnabled(true);
            bCopyTxId.setImageResource(R.drawable.ic_content_copy_24dp);

            if (sendListener.getActivityCallback().isStealthMode()
                    && (sendListener.getTxData().getAmount() == Wallet.SWEEP_ALL)) {
                tvTxAmount.setText(getString(R.string.street_sweep_amount));
            } else {
                tvTxAmount.setText(getString(R.string.send_amount, Helper.getDisplayAmount(committedTx.amount)));
            }
            tvTxFee.setText(getString(R.string.send_fee, Helper.getDisplayAmount(committedTx.fee)));
        }
        sendListener.enableDone();
    }
}
