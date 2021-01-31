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

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfDocument.Page;
import android.graphics.pdf.PdfDocument.PageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

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
    private Button btnReceipt;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        Timber.d("onCreateView() %s", (String.valueOf(savedInstanceState)));

        final View view = inflater.inflate(
                R.layout.fragment_send_success, container, false);

        bCopyTxId = view.findViewById(R.id.bCopyTxId);
        bCopyTxId.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Helper.clipBoardCopy(getActivity(), getString(R.string.label_send_txid), tvTxId.getText().toString());
                Toast.makeText(getActivity(), getString(R.string.message_copy_txid), Toast.LENGTH_SHORT).show();
            }
        });

        tvTxId = view.findViewById(R.id.tvTxId);
        tvTxAddress = view.findViewById(R.id.tvTxAddress);
        tvTxPaymentId = view.findViewById(R.id.tvTxPaymentId);
        tvTxAmount = view.findViewById(R.id.tvTxAmount);
        tvTxFee = view.findViewById(R.id.tvTxFee);
        btnReceipt = view.findViewById(R.id.btnReceipt);

        btnReceipt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnReceipt.setVisibility(View.GONE);

                // create a new document
                PdfDocument document = new PdfDocument();
                FileOutputStream fOut = null;
                Log.d("XLA", "Download Clicked");
                try {

                    // create a page description
                    PageInfo pageInfo = new PageInfo.Builder(view.getWidth(), view.getHeight(), 1).create();

                    // start a page
                    Page page = document.startPage(pageInfo);
                    view.draw(page.getCanvas());

                    // finish the page
                    document.finishPage(page);
                    // add more pages
                    // write the document content
                    String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/txs";

                    File dir = new File(path);

                    if (!dir.exists())
                        dir.mkdirs();

                    File file = new File(dir, "receipt.pdf");

                    fOut = new FileOutputStream(file);

                    document.writeTo(fOut);
                    fOut.flush();
                    fOut.close();

                    Intent intentShareFile = new Intent(Intent.ACTION_SEND);

                    if(file.exists()) {
                        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
                        StrictMode.setVmPolicy(builder.build());

                        intentShareFile.setType("application/pdf");
                        intentShareFile.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://"+file.getAbsolutePath()));
                        intentShareFile.putExtra(Intent.EXTRA_SUBJECT, "Sharing File...");
                        intentShareFile.putExtra(Intent.EXTRA_TEXT, file.getName());
                        intentShareFile.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(intentShareFile, "Share File"));
                    }


                } catch (FileNotFoundException de) {
                    Log.e("XLA", "DocumentException:" + de);
                } catch (IOException e) {
                    Log.e("XLA", "ioException:" + e);
                }
                finally {
                    document.close();
                }

                btnReceipt.setVisibility(View.VISIBLE);

            }
        });

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
