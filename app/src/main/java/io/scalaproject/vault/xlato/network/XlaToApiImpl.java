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
 */

package io.scalaproject.vault.xlato.network;

import androidx.annotation.NonNull;

import io.scalaproject.vault.util.OkHttpHelper;
import io.scalaproject.vault.xlato.XlaToError;
import io.scalaproject.vault.xlato.XlaToException;
import io.scalaproject.vault.xlato.api.CreateOrder;
import io.scalaproject.vault.xlato.api.QueryOrderParameters;
import io.scalaproject.vault.xlato.api.QueryOrderStatus;
import io.scalaproject.vault.xlato.api.XlaToApi;
import io.scalaproject.vault.xlato.api.XlaToCallback;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import timber.log.Timber;

public class XlaToApiImpl implements XlaToApi, XlaToApiCall {

    @NonNull
    private final OkHttpClient okHttpClient;

    private final HttpUrl baseUrl;

    public XlaToApiImpl(@NonNull final OkHttpClient okHttpClient, final HttpUrl baseUrl) {
        this.okHttpClient = okHttpClient;
        this.baseUrl = baseUrl;
    }

    @Override
    public void createOrder(final double amount, @NonNull final String address,
                            @NonNull final XlaToCallback<CreateOrder> callback) {
        CreateOrderImpl.call(this, amount, address, callback);
    }

    @Override
    public void createOrder(@NonNull final String url,
                            @NonNull final XlaToCallback<CreateOrder> callback) {
        CreateOrderImpl.call(this, url, callback);
    }

    @Override
    public void queryOrderStatus(@NonNull final String uuid,
                                 @NonNull final XlaToCallback<QueryOrderStatus> callback) {
        QueryOrderStatusImpl.call(this, uuid, callback);
    }

    @Override
    public void queryOrderParameters(@NonNull final XlaToCallback<QueryOrderParameters> callback) {
        QueryOrderParametersImpl.call(this, callback);
    }


    @Override
    public void call(@NonNull final String path, @NonNull final NetworkCallback callback) {
        call(path, null, callback);
    }

    @Override
    public void call(@NonNull final String path, final JSONObject request, @NonNull final NetworkCallback callback) {
        final HttpUrl url = baseUrl.newBuilder()
                .addPathSegment(path)
                .addPathSegment("") // xmr.to needs a trailing slash!
                .build();

        Timber.d(url.toString());
        final Request httpRequest = createHttpRequest(request, url);
        Timber.d(httpRequest.toString());
        Timber.d(request == null ? "null request" : request.toString());

        okHttpClient.newCall(httpRequest).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(final Call call, final IOException ex) {
                Timber.d("A");
                callback.onError(ex);
            }

            @Override
            public void onResponse(final Call call, final Response response) throws IOException {
                Timber.d("onResponse code=%d", response.code());
                if (response.isSuccessful()) {
                    try {
                        final JSONObject json = new JSONObject(response.body().string());
                        callback.onSuccess(json);
                    } catch (JSONException ex) {
                        callback.onError(ex);
                    }
                } else {
                    try {
                        final JSONObject json = new JSONObject(response.body().string());
                        Timber.d(json.toString(2));
                        final XlaToError error = new XlaToError(json);
                        Timber.e("xmr.to says %d/%s", response.code(), error.toString());
                        callback.onError(new XlaToException(response.code(), error));
                    } catch (JSONException ex) {
                        callback.onError(new XlaToException(response.code()));
                    }
                }
            }
        });
    }

    private Request createHttpRequest(final JSONObject request, final HttpUrl url) {
        if (request != null) {
            final RequestBody body = RequestBody.create(
                    MediaType.parse("application/json"), request.toString());
            return OkHttpHelper.getPostRequest(url, body);
        } else {
            return OkHttpHelper.getGetRequest(url);
        }
    }
}
