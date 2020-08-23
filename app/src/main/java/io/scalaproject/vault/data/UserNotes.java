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

import io.scalaproject.vault.xlato.api.QueryOrderStatus;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

public class UserNotes {
    public String txNotes = "";
    public String note = "";
    public String xlatoKey = null;
    public String xlatoAmount = null; // could be a double - but we are not doing any calculations
    public String xlatoDestination = null;

    public UserNotes(final String txNotes) {
        if (txNotes == null) {
            return;
        }
        this.txNotes = txNotes;
        Pattern p = Pattern.compile("^\\{(xlato-\\w{6}),([0-9.]*)BTC,(\\w*)\\} ?(.*)");
        Matcher m = p.matcher(txNotes);
        if (m.find()) {
            xlatoKey = m.group(1);
            xlatoAmount = m.group(2);
            xlatoDestination = m.group(3);
            note = m.group(4);
        } else {
            note = txNotes;
        }
    }

    public void setNote(String newNote) {
        if (newNote != null) {
            note = newNote;
        } else {
            note = "";
        }
        txNotes = buildTxNote();
    }

    public void setxlatoStatus(QueryOrderStatus xlatoStatus) {
        if (xlatoStatus != null) {
            xlatoKey = xlatoStatus.getUuid();
            xlatoAmount = String.valueOf(xlatoStatus.getBtcAmount());
            xlatoDestination = xlatoStatus.getBtcDestAddress();
        } else {
            xlatoKey = null;
            xlatoAmount = null;
            xlatoDestination = null;
        }
        txNotes = buildTxNote();
    }

    private String buildTxNote() {
        StringBuffer sb = new StringBuffer();
        if (xlatoKey != null) {
            if ((xlatoAmount == null) || (xlatoDestination == null))
                throw new IllegalArgumentException("Broken notes");
            sb.append("{");
            sb.append(xlatoKey);
            sb.append(",");
            sb.append(xlatoAmount);
            sb.append("BTC,");
            sb.append(xlatoDestination);
            sb.append("}");
            if ((note != null) && (!note.isEmpty()))
                sb.append(" ");
        }
        sb.append(note);
        return sb.toString();
    }
}