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

package io.scalaproject.vault.data;

import io.scalaproject.vault.model.PendingTransaction;

public class PendingTx {
    final public PendingTransaction.Status status;
    final public String error;
    final public long amount;
    final public long dust;
    final public long fee;
    final public String txId;
    final public long txCount;

    public PendingTx(PendingTransaction pendingTransaction) {
        status = pendingTransaction.getStatus();
        error = pendingTransaction.getErrorString();
        amount = pendingTransaction.getAmount();
        dust = pendingTransaction.getDust();
        fee = pendingTransaction.getFee();
        txId = pendingTransaction.getFirstTxId();
        txCount = pendingTransaction.getTxCount();
    }
}
