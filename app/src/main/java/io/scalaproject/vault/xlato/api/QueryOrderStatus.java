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

package io.scalaproject.vault.xlato.api;

import java.util.Date;

public interface QueryOrderStatus {
    enum State {
        UNDEF,
        TO_BE_CREATED, // order creation pending
        UNPAID, // waiting for Scala payment by user
        UNDERPAID, // order partially paid
        PAID_UNCONFIRMED, // order paid, waiting for enough confirmations
        PAID, // order paid and sufficiently confirmed
        BTC_SENT, // bitcoin payment sent
        TIMED_OUT, // order timed out before payment was complete
        NOT_FOUND // order wasn’t found in system (never existed or was purged)
    }

    boolean isCreated();

    boolean isTerminal();

    boolean isPending();

    boolean isPaid();

    boolean isSent();

    boolean isError();

    QueryOrderStatus.State getState();

    double getBtcAmount();

    String getBtcDestAddress();

    String getUuid();

    int getBtcNumConfirmationsThreshold();

    Date getCreatedAt();

    Date getExpiresAt();

    int getSecondsTillTimeout();

    double getIncomingAmountTotal();

    double getRemainingAmountIncoming();

    int getIncomingNumConfirmationsRemaining();

    double getIncomingPriceBtc();

    String getReceivingSubaddress();

    int getRecommendedMixin();
}
