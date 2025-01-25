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

// based on https://code.tutsplus.com/tutorials/creating-compound-views-on-android--cms-22889

package io.scalaproject.vault.widget;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.google.android.material.textfield.TextInputLayout;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import io.scalaproject.vault.R;
import io.scalaproject.vault.model.Wallet;
import io.scalaproject.vault.service.exchange.api.ExchangeApi;
import io.scalaproject.vault.service.exchange.api.ExchangeCallback;
import io.scalaproject.vault.service.exchange.api.ExchangeRate;
import io.scalaproject.vault.util.Helper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import timber.log.Timber;

public class ExchangeView extends LinearLayout {
    String xlaAmount = null;
    String notxlaAmount = null;

    public void enable(boolean enable) {
        etAmount.setEnabled(enable);
        sCurrencyA.setEnabled(enable);
        sCurrencyB.setEnabled(enable);
    }

    void setxla(String xla) {
        xlaAmount = xla;
        if (onNewAmountListener != null) {
            onNewAmountListener.onNewAmount(xla);
        }
    }

    public void setAmount(String xlaAmount) {
        if (xlaAmount != null) {
            setCurrencyA(0);
            Objects.requireNonNull(etAmount.getEditText()).setText(xlaAmount);
            setxla(xlaAmount);
            this.notxlaAmount = null;
            doExchange();
        } else {
            setxla(null);
            this.notxlaAmount = null;
            tvAmountB.setText("--");
        }
    }

    public String getAmount() {
        return xlaAmount;
    }

    public void setError(String msg) {
        etAmount.setError(msg);
    }

    TextInputLayout etAmount;
    TextView tvAmountB;
    Spinner sCurrencyA;
    Spinner sCurrencyB;
    ImageView evExchange;
    ProgressBar pbExchange;


    public void setCurrencyA(int currency) {
        if ((currency != 0) && (getCurrencyB() != 0)) {
            setCurrencyB(0);
        }
        sCurrencyA.setSelection(currency, true);
        doExchange();
    }

    public void setCurrencyB(int currency) {
        if ((currency != 0) && (getCurrencyA() != 0)) {
            setCurrencyA(0);
        }
        sCurrencyB.setSelection(currency, true);
        doExchange();
    }

    public int getCurrencyA() {
        return sCurrencyA.getSelectedItemPosition();
    }

    public int getCurrencyB() {
        return sCurrencyB.getSelectedItemPosition();
    }

    public ExchangeView(Context context) {
        super(context);
        initializeViews(context);
    }

    public ExchangeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initializeViews(context);
    }

    public ExchangeView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initializeViews(context);
    }

    /**
     * Inflates the views in the layout.
     *
     * @param context the current context for the view.
     */
    private void initializeViews(Context context) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.view_exchange, this);
    }

    void setCurrencyAdapter(Spinner spinner) {
        List<String> currencies = new ArrayList<>();
        currencies.add(Helper.BASE_CRYPTO);
        currencies.addAll(Arrays.asList(getResources().getStringArray(R.array.currency)));
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, currencies);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        etAmount = findViewById(R.id.etAmount);
        tvAmountB = findViewById(R.id.tvAmountB);
        sCurrencyA = findViewById(R.id.sCurrencyA);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getContext(), R.array.currency, R.layout.item_spinner);
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown_item);
        sCurrencyA.setAdapter(adapter);
        sCurrencyB = findViewById(R.id.sCurrencyB);
        sCurrencyB.setAdapter(adapter);
        evExchange = findViewById(R.id.evExchange);
        pbExchange = findViewById(R.id.pbExchange);

        setCurrencyAdapter(sCurrencyA);
        setCurrencyAdapter(sCurrencyB);

        // make progress circle gray
        pbExchange.getIndeterminateDrawable().setColorFilter(getResources().getColor(R.color.trafficGray), android.graphics.PorterDuff.Mode.MULTIPLY);
        sCurrencyA.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (position != 0) { // if not XLA, select XLA on other
                    sCurrencyB.setSelection(0, true);
                }
                doExchange();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // nothing (yet?)
                Timber.tag("ExchangeView").d("onNothingSelected");
            }
        });

        sCurrencyB.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(final AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (position != 0) { // if not XLA, select XLA on other
                    sCurrencyA.setSelection(0, true);
                }
                parentView.post(() -> ((TextView) parentView.getChildAt(0)).setTextColor(getResources().getColor(R.color.scalaGray)));
                doExchange();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // nothing
                Timber.tag("ExchangeView").d("onNothingSelected");
            }
        });

        Objects.requireNonNull(etAmount.getEditText()).setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                doExchange();
            }
        });

        etAmount.getEditText().setOnEditorActionListener((v, actionId, event) -> {
            if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) && (event.getAction() == KeyEvent.ACTION_DOWN))
                    || (actionId == EditorInfo.IME_ACTION_DONE)) {
                doExchange();
                return true;
            }
            return false;
        });


        etAmount.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                etAmount.setError(null);
                clearAmounts();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                Timber.tag("ExchangeView").d("beforeTextChanged");
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Timber.tag("ExchangeView").d("onTextChanged");
            }
        });
    }

    final static double MAX_AMOUNT_XLA = 10000000;
    final static double MAX_AMOUNT_NOTXLA = 100000000;

    public boolean checkEnteredAmount() {
        boolean ok = true;
        Timber.d("checkEnteredAmount");
        String amountEntry = Objects.requireNonNull(etAmount.getEditText()).getText().toString();
        if (!amountEntry.isEmpty()) {
            try {
                double a = Double.parseDouble(amountEntry);
                double maxAmount = (getCurrencyA() == 0) ? MAX_AMOUNT_XLA : MAX_AMOUNT_NOTXLA;
                if (a > (maxAmount)) {
                    etAmount.setError(getResources().
                            getString(R.string.receive_amount_too_big,
                                    String.format(Locale.US, "%,.0f", maxAmount)));
                    ok = false;
                } else if (a < 0) {
                    etAmount.setError(getResources().getString(R.string.receive_amount_negative));
                    ok = false;
                }
            } catch (NumberFormatException ex) {
                etAmount.setError(getResources().getString(R.string.receive_amount_nan));
                ok = false;
            }
        }
        if (ok) {
            etAmount.setError(null);
        }
        return ok;
    }

    public void doExchange() {
        tvAmountB.setText("--");
        // use cached exchange rate if we have it
        if (!isExchangeInProgress()) {
            String enteredCurrencyA = (String) sCurrencyA.getSelectedItem();
            String enteredCurrencyB = (String) sCurrencyB.getSelectedItem();
            if ((enteredCurrencyA + enteredCurrencyB).equals(assetPair)) {
                if (prepareExchange()) {
                    exchange(assetRate);
                } else {
                    clearAmounts();
                }
            } else {
                clearAmounts();
                startExchange();
            }
        } else {
            clearAmounts();
        }
    }

    private void clearAmounts() {
        if ((xlaAmount != null) || (notxlaAmount != null)) {
            tvAmountB.setText("--");
            setxla(null);
            notxlaAmount = null;
        }
    }

    private final ExchangeApi exchangeApi = Helper.getExchangeApi();

    void startExchange() {
        showProgress();
        String currencyA = (String) sCurrencyA.getSelectedItem();
        String currencyB = (String) sCurrencyB.getSelectedItem();

        exchangeApi.queryExchangeRate(currencyA, currencyB, new ExchangeCallback() {
            @Override
            public void onSuccess(final ExchangeRate exchangeRate) {
                if (isAttachedToWindow())
                    new Handler(Looper.getMainLooper()).post(() -> exchange(exchangeRate));
            }

            @Override
            public void onError(final Exception e) {
                Timber.e(e.getLocalizedMessage());
                new Handler(Looper.getMainLooper()).post(() -> exchangeFailed());
            }
        });
    }

    public void exchange(double rate) {
        if (getCurrencyA() == 0) {
            if (xlaAmount == null) return;
            if (!xlaAmount.isEmpty() && (rate > 0)) {
                double amountB = rate * Double.parseDouble(xlaAmount);
                notxlaAmount = Helper.getFormattedAmount(amountB, getCurrencyB() == 0);
            } else {
                notxlaAmount = "";
            }
            tvAmountB.setText(notxlaAmount);
        } else if (getCurrencyB() == 0) {
            if (notxlaAmount == null) return;
            if (!notxlaAmount.isEmpty() && (rate > 0)) {
                double amountB = rate * Double.parseDouble(notxlaAmount);
                setxla(Helper.getFormattedAmount(amountB, true));
            } else {
                setxla("");
            }
            tvAmountB.setText(xlaAmount);
        } else { // no XLA currency - cannot happen!
            Timber.e("No XLA currency!");
            setxla(null);
            notxlaAmount = null;
            return;
        }
    }

    boolean prepareExchange() {
        Timber.d("prepareExchange()");
        if (checkEnteredAmount()) {
            String enteredAmount = Objects.requireNonNull(etAmount.getEditText()).getText().toString();
            if (!enteredAmount.isEmpty()) {
                String cleanAmount = "";
                if (getCurrencyA() == 0) {
                    // sanitize the input
                    cleanAmount = Helper.getDisplayAmount(Wallet.getAmountFromString(enteredAmount));
                    setxla(cleanAmount);
                    notxlaAmount = null;
                    Timber.d("cleanAmount = %s", cleanAmount);
                } else if (getCurrencyB() == 0) { // we use B & 0 here for the else below ...
                    // sanitize the input
                    double amountA = Double.parseDouble(enteredAmount);
                    cleanAmount = String.format(Locale.US, "%.2f", amountA);
                    setxla(null);
                    notxlaAmount = cleanAmount;
                } else { // no XLA currency - cannot happen!
                    Timber.e("No XLA currency!");
                    setxla(null);
                    notxlaAmount = null;
                    return false;
                }
                Timber.d("prepareExchange() %s", cleanAmount);
            } else {
                setxla("");
                notxlaAmount = "";
            }
            return true;
        } else {
            setxla(null);
            notxlaAmount = null;
            return false;
        }
    }

    public void exchangeFailed() {
        hideProgress();
        exchange(0);
        if (onFailedExchangeListener != null) {
            onFailedExchangeListener.onFailedExchange();
            Timber.tag("ExchangeView").d("onFailedExchange");
        }
    }

    String assetPair = null;
    double assetRate = 0;

    public void exchange(ExchangeRate exchangeRate) {
        hideProgress();
        // first, make sure this is what we want
        String enteredCurrencyA = (String) sCurrencyA.getSelectedItem();
        String enteredCurrencyB = (String) sCurrencyB.getSelectedItem();
        if (!exchangeRate.getBaseCurrency().equals(enteredCurrencyA)
                || !exchangeRate.getQuoteCurrency().equals(enteredCurrencyB)) {
            // something's wrong
            Timber.e("Currencies don't match!");
            return;
        }
        assetPair = enteredCurrencyA + enteredCurrencyB;
        assetRate = exchangeRate.getRate();
        if (prepareExchange()) {
            exchange(exchangeRate.getRate());
        }
    }

    private void showProgress() {
        pbExchange.setVisibility(View.VISIBLE);
    }

    private boolean isExchangeInProgress() {
        return pbExchange.getVisibility() == View.VISIBLE;
    }

    private void hideProgress() {
        pbExchange.setVisibility(View.INVISIBLE);
    }

    // Hooks
    public interface OnNewAmountListener {
        void onNewAmount(String xla);
    }

    OnNewAmountListener onNewAmountListener;

    public void setOnNewAmountListener(OnNewAmountListener listener) {
        onNewAmountListener = listener;
    }

    public interface OnAmountInvalidatedListener {
        void onAmountInvalidated();
    }

    OnAmountInvalidatedListener onAmountInvalidatedListener;

    public void setOnAmountInvalidatedListener(OnAmountInvalidatedListener listener) {
        onAmountInvalidatedListener = listener;
    }

    public interface OnFailedExchangeListener {
        void onFailedExchange();
    }

    OnFailedExchangeListener onFailedExchangeListener;

    public void setOnFailedExchangeListener(OnFailedExchangeListener listener) {
        onFailedExchangeListener = listener;
    }
}
