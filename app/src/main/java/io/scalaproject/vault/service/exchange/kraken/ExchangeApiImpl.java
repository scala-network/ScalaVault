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

package io.scalaproject.vault.service.exchange.kraken;

import androidx.annotation.NonNull;

import io.scalaproject.vault.service.exchange.api.ExchangeApi;
import io.scalaproject.vault.service.exchange.api.ExchangeCallback;

import java.util.Objects;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

public class ExchangeApiImpl implements ExchangeApi {

    @NonNull
    private final OkHttpClient okHttpClient;
    private final HttpUrl baseUrl;

    //so we can inject the mockserver url
    public ExchangeApiImpl(@NonNull final OkHttpClient okHttpClient, final HttpUrl baseUrl) {
        this.okHttpClient = okHttpClient;
        this.baseUrl = baseUrl;
    }

    public ExchangeApiImpl(@NonNull final OkHttpClient okHttpClient) {
        this(okHttpClient, Objects.requireNonNull(HttpUrl.parse("https://api.kraken.com/0/public/Assets")));
    }

    @Override
    public void queryExchangeRate(@NonNull final String baseCurrency, @NonNull final String quoteCurrency, @NonNull final ExchangeCallback callback) {

        if (baseCurrency.equals(quoteCurrency)) {
            callback.onSuccess(new ExchangeRateImpl(baseCurrency, quoteCurrency, 1.0));
            return;
        }
    }
}