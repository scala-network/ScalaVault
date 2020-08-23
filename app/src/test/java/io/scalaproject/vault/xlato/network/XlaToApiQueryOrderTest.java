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
import io.scalaproject.vault.xlato.api.QueryOrderStatus;
import io.scalaproject.vault.xlato.api.XlaToApi;

import net.jodah.concurrentunit.Waiter;

import org.json.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeoutException;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.Assert.assertEquals;

public class XlaToApiQueryOrderTest {

    static final SimpleDateFormat DATETIME_FORMATTER = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

    static Date ParseDate(String dateString) throws ParseException {
        return DATETIME_FORMATTER.parse(dateString.replaceAll("Z$", "+0000"));
    }


    private MockWebServer mockWebServer;

    private XlaToApi xlaToApi;

    private OkHttpClient okHttpClient = new OkHttpClient();
    private Waiter waiter;

    @Mock
    XlaToCallback<QueryOrderStatus> mockQueryxlaToCallback;

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
    public void orderStatus_shouldBePostMethod()
            throws InterruptedException {

        xlaToApi.queryOrderStatus("xlato - efMsiU", mockQueryxlaToCallback);

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("POST", request.getMethod());
    }

    @Test
    public void orderStatus_shouldBeContentTypeJson()
            throws InterruptedException {

        xlaToApi.queryOrderStatus("xlato - efMsiU", mockQueryxlaToCallback);

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"));
    }

    @Test
    public void orderStatus_shouldContainValidBody()
            throws InterruptedException {

        final String validBody = "{\"uuid\":\"xlato - efMsiU\"}";

        xlaToApi.queryOrderStatus("xlato - efMsiU", mockQueryxlaToCallback);

        RecordedRequest request = mockWebServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertEquals(validBody, body);
    }

    @Test
    public void orderStatus_wasSuccessfulShouldRespondWithOrder()
            throws TimeoutException {

//TODO: state enum
// TODO dates are dates
        final String state = "UNPAID";
        final double btcAmount = 0.1;
        final String btcDestAddress = "1FhnVJi2V1k4MqXm2nHoEbY5LV7FPai7bb";
        final String uuid = "xlato - efMsiU";
        final int btcNumConfirmationsBeforePurge = 144;
        final String createdAt = "2017-11-17T12:20:02Z";
        final String expiresAt = "2017-11-17T12:35:02Z";
        final int secondsTillTimeout = 882;
        final double xlaAmountTotal = 6.464;
        final double xlaAmountRemaining = 6.464;
        final int xlaNumConfirmationsRemaining = -1;
        final double xlaPriceBtc = 0.0154703;
        final String xlaReceivingSubaddress = "83BGzCTthheE2KxNTBPnPJjJUthYPfDfCf3ENSVQcpga8RYSxNz9qCz1qp9MLye9euMjckGi11cRdeVGqsVqTLgH8w5fJ1D";
        final int xlaRecommendedMixin = 5;

        MockResponse jsonMockResponse = new MockResponse().setBody(
                createMockQueryOrderResponse(
                        state,
                        btcAmount,
                        btcDestAddress,
                        uuid,
                        btcNumConfirmationsBeforePurge,
                        createdAt,
                        expiresAt,
                        secondsTillTimeout,
                        xlaAmountTotal,
                        xlaAmountRemaining,
                        xlaNumConfirmationsRemaining,
                        xlaPriceBtc,
                        xlaReceivingSubaddress,
                        xlaRecommendedMixin));
        mockWebServer.enqueue(jsonMockResponse);

        xlaToApi.queryOrderStatus(uuid, new XlaToCallback<QueryOrderStatus>() {
            @Override
            public void onSuccess(final QueryOrderStatus orderStatus) {
                waiter.assertEquals(orderStatus.getState().toString(), state);
                waiter.assertEquals(orderStatus.getBtcAmount(), btcAmount);
                waiter.assertEquals(orderStatus.getBtcDestAddress(), btcDestAddress);
                waiter.assertEquals(orderStatus.getUuid(), uuid);
                waiter.assertEquals(orderStatus.getBtcNumConfirmationsThreshold(), btcNumConfirmationsBeforePurge);
                try {
                    waiter.assertEquals(orderStatus.getCreatedAt(), ParseDate(createdAt));
                    waiter.assertEquals(orderStatus.getExpiresAt(), ParseDate(expiresAt));
                } catch (ParseException ex) {
                    waiter.fail(ex);
                }
                waiter.assertEquals(orderStatus.getSecondsTillTimeout(), secondsTillTimeout);
                waiter.assertEquals(orderStatus.getIncomingAmountTotal(), xlaAmountTotal);
                waiter.assertEquals(orderStatus.getRemainingAmountIncoming(), xlaAmountRemaining);
                waiter.assertEquals(orderStatus.getIncomingNumConfirmationsRemaining(), xlaNumConfirmationsRemaining);
                waiter.assertEquals(orderStatus.getIncomingPriceBtc(), xlaPriceBtc);
                waiter.assertEquals(orderStatus.getReceivingSubaddress(), xlaReceivingSubaddress);
                waiter.assertEquals(orderStatus.getRecommendedMixin(), xlaRecommendedMixin);
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
    public void orderStatus_wasNotSuccessfulShouldCallOnError()
            throws TimeoutException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        xlaToApi.queryOrderStatus("xlato - efMsiU", new XlaToCallback<QueryOrderStatus>() {
            @Override
            public void onSuccess(final QueryOrderStatus orderStatus) {
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
    public void orderStatus_orderNotFoundShouldCallOnError()
            throws TimeoutException {
        mockWebServer.enqueue(new MockResponse().
                setResponseCode(404).
                setBody("{\"error_msg\":\"requested order not found\",\"error\":\"XLATO-ERROR-006\"}"));
        xlaToApi.queryOrderStatus("xlato - efMsiU", new XlaToCallback<QueryOrderStatus>() {
            @Override
            public void onSuccess(final QueryOrderStatus orderStatus) {
                waiter.fail();
                waiter.resume();
            }

            @Override
            public void onError(final Exception e) {
                waiter.assertTrue(e instanceof XlaToException);
                XlaToException xlaEx = (XlaToException) e;
                waiter.assertTrue(xlaEx.getCode() == 404);
                waiter.assertNotNull(xlaEx.getError());
                waiter.assertEquals(xlaEx.getError().getErrorId(), XlaToError.Error.XLATO_ERROR_006);
                waiter.assertEquals(xlaEx.getError().getErrorMsg(), "requested order not found");
                waiter.resume();
            }

        });
        waiter.await();
    }

    private String createMockQueryOrderResponse(
            final String state,
            final double btcAmount,
            final String btcDestAddress,
            final String uuid,
            final int btcNumConfirmationsBeforePurge,
            final String createdAt,
            final String expiresAt,
            final int secondsTillTimeout,
            final double xlaAmountTotal,
            final double xlaAmountRemaining,
            final int xlaNumConfirmationsRemaining,
            final double xlaPriceBtc,
            final String xlaReceivingSubaddress,
            final int xlaRecommendedMixin
    ) {
        return "{\n" +
                "    \"incoming_price_btc\": \"" + xlaPriceBtc + "\",\n" +
                "    \"uuid\":\"" + uuid + "\",\n" +
                "    \"state\":\"" + state + "\",\n" +
                "    \"btc_amount\":\"" + btcAmount + "\",\n" +
                "    \"btc_dest_address\":\"" + btcDestAddress + "\",\n" +
                "    \"receiving_subaddress\":\"" + xlaReceivingSubaddress + "\",\n" +
                "    \"created_at\":\"" + createdAt + "\",\n" +
                "    \"expires_at\":\"" + expiresAt + "\",\n" +
                "    \"seconds_till_timeout\":\"" + secondsTillTimeout + "\",\n" +
                "    \"incoming_amount_total\":\"" + xlaAmountTotal + "\",\n" +
                "    \"remaining_amount_incoming\":\"" + xlaAmountRemaining + "\",\n" +
                "    \"incoming_num_confirmations_remaining\":\"" + xlaNumConfirmationsRemaining + "\",\n" +
                "    \"recommended_mixin\":\"" + xlaRecommendedMixin + "\",\n" +
                "    \"btc_num_confirmations_threshold\":\"" + btcNumConfirmationsBeforePurge + "\",\n"
                + "}";
    }
}