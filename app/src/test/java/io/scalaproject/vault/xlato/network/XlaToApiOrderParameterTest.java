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

package io.scalaproject.vault.xlato.network;

import io.scalaproject.vault.xlato.api.XlaToCallback;
import io.scalaproject.vault.xlato.XlaToError;
import io.scalaproject.vault.xlato.XlaToException;
import io.scalaproject.vault.xlato.api.QueryOrderParameters;
import io.scalaproject.vault.xlato.api.XlaToApi;

import net.jodah.concurrentunit.Waiter;

import org.json.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.TimeoutException;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.Assert.assertEquals;

public class XlaToApiOrderParameterTest {

    private MockWebServer mockWebServer;

    private XlaToApi xlaToApi;

    private OkHttpClient okHttpClient = new OkHttpClient();
    private Waiter waiter;

    @Mock
    XlaToCallback<QueryOrderParameters> mockParametersxlaToCallback;

    @Before
    public void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        waiter = new Waiter();

        MockitoAnnotations.initMocks(this);

        xlaToApi = new XlaToApiImpl(okHttpClient, mockWebServer.url("/"));
    }

    @After
    public void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    public void orderParameter_shouldBeGetMethod()
            throws InterruptedException {

        xlaToApi.queryOrderParameters(mockParametersxlaToCallback);

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("GET", request.getMethod());
    }

    @Test
    public void orderParameter_wasSuccessfulShouldRespondWithParameters()
            throws TimeoutException, InterruptedException {
        final boolean isZeroConfEnabled = true;
        final double price = 0.015537;
        final double upperLimit = 20.0;
        final double lowerLimit = 0.001;
        final double zeroConfMaxAmount = 0.1;

        MockResponse jsonMockResponse = new MockResponse().setBody(
                createMockOrderParameterResponse(isZeroConfEnabled, price, upperLimit, lowerLimit, zeroConfMaxAmount));
        mockWebServer.enqueue(jsonMockResponse);

        xlaToApi.queryOrderParameters(new XlaToCallback<QueryOrderParameters>() {
            @Override
            public void onSuccess(final QueryOrderParameters orderParameter) {
                waiter.assertEquals(orderParameter.getLowerLimit(), lowerLimit);
                waiter.assertEquals(orderParameter.getUpperLimit(), upperLimit);
                waiter.assertEquals(orderParameter.getPrice(), price);
                waiter.assertEquals(orderParameter.getZeroConfMaxAmount(), zeroConfMaxAmount);
                waiter.assertEquals(orderParameter.isZeroConfEnabled(), isZeroConfEnabled);
                waiter.resume();
            }

            @Override
            public void onError(final Exception e) {
                waiter.fail(e);
                waiter.resume();
            }
        });
        waiter.await();
    }

    @Test
    public void orderParameter_wasNotSuccessfulShouldCallOnError()
            throws TimeoutException, InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        xlaToApi.queryOrderParameters(new XlaToCallback<QueryOrderParameters>() {
            @Override
            public void onSuccess(final QueryOrderParameters orderParameter) {
                waiter.fail();
                waiter.resume();
            }

            @Override
            public void onError(final Exception e) {
                waiter.assertTrue(e instanceof XlaToException);
                waiter.assertTrue(((XlaToException) e).getCode() == 500);
                waiter.resume();
            }

        });
        waiter.await();
    }

    @Test
    public void orderParameter_thirdPartyServiceNotAvailableShouldCallOnError()
            throws TimeoutException, InterruptedException {
        mockWebServer.enqueue(new MockResponse().
                setResponseCode(503).
                setBody("{\"error_msg\":\"third party service not available\",\"error\":\"XLATO-ERROR-007\"}"));
        xlaToApi.queryOrderParameters(new XlaToCallback<QueryOrderParameters>() {
            @Override
            public void onSuccess(final QueryOrderParameters orderParameter) {
                waiter.fail();
                waiter.resume();
            }

            @Override
            public void onError(final Exception e) {
                waiter.assertTrue(e instanceof XlaToException);
                XlaToException xlaEx = (XlaToException) e;
                waiter.assertTrue(xlaEx.getCode() == 503);
                waiter.assertNotNull(xlaEx.getError());
                waiter.assertEquals(xlaEx.getError().getErrorId(), XlaToError.Error.XLATO_ERROR_007);
                waiter.assertEquals(xlaEx.getError().getErrorMsg(), "third party service not available");
                waiter.resume();
            }

        });
        waiter.await();
    }

    private String createMockOrderParameterResponse(
            final boolean isZeroConfEnabled,
            final double price,
            final double upperLimit,
            final double lowerLimit,
            final double zeroConfMaxAmount) {
        return "{\n"
                + "    \"zero_conf_enabled\": \"" + isZeroConfEnabled + "\",\n"
                + "    \"price\": \"" + price + "\",\n"
                + "    \"upper_limit\": \"" + upperLimit + "\",\n"
                + "    \"lower_limit\": \"" + lowerLimit + "\",\n"
                + "    \"zero_conf_max_amount\": \"" + zeroConfMaxAmount + "\"\n"
                + "}";
    }
}
