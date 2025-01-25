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

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import androidx.appcompat.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Objects;

import io.scalaproject.vault.R;
import io.scalaproject.vault.data.TxData;
import io.scalaproject.vault.model.PendingTransaction;
import io.scalaproject.vault.model.Wallet;
import io.scalaproject.vault.util.Helper;
import io.scalaproject.vault.data.UserNotes;

import timber.log.Timber;

public class SendConfirmWizardFragment extends SendWizardFragment implements SendConfirm {

    public static SendConfirmWizardFragment newInstance(Listener listener) {
        SendConfirmWizardFragment instance = new SendConfirmWizardFragment();
        instance.setSendListener(listener);
        return instance;
    }

    Listener sendListener;

    public SendConfirmWizardFragment setSendListener(Listener listener) {
        this.sendListener = listener;
        return this;
    }

    interface Listener {
        SendFragment.Listener getActivityCallback();

        TxData getTxData();

        void commitTransaction();

        void disposeTransaction();

        SendFragment.Mode getMode();
    }

    private TextView tvTxAddress;
    private TextView tvTxNotes;
    private TextView tvTxAmount;
    private TextView tvTxFee;
    private TextView tvTxTotal;
    private View llProgress;
    private View bSend;
    private View llConfirmSend;
    private View pbProgressSend;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        Timber.d("onCreateView() %s", (String.valueOf(savedInstanceState)));

        View view = inflater.inflate(
                R.layout.fragment_send_confirm, container, false);

        tvTxAddress = view.findViewById(R.id.tvTxAddress);
        tvTxNotes = view.findViewById(R.id.tvTxNotes);
        tvTxAmount = view.findViewById(R.id.tvTxAmount);
        tvTxFee = view.findViewById(R.id.tvTxFee);
        tvTxTotal = view.findViewById(R.id.tvTxTotal);

        llProgress = view.findViewById(R.id.llProgress);
        pbProgressSend = view.findViewById(R.id.pbProgressSend);
        llConfirmSend = view.findViewById(R.id.llConfirmSend);

        bSend = view.findViewById(R.id.bSend);
        bSend.setEnabled(false);
        bSend.setOnClickListener(v -> {
            Timber.d("bSend.setOnClickListener");
            bSend.setEnabled(false);
            preSend();
        });
        return view;
    }

    boolean inProgress = false;

    public void hideProgress() {
        llProgress.setVisibility(View.INVISIBLE);
        inProgress = false;
    }

    public void showProgress() {
        llProgress.setVisibility(View.VISIBLE);
        inProgress = true;
    }

    PendingTransaction pendingTransaction = null;

    @Override
    // callback from wallet when PendingTransaction created
    public void transactionCreated(String txTag, PendingTransaction pendingTransaction) {
        // ignore txTag - the app flow ensures this is the correct tx
        // TODO: use the txTag
        hideProgress();
        if (isResumed) {
            this.pendingTransaction = pendingTransaction;
            refreshTransactionDetails();
        } else {
            sendListener.disposeTransaction();
        }
    }

    void send() {
        sendListener.commitTransaction();
        pbProgressSend.setVisibility(View.VISIBLE);
    }

    @Override
    public void sendFailed(String errorText) {
        pbProgressSend.setVisibility(View.INVISIBLE);
        showAlert(getString(R.string.send_create_tx_error_title), errorText);
    }

    @Override
    public void createTransactionFailed(String errorText) {
        hideProgress();
        showAlert(getString(R.string.send_create_tx_error_title), errorText);
    }

    private void showAlert(String title, String message) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity(), R.style.MaterialAlertDialogCustom);
        builder.setCancelable(true).
                setTitle(title).
                setMessage(message).
                create().
                show();
    }

    @Override
    public boolean onValidateFields() {
        return true;
    }

    private boolean isResumed = false;

    @Override
    public void onPauseFragment() {
        isResumed = false;
        pendingTransaction = null;
        sendListener.disposeTransaction();
        refreshTransactionDetails();
        super.onPauseFragment();
    }

    @Override
    public void onResumeFragment() {
        super.onResumeFragment();
        Timber.d("onResumeFragment()");
        Helper.hideKeyboard(getActivity());
        isResumed = true;

        final TxData txData = sendListener.getTxData();
        tvTxAddress.setText(txData.getDestinationAddress());
        UserNotes notes = sendListener.getTxData().getUserNotes();
        if ((notes != null) && (!notes.note.isEmpty())) {
            tvTxNotes.setText(notes.note);
        } else {
            tvTxNotes.setText("-");
        }
        refreshTransactionDetails();
        if ((pendingTransaction == null) && (!inProgress)) {
            showProgress();
            prepareSend(txData);
        }
    }

    void refreshTransactionDetails() {
        Timber.d("refreshTransactionDetails()");
        if (pendingTransaction != null) {
            llConfirmSend.setVisibility(View.VISIBLE);
            bSend.setEnabled(true);
            tvTxFee.setText(Wallet.getDisplayAmount(pendingTransaction.getFee()));
            if (getActivityCallback().isStealthMode()
                    && (sendListener.getTxData().getAmount() == Wallet.SWEEP_ALL)) {
                tvTxAmount.setText(getString(R.string.street_sweep_amount));
                tvTxTotal.setText(getString(R.string.street_sweep_amount));
            } else {
                tvTxAmount.setText(Wallet.getDisplayAmount(pendingTransaction.getAmount()));
                tvTxTotal.setText(Wallet.getDisplayAmount(
                        pendingTransaction.getFee() + pendingTransaction.getAmount()));
            }
        } else {
            llConfirmSend.setVisibility(View.GONE);
            bSend.setEnabled(false);
        }
    }

    public void preSend() {
        final Activity activity = getActivity();
        View promptsView = getLayoutInflater().inflate(R.layout.prompt_password, null);
        assert activity != null;
        MaterialAlertDialogBuilder alertDialogBuilder = new MaterialAlertDialogBuilder(activity, R.style.MaterialAlertDialogCustom);
        alertDialogBuilder.setView(promptsView);

        final TextInputLayout etPassword = promptsView.findViewById(R.id.etPassword);
        etPassword.setHint(getString(R.string.prompt_send_password));

        Objects.requireNonNull(etPassword.getEditText()).addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (etPassword.getError() != null) {
                    etPassword.setError(null);
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {
            }
        });

        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton(getString(R.string.label_ok), (dialog, id) -> {
                    String pass = etPassword.getEditText().getText().toString();
                    if (getActivityCallback().verifyWalletPassword(pass)) {
                        dialog.dismiss();
                        Helper.hideKeyboardAlways(activity);
                        send();
                    } else {
                        etPassword.setError(getString(R.string.bad_password));
                    }
                })
                .setNegativeButton(getString(R.string.label_cancel),
                        (dialog, id) -> {
                            Helper.hideKeyboardAlways(activity);
                            dialog.cancel();
                            bSend.setEnabled(true); // allow to try again
                        });

        final AlertDialog passwordDialog = alertDialogBuilder.create();
        passwordDialog.setOnShowListener(dialog -> {
            Button button = passwordDialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
            button.setOnClickListener(view -> {
                String pass = etPassword.getEditText().getText().toString();
                if (getActivityCallback().verifyWalletPassword(pass)) {
                    Helper.hideKeyboardAlways(activity);
                    passwordDialog.dismiss();
                    send();
                } else {
                    etPassword.setError(getString(R.string.bad_password));
                }
            });
        });

        Helper.showKeyboard(passwordDialog);

        // accept keyboard "ok"
        etPassword.getEditText().setOnEditorActionListener((v, actionId, event) -> {
            if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) && (event.getAction() == KeyEvent.ACTION_DOWN))
                    || (actionId == EditorInfo.IME_ACTION_DONE)) {
                String pass = etPassword.getEditText().getText().toString();
                if (getActivityCallback().verifyWalletPassword(pass)) {
                    Helper.hideKeyboardAlways(activity);
                    passwordDialog.dismiss();
                    send();
                } else {
                    etPassword.setError(getString(R.string.bad_password));
                }
                return true;
            }
            return false;
        });

        if (Helper.preventScreenshot()) {
            Objects.requireNonNull(passwordDialog.getWindow()).setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }

        passwordDialog.show();
    }

    // creates a pending transaction and calls us back with transactionCreated()
    // or createTransactionFailed()
    void prepareSend(TxData txData) {
        getActivityCallback().onPrepareSend(null, txData);
    }

    SendFragment.Listener getActivityCallback() {
        return sendListener.getActivityCallback();
    }
}
