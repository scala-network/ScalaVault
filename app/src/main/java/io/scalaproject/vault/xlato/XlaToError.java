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

package io.scalaproject.vault.xlato;

import io.scalaproject.vault.R;

import org.json.JSONException;
import org.json.JSONObject;

public class XlaToError {
    private final Error errorId;
    private final String errorMsg;

    public XlaToError() {
        errorId = null;
        errorMsg = "";
    }

    public enum Error {
        XLATO_ERROR_UNDEF,
        XLATO_ERROR_001, // (503) internal services not available	try again later
        XLATO_ERROR_002, // (400) malformed bitcoin address	check address validity
        XLATO_ERROR_003, // (400) invalid bitcoin amount	check amount data type
        XLATO_ERROR_004, // (400) bitcoin amount out of bounds	check min and max amount
        XLATO_ERROR_005, // (400) unexpected validation error	contact support
        XLATO_ERROR_006, // (404) requested order not found	check order UUID
        XLATO_ERROR_007, // (503) third party service not available	try again later
        XLATO_ERROR_008, // (503) insufficient funds available	try again later
        XLATO_ERROR_009, // (400) invalid request	check request parameters
        XLATO_ERROR_010, // (400) payment protocol failed	invalid or outdated data served by url
        XLATO_ERROR_011, // (400) malformed payment protocol url	url is malformed or cannot be contacted
        XLATO_ERROR_012  // (403) too many requests
    }

    public boolean isRetryable() {
        return (errorId == Error.XLATO_ERROR_001)
                || (errorId == Error.XLATO_ERROR_007)
                || (errorId == Error.XLATO_ERROR_008);
    }

    public static String getErrorIdString(Error anError) {
        return anError.toString().replace('_', '-');
    }

    // mostly for testing
    public XlaToError(String errorId, String message) {
        Error error;
        try {
            error = Error.valueOf(errorId.replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            error = Error.XLATO_ERROR_UNDEF;
        }
        this.errorId = error;
        this.errorMsg = message;
    }

    public XlaToError(final JSONObject jsonObject) throws JSONException {
        final String errorId = jsonObject.getString("error");
        Error error;
        try {
            error = Error.valueOf(errorId.replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            error = Error.XLATO_ERROR_UNDEF;
        }
        this.errorId = error;
        this.errorMsg = jsonObject.getString("error_msg");
    }

    public Error getErrorId() {
        return errorId;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public int getErrorMsgId() {
        switch (errorId) {
            case XLATO_ERROR_001:
                return R.string.xmrto_error_001;
            case XLATO_ERROR_004:
                return R.string.xmrto_error_004;
            case XLATO_ERROR_010:
                return R.string.xmrto_error_010;
            case XLATO_ERROR_012:
                return R.string.xmrto_error_012;
            default:
                return R.string.xmrto_error;
        }
    }

    @Override
    public String toString() {
        return getErrorIdString(getErrorId()) + ": " + getErrorMsg();
    }

    /* Errors from Query Parameters
        HTTP code	XLA.TO error code	Error message/reason	Solution
        503	XLATO-ERROR-001	internal services not available	try again later
        503	XLATO-ERROR-007	third party service not available	try again later
        503	XLATO-ERROR-008	insufficient funds available	try again later
     */

    /* Errors from Create Order
        HTTP code	XLA.TO error code	Error message/reason	Solution
        503	XLATO-ERROR-001	internal services not available	try again later
        400	XLATO-ERROR-002	malformed bitcoin address	check address validity
        400	XLATO-ERROR-003	invalid bitcoin amount	check amount data type
        503	XLATO-ERROR-004	bitcoin amount out of bounds	check min and max amount
        400	XLATO-ERROR-005	unexpected validation error	contact support

        Errors from Create Order Payment Protocol
        400 XLATO-ERROR-010 payment protocol failed 	invalid or outdated data served by url
        400 XLATO-ERROR-011 malformed payment protocol url 	url is malformed or cannot be contacted
     */

    /* Errors from Query Order
        HTTP code	XLA.TO error code	Error message/reason	Solution
        400	XLATO-ERROR-009	invalid request	check request parameters
        404	XLATO-ERROR-006	requested order not found	check order UUID
     */

    /* General
        403 XLATO-ERROR-012 too many requests
     */
}
