/*
 * Copyright (c) 2017 m2049r et al.
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
import io.scalaproject.vault.service.exchange.api.ExchangeException;
import io.scalaproject.vault.service.exchange.api.ExchangeRate;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ExchangeRateImpl implements ExchangeRate {

    private final String baseCurrency;
    private final String quoteCurrency;
    private final double rate;

    @Override
    public String getServiceName() {
        return "prices.scala.network";
    }

    @Override
    public String getBaseCurrency() {
        return baseCurrency;
    }

    @Override
    public String getQuoteCurrency() {
        return quoteCurrency;
    }

    @Override
    public double getRate() {
        return rate;
    }

    ExchangeRateImpl(@NonNull final String baseCurrency, @NonNull final String quoteCurrency, double rate) {
        super();
        this.baseCurrency = baseCurrency;
        this.quoteCurrency = quoteCurrency;
        this.rate = rate;
    }

    ExchangeRateImpl(final JSONObject jsonObject, final boolean swapAssets) throws JSONException, ExchangeException {
        super();
        Log.w("MainExchangeRateImpl", "jsonObject=" + jsonObject);
        try {
            // Current API provides BTC, LTC, USD, EUR
            // Uppercase provides general info, lowercase provides price
            final String key = "USD"; //jsonObject.keys().next(); // we expect only one
            Log.w("MainExchangeRateImpl", "key=" + key);
            baseCurrency = "XLA";
            quoteCurrency = "USD";

/*
            Pattern pattern = Pattern.compile("^X(.*?)Z(.*?)$");
            Matcher matcher = pattern.matcher(key);
            if (matcher.find()) {
                Log.w("MainExchangeRateImpl", "matcher.find()");
                baseCurrency = swapAssets ? matcher.group(2) : matcher.group(1);
                quoteCurrency = swapAssets ? matcher.group(1) : matcher.group(2);
            } else {
                Log.w("MainExchangeRateImpl", "no match");
                throw new ExchangeException("no pair returned!");
            }
*/
            JSONObject pair = jsonObject.getJSONObject("usd");
            String priceKey = "price";
            if (pair.has("c")) {
                JSONArray close = pair.getJSONArray("c");
                priceKey = close.getString(0);
            } else if (pair.has(priceKey)) {
                priceKey = pair.getString(priceKey);
            }

            if (priceKey != null) {
                Log.w("MainExchangeRateImpl", "priceKey=" + priceKey + " swapAssets=" + swapAssets);
                try {
                    double rate = Double.parseDouble(priceKey);
                    this.rate = swapAssets ? (1 / rate) : rate;
                } catch (NumberFormatException ex) {
                    Log.w("MainExchangeRateImpl", "NumberFormatException");
                    throw new ExchangeException(ex.getLocalizedMessage());
                }
            } else {
                Log.w("MainExchangeRateImpl", "no price returned!");
                throw new ExchangeException("no price returned!");
            }
        } catch (NoSuchElementException ex) {
            Log.w("MainExchangeRateImpl", "NoSuchElementException");
            throw new ExchangeException(ex.getLocalizedMessage());
        }
    }
}