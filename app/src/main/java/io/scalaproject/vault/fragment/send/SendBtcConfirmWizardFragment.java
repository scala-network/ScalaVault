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
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
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
import android.widget.Toast;

import io.scalaproject.vault.R;
import io.scalaproject.vault.data.TxData;
import io.scalaproject.vault.data.TxDataBtc;
import io.scalaproject.vault.model.PendingTransaction;
import io.scalaproject.vault.model.Wallet;
import io.scalaproject.vault.util.Helper;
import io.scalaproject.vault.util.OkHttpHelper;
import io.scalaproject.vault.widget.SendProgressView;
import io.scalaproject.vault.xlato.XlaToError;
import io.scalaproject.vault.xlato.XlaToException;
import io.scalaproject.vault.xlato.api.CreateOrder;
import io.scalaproject.vault.xlato.api.QueryOrderStatus;
import io.scalaproject.vault.xlato.api.XlaToApi;
import io.scalaproject.vault.xlato.api.XlaToCallback;
import io.scalaproject.vault.xlato.network.XlaToApiImpl;

import java.text.NumberFormat;
import java.util.Locale;

import timber.log.Timber;

public class SendBtcConfirmWizardFragment extends SendWizardFragment implements SendConfirm {
    private final int QUERY_INTERVAL = 500;//ms

    public static SendBtcConfirmWizardFragment newInstance(SendConfirmWizardFragment.Listener listener) {
        SendBtcConfirmWizardFragment instance = new SendBtcConfirmWizardFragment();
        instance.setSendListener(listener);
        return instance;
    }

    SendConfirmWizardFragment.Listener sendListener;

    public SendBtcConfirmWizardFragment setSendListener(SendConfirmWizardFragment.Listener listener) {
        this.sendListener = listener;
        return this;
    }

    private View llStageA;
    private SendProgressView evStageA;
    private View llStageB;
    private SendProgressView evStageB;
    private View llStageC;
    private SendProgressView evStageC;
    private TextView tvTxBtcAmount;
    private TextView tvTxBtcRate;
    private TextView tvTxBtcAddress;
    private TextView tvTxxlaToKey;
    private TextView tvTxFee;
    private TextView tvTxTotal;
    private View llConfirmSend;
    private Button bSend;
    private View pbProgressSend;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        Timber.d("onCreateView(%s)", (String.valueOf(savedInstanceState)));

        View view = inflater.inflate(
                R.layout.fragment_send_btc_confirm, container, false);

        tvTxBtcAddress = view.findViewById(R.id.tvTxBtcAddress);
        tvTxBtcAmount = view.findViewById(R.id.tvTxBtcAmount);
        tvTxBtcRate = view.findViewById(R.id.tvTxBtcRate);
        tvTxxlaToKey = view.findViewById(R.id.tvTxxlaToKey);

        tvTxFee = view.findViewById(R.id.tvTxFee);
        tvTxTotal = view.findViewById(R.id.tvTxTotal);


        llStageA = view.findViewById(R.id.llStageA);
        evStageA = view.findViewById(R.id.evStageA);
        llStageB = view.findViewById(R.id.llStageB);
        evStageB = view.findViewById(R.id.evStageB);
        llStageC = view.findViewById(R.id.llStageC);
        evStageC = view.findViewById(R.id.evStageC);

        tvTxxlaToKey.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Helper.clipBoardCopy(getActivity(), getString(R.string.label_copy_xlatokey), tvTxxlaToKey.getText().toString());
                Toast.makeText(getActivity(), getString(R.string.message_copy_xlatokey), Toast.LENGTH_SHORT).show();
            }
        });

        llConfirmSend = view.findViewById(R.id.llConfirmSend);
        pbProgressSend = view.findViewById(R.id.pbProgressSend);

        bSend = view.findViewById(R.id.bSend);
        bSend.setEnabled(false);

        bSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Timber.d("bSend.setOnClickListener");
                bSend.setEnabled(false);
                preSend();
            }
        });

        return view;
    }

    int inProgress = 0;
    final static int STAGE_X = 0;
    final static int STAGE_A = 1;
    final static int STAGE_B = 2;
    final static int STAGE_C = 3;

    private void showProgress(int stage, String progressText) {
        Timber.d("showProgress(%d)", stage);
        inProgress = stage;
        switch (stage) {
            case STAGE_A:
                evStageA.showProgress(progressText);
                break;
            case STAGE_B:
                evStageB.showProgress(progressText);
                break;
            case STAGE_C:
                evStageC.showProgress(progressText);
                break;
            default:
                throw new IllegalStateException("unknown stage " + stage);
        }
    }

    public void hideProgress() {
        Timber.d("hideProgress(%d)", inProgress);
        switch (inProgress) {
            case STAGE_A:
                evStageA.hideProgress();
                llStageA.setVisibility(View.VISIBLE);
                break;
            case STAGE_B:
                evStageB.hideProgress();
                llStageB.setVisibility(View.VISIBLE);
                break;
            case STAGE_C:
                evStageC.hideProgress();
                llStageC.setVisibility(View.VISIBLE);
                break;
            default:
                throw new IllegalStateException("unknown stage " + inProgress);
        }
        inProgress = STAGE_X;
    }

    public void showStageError(String code, String message, String solution) {
        switch (inProgress) {
            case STAGE_A:
                evStageA.showMessage(code, message, solution);
                break;
            case STAGE_B:
                evStageB.showMessage(code, message, solution);
                break;
            case STAGE_C:
                evStageC.showMessage(code, message, solution);
                break;
            default:
                throw new IllegalStateException("unknown stage");
        }
        inProgress = STAGE_X;
    }

    PendingTransaction pendingTransaction = null;

    void send() {
        Timber.d("SEND @%d", sendCountdown);
        if (sendCountdown <= 0) {
            Timber.i("User waited too long in password dialog.");
            Toast.makeText(getContext(), getString(R.string.send_xlato_timeout), Toast.LENGTH_SHORT).show();
            return;
        }
        sendListener.getTxData().getUserNotes().setxlatoStatus(xlatoStatus);
        ((TxDataBtc) sendListener.getTxData()).setxlatoUuid(xlatoStatus.getUuid());
        // TODO make method in TxDataBtc to set both of the above in one go
        sendListener.commitTransaction();
        pbProgressSend.setVisibility(View.VISIBLE);
    }

    @Override
    public void sendFailed(String error) {
        pbProgressSend.setVisibility(View.INVISIBLE);
        Toast.makeText(getContext(), getString(R.string.status_transaction_failed, error), Toast.LENGTH_LONG).show();
    }

    @Override
    // callback from wallet when PendingTransaction created (started by prepareSend() here
    public void transactionCreated(final String txTag, final PendingTransaction pendingTransaction) {
        if (isResumed
                && (inProgress == STAGE_C)
                && (xlatoStatus != null)
                && (xlatoStatus.isCreated()
                && (xlatoStatus.getUuid().equals(txTag)))) {
            this.pendingTransaction = pendingTransaction;
            getView().post(new Runnable() {
                @Override
                public void run() {
                    hideProgress();
                    tvTxFee.setText(Wallet.getDisplayAmount(pendingTransaction.getFee()));
                    tvTxTotal.setText(Wallet.getDisplayAmount(
                            pendingTransaction.getFee() + pendingTransaction.getAmount()));
                    updateSendButton();
                }
            });
        } else {
            this.pendingTransaction = null;
            sendListener.disposeTransaction();
        }
    }

    @Override
    public void createTransactionFailed(String errorText) {
        Timber.e("CREATE TX FAILED");
        if (pendingTransaction != null) {
            throw new IllegalStateException("pendingTransaction is not null");
        }
        showStageError(getString(R.string.send_create_tx_error_title),
                errorText,
                getString(R.string.text_noretry_scala));
    }

    @Override
    public boolean onValidateFields() {
        return true;
    }

    private boolean isResumed = false;

    @Override
    public void onPauseFragment() {
        isResumed = false;
        stopSendTimer();
        sendListener.disposeTransaction();
        pendingTransaction = null;
        inProgress = STAGE_X;
        updateSendButton();
        super.onPauseFragment();
    }

    @Override
    public void onResumeFragment() {
        super.onResumeFragment();
        Timber.d("onResumeFragment()");
        if (sendListener.getMode() != SendFragment.Mode.BTC) {
            throw new IllegalStateException("Mode is not BTC!");
        }
        Helper.hideKeyboard(getActivity());
        llStageA.setVisibility(View.INVISIBLE);
        evStageA.hideProgress();
        llStageB.setVisibility(View.INVISIBLE);
        evStageB.hideProgress();
        llStageC.setVisibility(View.INVISIBLE);
        evStageC.hideProgress();
        isResumed = true;
        if ((pendingTransaction == null) && (inProgress == STAGE_X)) {
            createOrder();
        } // otherwise just sit there blank
        // TODO: don't sit there blank - can this happen? should we just die?
    }

    private int sendCountdown = 0;
    private static final int XLATO_COUNTDOWN = 10 * 60; // 10 minutes
    private static final int XLATO_COUNTDOWN_STEP = 1; // 1 second

    Runnable updateRunnable = null;

    void startSendTimer() {
        Timber.d("startSendTimer()");
        sendCountdown = XLATO_COUNTDOWN;
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isAdded())
                    return;
                Timber.d("updateTimer()");
                if (sendCountdown <= 0) {
                    bSend.setEnabled(false);
                    sendCountdown = 0;
                    Toast.makeText(getContext(), getString(R.string.send_xlato_timeout), Toast.LENGTH_SHORT).show();
                }
                int minutes = sendCountdown / 60;
                int seconds = sendCountdown % 60;
                String t = String.format("%d:%02d", minutes, seconds);
                bSend.setText(getString(R.string.send_send_timed_label, t));
                if (sendCountdown > 0) {
                    sendCountdown -= XLATO_COUNTDOWN_STEP;
                    getView().postDelayed(this, XLATO_COUNTDOWN_STEP * 1000);
                }
            }
        };
        getView().post(updateRunnable);
    }

    void stopSendTimer() {
        getView().removeCallbacks(updateRunnable);
    }

    void updateSendButton() {
        Timber.d("updateSendButton()");
        if (pendingTransaction != null) {
            llConfirmSend.setVisibility(View.VISIBLE);
            bSend.setEnabled(sendCountdown > 0);
        } else {
            llConfirmSend.setVisibility(View.GONE);
            bSend.setEnabled(false);
        }
    }

    public void preSend() {
        final Activity activity = getActivity();
        View promptsView = getLayoutInflater().inflate(R.layout.prompt_password, null);
        MaterialAlertDialogBuilder alertDialogBuilder = new MaterialAlertDialogBuilder(activity, R.style.MaterialAlertDialogCustom);
        alertDialogBuilder.setView(promptsView);

        final TextInputLayout etPassword = promptsView.findViewById(R.id.etPassword);
        etPassword.setHint(getString(R.string.prompt_send_password));

        etPassword.getEditText().addTextChangedListener(new TextWatcher() {
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
                .setPositiveButton(getString(R.string.label_ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        String pass = etPassword.getEditText().getText().toString();
                        if (getActivityCallback().verifyWalletPassword(pass)) {
                            dialog.dismiss();
                            Helper.hideKeyboardAlways(activity);
                            send();
                        } else {
                            etPassword.setError(getString(R.string.bad_password));
                        }
                    }
                })
                .setNegativeButton(getString(R.string.label_cancel),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                Helper.hideKeyboardAlways(activity);
                                dialog.cancel();
                                bSend.setEnabled(sendCountdown > 0); // allow to try again
                            }
                        });

        final androidx.appcompat.app.AlertDialog passwordDialog = alertDialogBuilder.create();
        passwordDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                Button button = ((android.app.AlertDialog) dialog).getButton(android.app.AlertDialog.BUTTON_POSITIVE);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String pass = etPassword.getEditText().getText().toString();
                        if (getActivityCallback().verifyWalletPassword(pass)) {
                            Helper.hideKeyboardAlways(activity);
                            passwordDialog.dismiss();
                            send();
                        } else {
                            etPassword.setError(getString(R.string.bad_password));
                        }
                    }
                });
            }
        });

        Helper.showKeyboard(passwordDialog);

        // accept keyboard "ok"
        etPassword.getEditText().setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
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
            }
        });

        if (Helper.preventScreenshot()) {
            passwordDialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(20,20,50,10);

        Button posButton = passwordDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        posButton.setLayoutParams(params);

        passwordDialog.show();
    }

    // creates a pending transaction and calls us back with transactionCreated()
    // or createTransactionFailed()
    void prepareSend() {
        if (!isResumed) return;
        if ((xlatoStatus == null)) {
            throw new IllegalStateException("xlatoStatus is null");
        }
        if ((!xlatoStatus.isCreated())) {
            throw new IllegalStateException("order is not created");
        }
        showProgress(3, getString(R.string.label_send_progress_create_tx));
        TxData txData = sendListener.getTxData();
        txData.setDestinationAddress(xlatoStatus.getReceivingSubaddress());
        txData.setAmount(Wallet.getAmountFromDouble(xlatoStatus.getIncomingAmountTotal()));
        getActivityCallback().onPrepareSend(xlatoStatus.getUuid(), txData);
    }

    SendFragment.Listener getActivityCallback() {
        return sendListener.getActivityCallback();
    }

    private CreateOrder xlatoOrder = null;

    private void processCreateOrder(final CreateOrder createOrder) {
        Timber.d("processCreateOrder %s", createOrder.getUuid());
        xlatoOrder = createOrder;
        if (QueryOrderStatus.State.TO_BE_CREATED.toString().equals(createOrder.getState())) {
            getView().post(new Runnable() {
                @Override
                public void run() {
                    tvTxxlaToKey.setText(createOrder.getUuid());
                    tvTxBtcAddress.setText(createOrder.getBtcDestAddress());
                    hideProgress();
                }
            });
            queryOrder(createOrder.getUuid());
        } else {
            throw new IllegalStateException("Create Order is not TO_BE_CREATED");
        }
    }

    private void processCreateOrderError(final Exception ex) {
        Timber.e("processCreateOrderError %s", ex.getLocalizedMessage());
        getView().post(new Runnable() {
            @Override
            public void run() {
                if (ex instanceof XlaToException) {
                    XlaToException xlaEx = (XlaToException) ex;
                    XlaToError xlaErr = xlaEx.getError();
                    if (xlaErr != null) {
                        if (xlaErr.isRetryable()) {
                            showStageError(xlaErr.getErrorId().toString(), xlaErr.getErrorMsg(),
                                    getString(R.string.text_retry));
                            evStageA.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    evStageA.setOnClickListener(null);
                                    createOrder();
                                }
                            });
                        } else {
                            showStageError(xlaErr.getErrorId().toString(), xlaErr.getErrorMsg(),
                                    getString(R.string.text_noretry));
                        }
                    } else {
                        showStageError(getString(R.string.label_generic_xlato_error),
                                getString(R.string.text_generic_xlato_error, xlaEx.getCode()),
                                getString(R.string.text_noretry));
                    }
                } else {
                    evStageA.showMessage(getString(R.string.label_generic_xlato_error),
                            ex.getLocalizedMessage(),
                            getString(R.string.text_noretry));
                }
            }
        });
    }

    private void createOrder() {
        if (!isResumed) return;
        Timber.d("createOrder");
        xlatoOrder = null;
        xlatoStatus = null;
        showProgress(1, getString(R.string.label_send_progress_xlato_create));
        TxDataBtc txDataBtc = (TxDataBtc) sendListener.getTxData();

        XlaToCallback<CreateOrder> callback = new XlaToCallback<CreateOrder>() {
            @Override
            public void onSuccess(CreateOrder createOrder) {
                if (!isResumed) return;
                if (xlatoOrder != null) {
                    Timber.w("another ongoing create order request");
                    return;
                }
                processCreateOrder(createOrder);
            }

            @Override
            public void onError(Exception ex) {
                if (!isResumed) return;
                if (xlatoOrder != null) {
                    Timber.w("another ongoing create order request");
                    return;
                }
                processCreateOrderError(ex);
            }
        };

        if (txDataBtc.getBip70() != null) {
            getxlaToApi().createOrder(txDataBtc.getBip70(), callback);
        } else {
            getxlaToApi().createOrder(txDataBtc.getBtcAmount(), txDataBtc.getBtcAddress(), callback);
        }
    }

    private QueryOrderStatus xlatoStatus = null;

    private void processQueryOrder(final QueryOrderStatus status) {
        Timber.d("processQueryOrder %s for %s", status.getState().toString(), status.getUuid());
        xlatoStatus = status;
        if (status.isCreated()) {
            getView().post(new Runnable() {
                @Override
                public void run() {
                    NumberFormat df = NumberFormat.getInstance(Locale.US);
                    df.setMaximumFractionDigits(12);
                    String btcAmount = df.format(status.getBtcAmount());
                    String xlaAmountTotal = df.format(status.getIncomingAmountTotal());
                    tvTxBtcAmount.setText(getString(R.string.text_send_btc_amount, btcAmount, xlaAmountTotal));
                    String xlaPriceBtc = df.format(status.getIncomingPriceBtc());
                    tvTxBtcRate.setText(getString(R.string.text_send_btc_rate, xlaPriceBtc));

                    double calcRate = status.getBtcAmount() / status.getIncomingPriceBtc();
                    Timber.d("Rates: %f / %f", calcRate, status.getIncomingPriceBtc());

                    tvTxBtcAddress.setText(status.getBtcDestAddress()); // TODO test if this is different?

                    Timber.d("Expires @ %s, in %s seconds", status.getExpiresAt().toString(), status.getSecondsTillTimeout());

                    Timber.d("Status = %s", status.getState().toString());
                    tvTxxlaToKey.setText(status.getUuid());

                    Timber.d("AmountRemaining=%f, xlaAmountTotal=%f", status.getRemainingAmountIncoming(), status.getIncomingAmountTotal());
                    hideProgress();
                    startSendTimer();
                    prepareSend();
                }
            });
        } else {
            Timber.d("try again!");
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    queryOrder(status.getUuid());
                }
            }, QUERY_INTERVAL);
        }
    }

    Handler handler = new Handler();

    private void processQueryOrderError(final Exception ex) {
        Timber.e("processQueryOrderError %s", ex.getLocalizedMessage());
        getView().post(new Runnable() {
            @Override
            public void run() {
                if (ex instanceof XlaToException) {
                    XlaToException xlaEx = (XlaToException) ex;
                    XlaToError xlaErr = xlaEx.getError();
                    if (xlaErr != null) {
                        if (xlaErr.isRetryable()) {
                            showStageError(xlaErr.getErrorId().toString(), xlaErr.getErrorMsg(),
                                    getString(R.string.text_retry));
                            evStageB.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    evStageB.setOnClickListener(null);
                                    queryOrder(xlatoOrder.getUuid());
                                }
                            });
                        } else {
                            showStageError(xlaErr.getErrorId().toString(), xlaErr.getErrorMsg(),
                                    getString(R.string.text_noretry));
                        }
                    } else {
                        showStageError(getString(R.string.label_generic_xlato_error),
                                getString(R.string.text_generic_xlato_error, xlaEx.getCode()),
                                getString(R.string.text_noretry));
                    }
                } else {
                    evStageB.showMessage(getString(R.string.label_generic_xlato_error),
                            ex.getLocalizedMessage(),
                            getString(R.string.text_noretry));
                }
            }
        });
    }

    private void queryOrder(final String uuid) {
        Timber.d("queryOrder(%s)", uuid);
        if (!isResumed) return;
        getView().post(new Runnable() {
            @Override
            public void run() {
                xlatoStatus = null;
                showProgress(2, getString(R.string.label_send_progress_xlato_query));
                getxlaToApi().queryOrderStatus(uuid, new XlaToCallback<QueryOrderStatus>() {
                    @Override
                    public void onSuccess(QueryOrderStatus status) {
                        if (!isResumed) return;
                        if (xlatoOrder == null) return;
                        if (!status.getUuid().equals(xlatoOrder.getUuid())) {
                            Timber.d("Query UUID does not match");
                            // ignore (we got a response to a stale request)
                            return;
                        }
                        if (xlatoStatus != null)
                            throw new IllegalStateException("xlatoStatus must be null here!");
                        processQueryOrder(status);
                    }

                    @Override
                    public void onError(Exception ex) {
                        if (!isResumed) return;
                        processQueryOrderError(ex);
                    }
                });
            }
        });
    }

    private XlaToApi xlaToApi = null;

    private XlaToApi getxlaToApi() {
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
