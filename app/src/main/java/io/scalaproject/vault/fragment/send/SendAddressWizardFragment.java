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

package io.scalaproject.vault.fragment.send;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import com.google.android.material.textfield.TextInputLayout;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.unstoppabledomains.exceptions.ns.NamingServiceException;
import com.unstoppabledomains.resolution.DomainResolution;
import com.unstoppabledomains.resolution.Resolution;
import com.unstoppabledomains.resolution.naming.service.NamingServiceType;

import io.scalaproject.vault.AddressBookFragment;
import io.scalaproject.vault.Config;
import io.scalaproject.vault.R;
import io.scalaproject.vault.data.BarcodeData;
import io.scalaproject.vault.data.TxData;
import io.scalaproject.vault.data.TxDataBtc;
import io.scalaproject.vault.data.UserNotes;
import io.scalaproject.vault.model.PendingTransaction;
import io.scalaproject.vault.model.Wallet;
import io.scalaproject.vault.util.BitcoinAddressValidator;
import io.scalaproject.vault.util.Helper;
import io.scalaproject.vault.util.OpenAliasHelper;
import io.scalaproject.vault.util.PaymentProtocolHelper;
import io.scalaproject.vault.xlato.XlaToError;
import io.scalaproject.vault.xlato.XlaToException;

import java.util.Map;
import java.util.Objects;

import timber.log.Timber;

public class SendAddressWizardFragment extends SendWizardFragment {

    static final int INTEGRATED_ADDRESS_LENGTH = 106;

    public static SendAddressWizardFragment newInstance(Listener listener) {
        SendAddressWizardFragment instance = new SendAddressWizardFragment();
        instance.setSendListener(listener);
        return instance;
    }

    Listener sendListener;

    public SendAddressWizardFragment setSendListener(Listener listener) {
        this.sendListener = listener;
        return this;
    }

    public interface Listener {
        void setBarcodeData(BarcodeData data);

        BarcodeData getBarcodeData();

        BarcodeData popBarcodeData();

        void setMode(SendFragment.Mode mode);

        TxData getTxData();

        void saveContact(String name, String address);
    }

    private EditText etDummy;
    private TextInputLayout etAddress;
    private TextInputLayout etNotes;
    private CardView cvScan;
    private View tvPaymentIdIntegrated;
    private ImageButton bPasteAddress;
    private ImageButton bAddressBook;
    private CheckBox ckSaveAddress;
    private TextInputLayout etContactName;

    private boolean resolvingOA = false;
    private boolean resolvingPP = false;
    private String resolvedPP = null;

    OnScanListener onScanListener;

    public interface OnScanListener {
        void onScan();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Timber.d("onCreateView() %s", (String.valueOf(savedInstanceState)));

        View view = inflater.inflate(R.layout.fragment_send_address, container, false);

        tvPaymentIdIntegrated = view.findViewById(R.id.tvPaymentIdIntegrated);

        etAddress = view.findViewById(R.id.etAddress);
        Objects.requireNonNull(etAddress.getEditText()).setRawInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        etAddress.getEditText().setOnEditorActionListener((v, actionId, event) -> {
            // ignore ENTER
            return ((event != null) && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER));
        });
        etAddress.getEditText().setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                /*View next = etAddress;
                String enteredAddress = etAddress.getEditText().getText().toString().trim();
                processUD(enteredAddress);
            */
            }
        });
        etAddress.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                Timber.d("AFTER: %s", editable.toString());
                if (editable.toString().equals(resolvedPP)) return; // no change required
                resolvedPP = null;
                etAddress.setError(null);
                if (isIntegratedAddress()) {
                    Timber.d("isIntegratedAddress");
                    etAddress.setError(getString(R.string.info_paymentid_integrated));
                    tvPaymentIdIntegrated.setVisibility(View.VISIBLE);
                    sendListener.setMode(SendFragment.Mode.XLA);
                } else if (isBitcoinAddress() || (resolvedPP != null)) {
                    Timber.d("isBitcoinAddress");
                    setBtcMode();
                } else {
                    Timber.d("isStandardAddress or other");
                    tvPaymentIdIntegrated.setVisibility(View.INVISIBLE);
                    sendListener.setMode(SendFragment.Mode.XLA);
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        bPasteAddress = view.findViewById(R.id.bPasteAddress);
        bPasteAddress.setOnClickListener(v -> {
            final String clip = Helper.getClipBoardText(requireActivity());
            if (clip == null) return;
            // clean it up
            final String address = clip.replaceAll("[^0-9A-Z-a-z]", "");
            if (Wallet.isAddressValid(address) || BitcoinAddressValidator.validate(address)) {
                final EditText et = etAddress.getEditText();
                et.setText(address);
                et.setSelection(et.getText().length());
                etAddress.requestFocus();
            } else {
                final String bip70 = PaymentProtocolHelper.getBip70(clip);
                if (bip70 != null) {
                    final EditText et = etAddress.getEditText();
                    et.setText(clip);
                    et.setSelection(et.getText().length());
                    //processBip70(bip70);
                } else
                    Toast.makeText(getActivity(), getString(R.string.send_address_invalid), Toast.LENGTH_SHORT).show();
            }
        });

        bAddressBook = view.findViewById(R.id.bAddressBook);
        bAddressBook.setOnClickListener(v -> {
            final Bundle extras = new Bundle();
            extras.putString(AddressBookFragment.REQUEST_MODE, AddressBookFragment.MODE_TYPE_READONLY);
            replaceFragment(new AddressBookFragment(), null, extras);
        });

        etContactName = view.findViewById(R.id.etContactName);

        ckSaveAddress = view.findViewById(R.id.ckSaveAddress);
        ckSaveAddress.setOnClickListener(v -> etContactName.setVisibility(ckSaveAddress.isChecked() ? View.VISIBLE : View.GONE));

        etNotes = view.findViewById(R.id.etNotes);
        Objects.requireNonNull(etNotes.getEditText()).setRawInputType(InputType.TYPE_CLASS_TEXT);
        etNotes.getEditText().setOnEditorActionListener((v, actionId, event) -> {
            if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) && (event.getAction() == KeyEvent.ACTION_DOWN))
                    || (actionId == EditorInfo.IME_ACTION_DONE)) {
                etDummy.requestFocus();
                return true;
            }
            return false;
        });

        cvScan = view.findViewById(R.id.bScan);
        cvScan.setOnClickListener(v -> onScanListener.onScan());

        etDummy = view.findViewById(R.id.etDummy);
        etDummy.setRawInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        etDummy.requestFocus();

        return view;
    }

    /*private void goToOpenAlias(String enteredAddress) {
        String dnsOA = dnsFromOpenAlias(enteredAddress);
        Timber.d("OpenAlias is %s", dnsOA);
        if (dnsOA != null) {
            processOpenAlias(dnsOA);
        } else if (enteredAddress.length() == 0 || checkAddressNoError()) {
            etAddress.setErrorEnabled(false);
        } else {
            // Not all UD address match Patterns.DOMAIN_NAME (eg. .888, .x)
            processUD(enteredAddress);
        }
    }*/

    void replaceFragment(Fragment newFragment, String stackName, Bundle extras) {
        if (extras != null) {
            newFragment.setArguments(extras);
        }

        FragmentTransaction transaction = requireActivity().getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, newFragment);
        transaction.addToBackStack(stackName);
        transaction.commit();
    }

    private void setBtcMode() {
        Timber.d("setBtcMode");
        tvPaymentIdIntegrated.setVisibility(View.INVISIBLE);
        sendListener.setMode(SendFragment.Mode.BTC);
    }

    /*private void processOpenAlias(String dnsOA) {
        if (resolvingOA) return; // already resolving - just wait
        sendListener.popBarcodeData();
        resolvingOA = true;
        int nErrorBK = etAddress.getErrorCurrentTextColors();
        etAddress.setErrorTextColor(getResources().getColorStateList(R.color.c_light_blue));
        etAddress.setError(getString(R.string.send_address_resolve_openalias));
        etAddress.setErrorTextColor(ColorStateList.valueOf(nErrorBK));
        OpenAliasHelper.resolve(dnsOA, new OpenAliasHelper.OnResolvedListener() {
            @Override
            public void onResolved(BarcodeData barcodeData) {
                resolvingOA = false;
                if (barcodeData != null) {
                    Timber.d("Security=%s, %s", barcodeData.security.toString(), barcodeData.address);
                    processScannedData(barcodeData);
                } else {
                    Timber.d("NO XLA OPENALIAS TXT FOUND");
                    processUD(dnsOA);
                }
            }

            @Override
            public void onFailure() {
                resolvingOA = false;
                Timber.e("OA FAILED");
                processUD(dnsOA);
            }
        });
    }*/

    @SuppressLint("UseCompatLoadingForColorStateLists")
    private void processUD(String udString) {
        sendListener.popBarcodeData();

        DomainResolution resolution = new Resolution();

        final boolean[] domainIsUD = {false};
        final String[] address = {""};
        etAddress.setErrorTextColor(getResources().getColorStateList(R.color.c_light_blue));
        etAddress.setError(getString(R.string.send_address_resolve_ud));
        new Thread(() -> {
                try {
                    address[0] = resolution.getAddress(udString, "xla");
                    domainIsUD[0] = true;
                } catch (NamingServiceException e) {
                    Timber.d(e.getLocalizedMessage());
                    switch (e.getCode()) {
                        case UnknownCurrency:
                        case RecordNotFound:
                            domainIsUD[0] = true;
                            break;
                        default:
                            domainIsUD[0] = false;
                            break;
                    }
                }

            requireActivity().runOnUiThread(new Runnable() {
                @SuppressLint("UseCompatLoadingForColorStateLists")
                public void run() {
                    if (domainIsUD[0]) {
                        BarcodeData barcodeData = new BarcodeData(BarcodeData.Asset.XLA, address[0], udString, null, null, BarcodeData.Security.NORMAL);
                        processScannedData(barcodeData);
                    } else {
                        shakeAddress();
                        Timber.d("Non ENS / UD address %s", udString);
                        etAddress.setErrorTextColor(getResources().getColorStateList(R.color.c_red));
                        etAddress.setError(getString(R.string.send_address_invalid));
                    }
                }
            });
        }).start();
    }

    /*private void processBip70(final String bip70) {
        Timber.d("RESOLVED PP: %s", resolvedPP);
        if (resolvingPP) return; // already resolving - just wait
        resolvingPP = true;
        sendListener.popBarcodeData();
        etAddress.setError(getString(R.string.send_address_resolve_bip70));
        PaymentProtocolHelper.resolve(bip70, new PaymentProtocolHelper.OnResolvedListener() {
            @Override
            public void onResolved(BarcodeData.Asset asset, String address, double amount, String resolvedBip70) {
                resolvingPP = false;
                if (asset != BarcodeData.Asset.BTC)
                    throw new IllegalArgumentException("only BTC here");

                if (resolvedBip70 == null)
                    throw new IllegalArgumentException("success means we have a pp_url - else die");

                final BarcodeData barcodeData =
                        new BarcodeData(BarcodeData.Asset.BTC, address, null,
                                resolvedBip70, null, String.valueOf(amount),
                                BarcodeData.Security.BIP70);
                etNotes.post(new Runnable() {
                    @Override
                    public void run() {
                        Timber.d("security is %s", barcodeData.security);
                        processScannedData(barcodeData);
                        etNotes.requestFocus();
                    }
                });
            }

            @Override
            public void onFailure(final Exception ex) {
                resolvingPP = false;
                etAddress.post(new Runnable() {
                    @Override
                    public void run() {
                        int errorMsgId = R.string.send_address_not_bip70;
                        if (ex instanceof XlaToException) {
                            XlaToError error = ((XlaToException) ex).getError();
                            if (error != null) {
                                errorMsgId = error.getErrorMsgId();
                            }
                        }
                        etAddress.setError(getString(errorMsgId));
                    }
                });
                Timber.d("PP FAILED");
            }
        });
    }*/

    private boolean checkAddressNoError() {
        String address = Objects.requireNonNull(etAddress.getEditText()).getText().toString();
        return Wallet.isAddressValid(address)
                || BitcoinAddressValidator.validate(address)
                || (resolvedPP != null);
    }

    @SuppressLint("UseCompatLoadingForColorStateLists")
    private boolean checkAddress() {
        etAddress.setErrorTextColor(getResources().getColorStateList(R.color.c_red));
        boolean ok = checkAddressNoError();
        if (!ok) {
            etAddress.setError(getString(R.string.send_address_invalid));
        } else {
            etAddress.setError(null);
        }
        return ok;
    }

    private boolean isIntegratedAddress() {
        String address = Objects.requireNonNull(etAddress.getEditText()).getText().toString();
        return (address.length() == INTEGRATED_ADDRESS_LENGTH)
                && Wallet.isAddressValid(address);
    }

    private boolean isBitcoinAddress() {
        final String address = Objects.requireNonNull(etAddress.getEditText()).getText().toString();
        return BitcoinAddressValidator.validate(address);
    }

    private void shakeAddress() {
        etAddress.startAnimation(Helper.getShakeAnimation(getContext()));
    }

    @Override
    public boolean onValidateFields() {
        if (!checkAddress()) {
            String enteredAddress = Objects.requireNonNull(etAddress.getEditText()).getText().toString().trim();
            processUD(enteredAddress);
            return false;
        }

        if (sendListener != null) {
            TxData txData = sendListener.getTxData();
            if (txData instanceof TxDataBtc) {
                if (resolvedPP != null) {
                    // take the value from the field nonetheless as this is what the user sees
                    // (in case we have a bug somewhere)
                    ((TxDataBtc) txData).setBip70(Objects.requireNonNull(etAddress.getEditText()).getText().toString());
                    ((TxDataBtc) txData).setBtcAddress(null);
                } else {
                    ((TxDataBtc) txData).setBtcAddress(Objects.requireNonNull(etAddress.getEditText()).getText().toString());
                    ((TxDataBtc) txData).setBip70(null);
                }
                txData.setDestinationAddress(null);
            } else {
                txData.setDestinationAddress(Objects.requireNonNull(etAddress.getEditText()).getText().toString());
            }

            txData.setUserNotes(new UserNotes(Objects.requireNonNull(etNotes.getEditText()).getText().toString()));
            txData.setPriority(PendingTransaction.Priority.Priority_Default);
            txData.setMixin(SendFragment.MIXIN);
        }

        if(ckSaveAddress.isChecked()) {
            String contactName = Objects.requireNonNull(etContactName.getEditText()).getText().toString().trim();
            String destinationAddress = Objects.requireNonNull(etAddress.getEditText()).getText().toString().trim();

            if (contactName.isEmpty()) {
                etContactName.setError(getString(R.string.contact_name_empty_error));
                return false;
            } else {
                sendListener.saveContact(contactName, destinationAddress);
                etContactName.setError(null);
            }
        }

        return true;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnScanListener) {
            onScanListener = (OnScanListener) context;
        } else {
            throw new ClassCastException(context.toString() + " must implement ScanListener");
        }
    }

    // QR Scan Stuff

    @Override
    public void onResume() {
        super.onResume();
        Timber.d("onResume");
        processScannedData();

        String address = Config.read(Config.CONFIG_KEY_SELECTED_ADDRESS);
        if(!address.isEmpty()) {
            Config.write(Config.CONFIG_KEY_SELECTED_ADDRESS, "");
            Objects.requireNonNull(etAddress.getEditText()).setText(address);
        }
    }

    public void processScannedData(BarcodeData barcodeData) {
        sendListener.setBarcodeData(barcodeData);
        if (isResumed())
            processScannedData();
    }

    public void processScannedData() {
        resolvedPP = null;
        BarcodeData barcodeData = sendListener.getBarcodeData();
        if (barcodeData != null) {
            Timber.d("GOT DATA");

            if (barcodeData.bip70 != null) {
                setBtcMode();
                if (barcodeData.security == BarcodeData.Security.BIP70) {
                    resolvedPP = barcodeData.bip70;
                    etAddress.setError(getString(R.string.send_address_bip70));
                } else {
                    //processBip70(barcodeData.bip70);
                }
                Objects.requireNonNull(etAddress.getEditText()).setText(barcodeData.bip70);
            } else if (barcodeData.address != null) {
                Objects.requireNonNull(etAddress.getEditText()).setText(barcodeData.address);
                if (checkAddress()) {
                    if (barcodeData.security == BarcodeData.Security.OA_NO_DNSSEC)
                        etAddress.setError(getString(R.string.send_address_no_dnssec));
                    else if (barcodeData.security == BarcodeData.Security.OA_DNSSEC)
                        etAddress.setError(getString(R.string.send_address_openalias));
                }
            } else {
                Objects.requireNonNull(etAddress.getEditText()).getText().clear();
                etAddress.setError(null);
            }

            String scannedNotes = barcodeData.description;
            if (scannedNotes != null) {
                Objects.requireNonNull(etNotes.getEditText()).setText(scannedNotes);
            } else {
                Objects.requireNonNull(etNotes.getEditText()).getText().clear();
                etNotes.setError(null);
            }
        } else
            Timber.d("barcodeData=null");
    }

    @Override
    public void onResumeFragment() {
        super.onResumeFragment();
        Timber.d("onResumeFragment()");
        etDummy.requestFocus();
    }

    String dnsFromOpenAlias(String openalias) {
        Timber.d("checking openalias candidate %s", openalias);
        if (Patterns.DOMAIN_NAME.matcher(openalias).matches()) return openalias;
        if (Patterns.EMAIL_ADDRESS.matcher(openalias).matches()) {
            openalias = openalias.replaceFirst("@", ".");
            if (Patterns.DOMAIN_NAME.matcher(openalias).matches()) return openalias;
        }
        return null; // not an openalias
    }
}
