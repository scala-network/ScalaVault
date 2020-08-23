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
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import io.scalaproject.vault.R;
import io.scalaproject.vault.data.BarcodeData;
import io.scalaproject.vault.data.TxDataBtc;
import io.scalaproject.vault.model.Wallet;
import io.scalaproject.vault.util.Helper;
import io.scalaproject.vault.util.OkHttpHelper;
import io.scalaproject.vault.widget.ExchangeEditText;
import io.scalaproject.vault.widget.ExchangeOtherEditText;
import io.scalaproject.vault.widget.SendProgressView;
import io.scalaproject.vault.xlato.XlaToError;
import io.scalaproject.vault.xlato.XlaToException;
import io.scalaproject.vault.xlato.api.QueryOrderParameters;
import io.scalaproject.vault.xlato.api.XlaToApi;
import io.scalaproject.vault.xlato.api.XlaToCallback;
import io.scalaproject.vault.xlato.network.XlaToApiImpl;

import java.text.NumberFormat;
import java.util.Locale;

import timber.log.Timber;

public class SendBtcAmountWizardFragment extends SendWizardFragment {

    public static SendBtcAmountWizardFragment newInstance(SendAmountWizardFragment.Listener listener) {
        SendBtcAmountWizardFragment instance = new SendBtcAmountWizardFragment();
        instance.setSendListener(listener);
        return instance;
    }

    SendAmountWizardFragment.Listener sendListener;

    public SendBtcAmountWizardFragment setSendListener(SendAmountWizardFragment.Listener listener) {
        this.sendListener = listener;
        return this;
    }

    private TextView tvFunds;
    private ExchangeOtherEditText etAmount;

    private TextView tvxlaToParms;
    private SendProgressView evParams;
    private View llxlaToParms;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        Timber.d("onCreateView() %s", (String.valueOf(savedInstanceState)));

        sendListener = (SendAmountWizardFragment.Listener) getParentFragment();

        View view = inflater.inflate(R.layout.fragment_send_btc_amount, container, false);

        tvFunds = view.findViewById(R.id.tvFunds);

        evParams = view.findViewById(R.id.evxlaToParms);
        llxlaToParms = view.findViewById(R.id.llxlaToParms);

        tvxlaToParms = view.findViewById(R.id.tvxlaToParms);

        etAmount = view.findViewById(R.id.etAmount);
        etAmount.requestFocus();
        return view;
    }


    @Override
    public boolean onValidateFields() {
        if (!etAmount.validate(maxBtc, minBtc)) {
            return false;
        }
        if (sendListener != null) {
            TxDataBtc txDataBtc = (TxDataBtc) sendListener.getTxData();
            String btcString = etAmount.getNativeAmount();
            if (btcString != null) {
                try {
                    double btc = Double.parseDouble(btcString);
                    Timber.d("setAmount %f", btc);
                    txDataBtc.setBtcAmount(btc);
                } catch (NumberFormatException ex) {
                    Timber.d(ex.getLocalizedMessage());
                    txDataBtc.setBtcAmount(0);
                }
            } else {
                txDataBtc.setBtcAmount(0);
            }
        }
        return true;
    }

    private void setBip70Mode() {
        TxDataBtc txDataBtc = (TxDataBtc) sendListener.getTxData();
        if (txDataBtc.getBip70() == null) {
            etAmount.setEditable(true);
            Helper.showKeyboard(getActivity());
        } else {
            etAmount.setEditable(false);
            Helper.hideKeyboard(getActivity());
        }
    }

    double maxBtc = 0;
    double minBtc = 0;

    @Override
    public void onPauseFragment() {
        llxlaToParms.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onResumeFragment() {
        super.onResumeFragment();
        Timber.d("onResumeFragment()");
        final long funds = getTotalFunds();
        if (!sendListener.getActivityCallback().isStealthMode()) {
            tvFunds.setText(getString(R.string.send_available,
                    Wallet.getDisplayAmount(funds)));
        } else {
            tvFunds.setText(getString(R.string.send_available,
                    getString(R.string.unknown_amount)));
        }
        final BarcodeData data = sendListener.popBarcodeData();
        if (data != null) {
            if (data.amount != null) {
                etAmount.setAmount(data.amount);
            }
        }
        setBip70Mode();
        callxlaTo();
    }

    long getTotalFunds() {
        return sendListener.getActivityCallback().getTotalFunds();
    }

    private QueryOrderParameters orderParameters = null;

    private void processOrderParms(final QueryOrderParameters orderParameters) {
        this.orderParameters = orderParameters;
        getView().post(new Runnable() {
            @Override
            public void run() {
                etAmount.setExchangeRate(1.0d / orderParameters.getPrice());
                NumberFormat df = NumberFormat.getInstance(Locale.US);
                df.setMaximumFractionDigits(6);
                String min = df.format(orderParameters.getLowerLimit());
                String max = df.format(orderParameters.getUpperLimit());
                String rate = df.format(orderParameters.getPrice());
                Spanned xlaParmText = Html.fromHtml(getString(R.string.info_send_xlato_parms, min, max, rate));
                if (orderParameters.isZeroConfEnabled()) {
                    String zeroConf = df.format(orderParameters.getZeroConfMaxAmount());
                    Spanned zeroConfText = Html.fromHtml(getString(R.string.info_send_xlato_zeroconf, zeroConf));
                    xlaParmText = (Spanned) TextUtils.concat(xlaParmText, " ", zeroConfText);
                }
                tvxlaToParms.setText(xlaParmText);
                maxBtc = orderParameters.getUpperLimit();
                minBtc = orderParameters.getLowerLimit();
                Timber.d("minBtc=%f / maxBtc=%f", minBtc, maxBtc);

                final long funds = getTotalFunds();
                double availablexla = 1.0 * funds / 1000000000000L;
                maxBtc = Math.min(maxBtc, availablexla * orderParameters.getPrice());

                String availBtcString;
                String availxlaString;
                if (!sendListener.getActivityCallback().isStealthMode()) {
                    availBtcString = df.format(availablexla * orderParameters.getPrice());
                    availxlaString = df.format(availablexla);
                } else {
                    availBtcString = getString(R.string.unknown_amount);
                    availxlaString = availBtcString;
                }
                tvFunds.setText(getString(R.string.send_available_btc,
                        availxlaString,
                        availBtcString));
                llxlaToParms.setVisibility(View.VISIBLE);
                evParams.hideProgress();
            }
        });
    }

    private void processOrderParmsError(final Exception ex) {
        etAmount.setExchangeRate(0);
        orderParameters = null;
        maxBtc = 0;
        minBtc = 0;
        Timber.e(ex);
        getView().post(new Runnable() {
            @Override
            public void run() {
                if (ex instanceof XlaToException) {
                    XlaToException xlaEx = (XlaToException) ex;
                    XlaToError xlaErr = xlaEx.getError();
                    if (xlaErr != null) {
                        if (xlaErr.isRetryable()) {
                            evParams.showMessage(xlaErr.getErrorId().toString(), xlaErr.getErrorMsg(),
                                    getString(R.string.text_retry));
                            evParams.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    evParams.setOnClickListener(null);
                                    callxlaTo();
                                }
                            });
                        } else {
                            evParams.showMessage(xlaErr.getErrorId().toString(), xlaErr.getErrorMsg(),
                                    getString(R.string.text_noretry));
                        }
                    } else {
                        evParams.showMessage(getString(R.string.label_generic_xlato_error),
                                getString(R.string.text_generic_xlato_error, xlaEx.getCode()),
                                getString(R.string.text_noretry));
                    }
                } else {
                    evParams.showMessage(getString(R.string.label_generic_xlato_error),
                            ex.getLocalizedMessage(),
                            getString(R.string.text_noretry));
                }
            }
        });
    }

    private void callxlaTo() {
        evParams.showProgress(getString(R.string.label_send_progress_queryparms));
        getxlaToApi().queryOrderParameters(new XlaToCallback<QueryOrderParameters>() {
            @Override
            public void onSuccess(final QueryOrderParameters orderParameters) {
                processOrderParms(orderParameters);
            }

            @Override
            public void onError(final Exception e) {
                processOrderParmsError(e);
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