/*
 * Copyright (c) 2017-2019 m2049r et al.
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

package io.scalaproject.vault.service.exchange.main;

import android.util.Log;
import androidx.annotation.NonNull;

import io.scalaproject.vault.service.exchange.api.ExchangeApi;
import io.scalaproject.vault.service.exchange.api.ExchangeCallback;
import io.scalaproject.vault.service.exchange.api.ExchangeException;
import io.scalaproject.vault.service.exchange.api.ExchangeRate;
import io.scalaproject.vault.util.Helper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import timber.log.Timber;

public class ExchangeApiImpl implements ExchangeApi {
    static public final String BASE_FIAT = "EUR";
    static public final String BASE_CRYPTO = "XLA";
    static public double rate = 1.0;
    static public String baseCurrency = Helper.BASE_CRYPTO;
    static public String quoteCurrency = "USD";

    @NonNull
    private final OkHttpClient okHttpClient;
    private final HttpUrl baseUrl;

    //so we can inject the mockserver url
    public ExchangeApiImpl(@NonNull final OkHttpClient okHttpClient, final HttpUrl baseUrl) {
        this.okHttpClient = okHttpClient;
        this.baseUrl = baseUrl;
    }

    // Scala Network API for prices no need any extra parameter in the url allways return the same value
    public ExchangeApiImpl(@NonNull final OkHttpClient okHttpClient) {
        this(okHttpClient, Objects.requireNonNull(HttpUrl.parse("https://prices.scala.network/")));
    }

    @Override
    public void queryExchangeRate(@NonNull final String baseCurrency, @NonNull final String quoteCurrency, @NonNull final ExchangeCallback callback) {
        Timber.tag("MainExchangeApiImpl").w("B=" + baseCurrency + " Q=" + quoteCurrency);
        ExchangeApiImpl.baseCurrency = baseCurrency;
        ExchangeApiImpl.quoteCurrency = quoteCurrency;

        if (baseCurrency.equals(quoteCurrency)) {
            Timber.d("BASE=QUOTE=1");
            callback.onSuccess(new ExchangeRateImpl(baseCurrency, quoteCurrency, rate));
            return;
        }

        boolean invertQuery;

        if (Helper.BASE_CRYPTO.equals(baseCurrency)) { invertQuery = false; }
        else if (Helper.BASE_CRYPTO.equals(quoteCurrency)) { invertQuery = true; }
        else { callback.onError(new IllegalArgumentException("no crypto specified")); return; }

        Timber.d("queryExchangeRate: i %b, b %s, q %s", invertQuery, baseCurrency, quoteCurrency);

        // Add extra query parameters
        final HttpUrl url = baseUrl.newBuilder().addQueryParameter("pair", baseCurrency + (quoteCurrency.equals("BTC") ? "XBT" : quoteCurrency)).build();
        final Request httpRequest = createHttpRequest(url);

        // Try to get XLA API for prices in format json
        okHttpClient.newCall(httpRequest).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull final Call call, @NonNull final IOException ex) {
                // On failure we can call another api to convert currency price like a backup site1, site2, site3...
               callback.onError(ex);
            }

            @Override
            public void onResponse(@NonNull final Call call, @NonNull final Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        assert response.body() != null;
                        final JSONObject json = new JSONObject(response.body().string());
                        Timber.tag("MainExchangeApiImpl").w("onResponse get body is successful");
                        if (!isValidJson(String.valueOf(json))) {
                                callback.onError(new ExchangeException(response.code(), "Error parsing JSON"));
                            } else {
                                reportSuccess(json, invertQuery, callback);
                            }
                        } catch (JSONException ex) {
                        callback.onError(new ExchangeException(ex.getLocalizedMessage()));
                    }
                } else {
                    callback.onError(new ExchangeException(response.code(), response.message()));
                }
            }
        });
    }

    void reportSuccess(JSONObject jsonObject, boolean swapAssets, ExchangeCallback callback) {
        try {
            final ExchangeRate exchangeRate = new ExchangeRateImpl(jsonObject, swapAssets);
            callback.onSuccess(exchangeRate);

            // This need clean and optimize --Vv - O.o
            final String quote = Helper.BASE_CRYPTO.equals(baseCurrency) ? quoteCurrency : baseCurrency;
            // now we get from ecb the selected coin
            final ExchangeApi ecbApi = new io.scalaproject.vault.service.exchange.ecb.ExchangeApiImpl(okHttpClient);
            ecbApi.queryExchangeRate(BASE_FIAT, quote, new ExchangeCallback() {
                @Override
                public void onSuccess(final ExchangeRate ecbRate) {
                    Timber.d("ECB = %f", ecbRate.getRate());
                    Log.w("MainExchangeApiImpl", "ECB = " + ecbRate.getRate());
                    double rate = ecbRate.getRate() * exchangeRate.getRate();
                    Log.w("MainExchangeApiImpl", "exchangerate = " + exchangeRate.getRate());
                    Log.w("MainExchangeApiImpl", "rateint = " + rate);
                    if (!quote.equals(quoteCurrency)) rate = 1.0d / rate;
                    Timber.d("rate = %f", rate);
                    Log.w("MainExchangeApiImpl", "rate = " + rate);

                    final ExchangeRate exchangeRate = new io.scalaproject.vault.service.exchange.main.ExchangeRateImpl(baseCurrency, quoteCurrency, rate);
                    callback.onSuccess(exchangeRate);
                }

                @Override
                public void onError(Exception ex) {
                    Timber.d(ex);
                    callback.onError(ex);
                }
            });

            Timber.tag("MainExchangeApiImpl").w("Currency price updated");
        } catch (JSONException ex) {
            callback.onError(new ExchangeException(ex.getLocalizedMessage()));
        } catch (ExchangeException ex) {
            callback.onError(ex);
        }
    }

    private Request createHttpRequest(final HttpUrl url) {
        return new Request.Builder().url(url).get().build();
    }

    // Basic validation of JSON
    public boolean isValidJson(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return false;
        }
        try {
            new JSONObject(jsonString);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

}