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

package io.scalaproject.vault;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import io.scalaproject.vault.model.Wallet;
import io.scalaproject.vault.model.WalletManager;
import io.scalaproject.vault.util.FingerprintHelper;
import io.scalaproject.vault.util.Helper;
import io.scalaproject.vault.util.KeyStoreHelper;
import io.scalaproject.vault.util.RestoreHeight;
import io.scalaproject.vault.util.ledger.Scala;
import io.scalaproject.vault.widget.Toolbar;
import com.nulabinc.zxcvbn.Strength;
import com.nulabinc.zxcvbn.Zxcvbn;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;
import java.util.TimeZone;

import timber.log.Timber;

public class GenerateFragment extends Fragment {

    static final String TYPE = "type";
    static final String TYPE_NEW = "new";
    static final String TYPE_KEY = "key";
    static final String TYPE_SEED = "seed";
    static final String TYPE_LEDGER = "ledger";
    static final String TYPE_VIEWONLY = "view";

    private TextInputLayout etWalletName;
    private TextInputLayout etWalletPassword;
    private LinearLayout llFingerprintAuth;
    private TextInputLayout etWalletAddress;
    private TextInputLayout etWalletMnemonic;
    private TextInputLayout etWalletViewKey;
    private TextInputLayout etWalletSpendKey;
    private TextInputLayout etWalletRestoreHeight;
    private Button bGenerate;
    private SwitchCompat sFingerprintAuth;

    private String type = null;

    private void clearErrorOnTextEntry(final TextInputLayout textInputLayout) {
        Objects.requireNonNull(textInputLayout.getEditText()).addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                textInputLayout.setError(null);
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        Bundle args = getArguments();
        assert args != null;
        this.type = args.getString(TYPE);

        View view = inflater.inflate(R.layout.fragment_generate, container, false);

        etWalletName = view.findViewById(R.id.etWalletName);
        etWalletPassword = view.findViewById(R.id.etWalletPassword);
        llFingerprintAuth = view.findViewById(R.id.llFingerprintAuth);
        etWalletMnemonic = view.findViewById(R.id.etWalletMnemonic);
        etWalletAddress = view.findViewById(R.id.etWalletAddress);
        etWalletViewKey = view.findViewById(R.id.etWalletViewKey);
        etWalletSpendKey = view.findViewById(R.id.etWalletSpendKey);
        etWalletRestoreHeight = view.findViewById(R.id.etWalletRestoreHeight);
        bGenerate = view.findViewById(R.id.bGenerate);
        sFingerprintAuth = view.findViewById(R.id.sFingerprintAuth);

        Objects.requireNonNull(etWalletAddress.getEditText()).setRawInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        Objects.requireNonNull(etWalletViewKey.getEditText()).setRawInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        Objects.requireNonNull(etWalletSpendKey.getEditText()).setRawInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        Objects.requireNonNull(etWalletName.getEditText()).setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                checkName();
            }
        });
        clearErrorOnTextEntry(etWalletName);

        Objects.requireNonNull(etWalletPassword.getEditText()).addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                checkPassword();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        Objects.requireNonNull(etWalletMnemonic.getEditText()).setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                checkMnemonic();
            }
        });
        clearErrorOnTextEntry(etWalletMnemonic);

        etWalletAddress.getEditText().setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                checkAddress();
            }
        });
        clearErrorOnTextEntry(etWalletAddress);

        etWalletViewKey.getEditText().setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                checkViewKey();
            }
        });
        clearErrorOnTextEntry(etWalletViewKey);

        etWalletSpendKey.getEditText().setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                checkSpendKey();
            }
        });
        clearErrorOnTextEntry(etWalletSpendKey);

        Helper.showKeyboard(getActivity());

        etWalletName.getEditText().setOnEditorActionListener((v, actionId, event) -> {
            if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) && (event.getAction() == KeyEvent.ACTION_DOWN))
                    || (actionId == EditorInfo.IME_ACTION_NEXT)) {
                if (checkName()) {
                    etWalletPassword.requestFocus();
                } // otherwise ignore
                return true;
            }
            return false;
        });

        if (FingerprintHelper.isDeviceSupported(requireContext())) {
            llFingerprintAuth.setVisibility(View.VISIBLE);

            sFingerprintAuth.setOnClickListener(view1 -> {
                if (!sFingerprintAuth.isChecked()) return;

                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity(), R.style.MaterialAlertDialogCustom);
                builder.setMessage(Html.fromHtml(getString(R.string.generate_fingerprint_warn)))
                        .setCancelable(false)
                        .setPositiveButton(getString(R.string.label_ok), null)
                        .setNegativeButton(getString(R.string.label_cancel), (dialogInterface, i) -> sFingerprintAuth.setChecked(false))
                        .show();
            });
        }

        switch (type) {
            case TYPE_NEW -> {
                etWalletPassword.getEditText().setImeOptions(EditorInfo.IME_ACTION_DONE);
                etWalletPassword.getEditText().setOnEditorActionListener((v, actionId, event) -> {
                    if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) && (event.getAction() == KeyEvent.ACTION_DOWN))
                            || (actionId == EditorInfo.IME_ACTION_DONE)) {
                        Helper.hideKeyboard(getActivity());
                        generateWallet();
                        return true;
                    }
                    return false;
                });
            }
            case TYPE_LEDGER -> {
                etWalletPassword.getEditText().setImeOptions(EditorInfo.IME_ACTION_DONE);
                etWalletPassword.getEditText().setOnEditorActionListener((v, actionId, event) -> {
                    if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) && (event.getAction() == KeyEvent.ACTION_DOWN))
                            || (actionId == EditorInfo.IME_ACTION_DONE)) {
                        etWalletRestoreHeight.requestFocus();
                        return true;
                    }
                    return false;
                });
            }
            case TYPE_SEED -> {
                etWalletPassword.getEditText().setOnEditorActionListener((v, actionId, event) -> {
                    if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) && (event.getAction() == KeyEvent.ACTION_DOWN))
                            || (actionId == EditorInfo.IME_ACTION_NEXT)) {
                        etWalletMnemonic.requestFocus();
                        return true;
                    }
                    return false;
                });
                etWalletMnemonic.setVisibility(View.VISIBLE);
                etWalletMnemonic.getEditText().setOnEditorActionListener((v, actionId, event) -> {
                    if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) && (event.getAction() == KeyEvent.ACTION_DOWN))
                            || (actionId == EditorInfo.IME_ACTION_NEXT)) {
                        if (checkMnemonic()) {
                            etWalletRestoreHeight.requestFocus();
                        }
                        return true;
                    }
                    return false;
                });
            }
            case TYPE_KEY, TYPE_VIEWONLY -> {
                etWalletPassword.getEditText().setOnEditorActionListener((v, actionId, event) -> {
                    if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) && (event.getAction() == KeyEvent.ACTION_DOWN))
                            || (actionId == EditorInfo.IME_ACTION_NEXT)) {
                        etWalletAddress.requestFocus();
                        return true;
                    }
                    return false;
                });
                etWalletAddress.setVisibility(View.VISIBLE);
                etWalletAddress.getEditText().setOnEditorActionListener((v, actionId, event) -> {
                    if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) && (event.getAction() == KeyEvent.ACTION_DOWN))
                            || (actionId == EditorInfo.IME_ACTION_NEXT)) {
                        if (checkAddress()) {
                            etWalletViewKey.requestFocus();
                        }
                        return true;
                    }
                    return false;
                });
                etWalletViewKey.setVisibility(View.VISIBLE);
                etWalletViewKey.getEditText().setOnEditorActionListener((v, actionId, event) -> {
                    if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) && (event.getAction() == KeyEvent.ACTION_DOWN))
                            || (actionId == EditorInfo.IME_ACTION_NEXT)) {
                        if (checkViewKey()) {
                            if (type.equals(TYPE_KEY)) {
                                etWalletSpendKey.requestFocus();
                            } else {
                                etWalletRestoreHeight.requestFocus();
                            }
                        }
                        return true;
                    }
                    return false;
                });
            }
        }
        if (type.equals(TYPE_KEY)) {
            etWalletSpendKey.setVisibility(View.VISIBLE);
            etWalletSpendKey.getEditText().setOnEditorActionListener((v, actionId, event) -> {
                if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) && (event.getAction() == KeyEvent.ACTION_DOWN))
                        || (actionId == EditorInfo.IME_ACTION_NEXT)) {
                    if (checkSpendKey()) {
                        etWalletRestoreHeight.requestFocus();
                    }
                    return true;
                }
                return false;
            });
        }
        if (!type.equals(TYPE_NEW)) {
            etWalletRestoreHeight.setVisibility(View.VISIBLE);
            Objects.requireNonNull(etWalletRestoreHeight.getEditText()).setOnEditorActionListener((v, actionId, event) -> {
                if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) && (event.getAction() == KeyEvent.ACTION_DOWN))
                        || (actionId == EditorInfo.IME_ACTION_DONE)) {
                    Helper.hideKeyboard(getActivity());
                    generateWallet();
                    return true;
                }
                return false;
            });
        }
        bGenerate.setOnClickListener(v -> {
            Helper.hideKeyboard(getActivity());
            generateWallet();
        });

        etWalletName.requestFocus();
        initZxcvbn();

        return view;
    }

    Zxcvbn zxcvbn = new Zxcvbn();

    // initialize zxcvbn engine in background thread
    private void initZxcvbn() {
        new Thread(() -> zxcvbn.measure("")).start();
    }

    private void checkPassword() {
        String password = Objects.requireNonNull(etWalletPassword.getEditText()).getText().toString();
        if (!password.isEmpty()) {
            Strength strength = zxcvbn.measure(password);
            int msg;
            double guessesLog10 = strength.getGuessesLog10();
            if (guessesLog10 < 10)
                msg = R.string.password_weak;
            else if (guessesLog10 < 11)
                msg = R.string.password_fair;
            else if (guessesLog10 < 12)
                msg = R.string.password_good;
            else if (guessesLog10 < 13)
                msg = R.string.password_strong;
            else
                msg = R.string.password_very_strong;
            etWalletPassword.setError(getResources().getString(msg));
        } else {
            etWalletPassword.setError(null);
        }
    }

    private boolean checkName() {
        String name = Objects.requireNonNull(etWalletName.getEditText()).getText().toString();
        boolean ok = true;
        if (name.isEmpty()) {
            etWalletName.setError(getString(R.string.generate_wallet_name));
            ok = false;
        } else if (name.charAt(0) == '.') {
            etWalletName.setError(getString(R.string.generate_wallet_dot));
            ok = false;
        } else {
            File walletFile = Helper.getWalletFile(getActivity(), name);
            if (WalletManager.getInstance().walletExists(walletFile)) {
                etWalletName.setError(getString(R.string.generate_wallet_exists));
                ok = false;
            }
        }
        if (ok) {
            etWalletName.setError(null);
        }
        return ok;
    }

    private boolean checkHeight() {
        long height = !type.equals(TYPE_NEW) ? getHeight() : 0;
        boolean ok = true;
        if (height < 0) {
            if(etWalletRestoreHeight.getError() == null || etWalletRestoreHeight.getError().toString().isEmpty())
                etWalletRestoreHeight.setError(getString(R.string.generate_restoreheight_error));
            ok = false;
        }
        if (ok) {
            etWalletRestoreHeight.setError(null);
        }
        return ok;
    }

    private boolean checkHeightDate(final Date date) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.set(Calendar.DST_OFFSET, 0);
        cal.setTime(date);

        if (date.before(RestoreHeight.getGenesisBLockDate()))
        {
            etWalletRestoreHeight.setError(getString(R.string.generate_restoreheight_min_error, RestoreHeight.GENESISBLOCK_DATE));
            return false;
        }

        etWalletRestoreHeight.setError(null);
        return true;
    }

    private long getHeight() {
        long height = -1;

        String restoreHeight = Objects.requireNonNull(etWalletRestoreHeight.getEditText()).getText().toString().trim();

        if (restoreHeight.isEmpty()) return 0;

        try {
            // is it a date?
            @SuppressLint("SimpleDateFormat") SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd");
            parser.setLenient(false);

            Date date = parser.parse(restoreHeight);
            if (!checkHeightDate(date))
                return -1;

            height = RestoreHeight.getInstance().getHeight(date);
        } catch (ParseException ex) {
            Timber.w("Restore Height = %s", restoreHeight);
        }
        if ((height < 0) && (restoreHeight.length() == 8))
            try {
                // is it a date without dashes?
                @SuppressLint("SimpleDateFormat") SimpleDateFormat parser = new SimpleDateFormat("yyyyMMdd");
                parser.setLenient(false);

                Date date = parser.parse(restoreHeight);
                if (!checkHeightDate(date))
                    return -1;

                height = RestoreHeight.getInstance().getHeight(date);
            } catch (ParseException ex) {
                Timber.w("Restore Height = %s", restoreHeight);
            }
        if (height < 0)
            try {
                // or is it a height?
                height = Long.parseLong(restoreHeight);
            } catch (NumberFormatException ex) {
                return -1;
            }
        Timber.d("Using Restore Height = %d", height);
        return height;
    }

    private boolean checkMnemonic() {
        String seed = Objects.requireNonNull(etWalletMnemonic.getEditText()).getText().toString();
        boolean ok = (seed.split("\\s").length == 25); // 25 words
        if (!ok) {
            etWalletMnemonic.setError(getString(R.string.generate_check_mnemonic));
        } else {
            etWalletMnemonic.setError(null);
        }
        return ok;
    }

    private boolean checkAddress() {
        String address = Objects.requireNonNull(etWalletAddress.getEditText()).getText().toString();
        boolean ok = Wallet.isAddressValid(address);
        if (!ok) {
            etWalletAddress.setError(getString(R.string.generate_check_address));
        } else {
            etWalletAddress.setError(null);
        }
        return ok;
    }

    private boolean checkViewKey() {
        String viewKey = Objects.requireNonNull(etWalletViewKey.getEditText()).getText().toString();
        boolean ok = (viewKey.length() == 64) && (viewKey.matches("^[0-9a-fA-F]+$"));
        if (!ok) {
            etWalletViewKey.setError(getString(R.string.generate_check_key));
        } else {
            etWalletViewKey.setError(null);
        }
        return ok;
    }

    private boolean checkSpendKey() {
        String spendKey = Objects.requireNonNull(etWalletSpendKey.getEditText()).getText().toString();
        boolean ok = ((spendKey.isEmpty()) || ((spendKey.length() == 64) && (spendKey.matches("^[0-9a-fA-F]+$"))));
        if (!ok) {
            etWalletSpendKey.setError(getString(R.string.generate_check_key));
        } else {
            etWalletSpendKey.setError(null);
        }
        return ok;
    }

    private void generateWallet() {
        if (!checkName()) return;
        if (!checkHeight()) return;

        String name = Objects.requireNonNull(etWalletName.getEditText()).getText().toString();
        String password = Objects.requireNonNull(etWalletPassword.getEditText()).getText().toString();
        boolean fingerprintAuthAllowed = sFingerprintAuth.isChecked();

        // create the real wallet password
        String crazyPass = KeyStoreHelper.getCrazyPass(getActivity(), password);

        long height = getHeight();
        if (height < 0) height = 0;

        switch (type) {
            case TYPE_NEW -> {
                bGenerate.setEnabled(false);
                if (fingerprintAuthAllowed) {
                    KeyStoreHelper.saveWalletUserPass(requireActivity(), name, password);
                }
                activityCallback.onGenerate(name, crazyPass);
            }
            case TYPE_SEED -> {
                if (!checkMnemonic()) return;
                String seed = Objects.requireNonNull(etWalletMnemonic.getEditText()).getText().toString();
                bGenerate.setEnabled(false);
                if (fingerprintAuthAllowed) {
                    KeyStoreHelper.saveWalletUserPass(requireActivity(), name, password);
                }
                activityCallback.onGenerate(name, crazyPass, seed, height);
            }
            case TYPE_LEDGER -> {
                bGenerate.setEnabled(false);
                if (fingerprintAuthAllowed) {
                    KeyStoreHelper.saveWalletUserPass(requireActivity(), name, password);
                }
                activityCallback.onGenerateLedger(name, crazyPass, height);
            }
            case TYPE_KEY, TYPE_VIEWONLY -> {
                if (checkAddress() && checkViewKey() && checkSpendKey()) {
                    bGenerate.setEnabled(false);
                    String address = Objects.requireNonNull(etWalletAddress.getEditText()).getText().toString();
                    String viewKey = Objects.requireNonNull(etWalletViewKey.getEditText()).getText().toString();
                    String spendKey = "";
                    if (type.equals(TYPE_KEY)) {
                        spendKey = Objects.requireNonNull(etWalletSpendKey.getEditText()).getText().toString();
                    }
                    if (fingerprintAuthAllowed) {
                        KeyStoreHelper.saveWalletUserPass(requireActivity(), name, password);
                    }
                    activityCallback.onGenerate(name, crazyPass, address, viewKey, spendKey, height);
                }
            }
        }
    }

    public void walletGenerateError() {
        bGenerate.setEnabled(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        Timber.d("onResume()");
        activityCallback.setTitle(getString(R.string.generate_title) + " - " + getType());
        activityCallback.setToolbarButton(Toolbar.BUTTON_BACK);

    }

    String getType() {
        return switch (type) {
            case TYPE_KEY -> getString(R.string.generate_wallet_type_key);
            case TYPE_NEW -> getString(R.string.generate_wallet_type_new);
            case TYPE_SEED -> getString(R.string.generate_wallet_type_seed);
            case TYPE_LEDGER -> getString(R.string.generate_wallet_type_ledger);
            case TYPE_VIEWONLY -> getString(R.string.generate_wallet_type_view);
            default -> {
                Timber.e("unknown type %s", type);
                yield "?";
            }
        };
    }

    GenerateFragment.Listener activityCallback;

    public interface Listener {
        void onGenerate(String name, String password);

        void onGenerate(String name, String password, String seed, long height);

        void onGenerate(String name, String password, String address, String viewKey, String spendKey, long height);

        void onGenerateLedger(String name, String password, long height);

        void setTitle(String title);

        void setToolbarButton(int type);

    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof GenerateFragment.Listener) {
            this.activityCallback = (GenerateFragment.Listener) context;
        } else {
            throw new ClassCastException(context.toString() + " must implement Listener");
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        switch (type) {
            case TYPE_KEY:
                inflater.inflate(R.menu.create_wallet_keys, menu);
                break;
            case TYPE_NEW:
                inflater.inflate(R.menu.create_wallet_new, menu);
                break;
            case TYPE_SEED:
                inflater.inflate(R.menu.create_wallet_seed, menu);
                break;
            case TYPE_LEDGER:
                inflater.inflate(R.menu.create_wallet_ledger, menu);
                break;
            case TYPE_VIEWONLY:
                inflater.inflate(R.menu.create_wallet_view, menu);
                break;
            default:
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    androidx.appcompat.app.AlertDialog ledgerDialog = null;

    public void convertLedgerSeed() {
        if (ledgerDialog != null) return;
        final Activity activity = getActivity();
        View promptsView = getLayoutInflater().inflate(R.layout.prompt_ledger_seed, null);
        assert activity != null;
        MaterialAlertDialogBuilder alertDialogBuilder = new MaterialAlertDialogBuilder(activity, R.style.MaterialAlertDialogCustom);
        alertDialogBuilder.setView(promptsView);

        final TextInputLayout etSeed = promptsView.findViewById(R.id.etSeed);
        final TextInputLayout etPassphrase = promptsView.findViewById(R.id.etPassphrase);

        Objects.requireNonNull(etSeed.getEditText()).addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (etSeed.getError() != null) {
                    etSeed.setError(null);
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {
            }
        });

        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton(getString(R.string.label_ok), null)
                .setNegativeButton(getString(R.string.label_cancel),
                        (dialog, id) -> {
                            Helper.hideKeyboardAlways(activity);
                            Objects.requireNonNull(etWalletMnemonic.getEditText()).getText().clear();
                            dialog.cancel();
                            ledgerDialog = null;
                        });

        ledgerDialog = alertDialogBuilder.create();

        ledgerDialog.setOnShowListener(dialog -> {
            Button button = ledgerDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            button.setOnClickListener(view -> {
                String ledgerSeed = etSeed.getEditText().getText().toString();
                String ledgerPassphrase = Objects.requireNonNull(etPassphrase.getEditText()).getText().toString();
                String scalaSeed = Scala.convert(ledgerSeed, ledgerPassphrase);
                if (scalaSeed != null) {
                    Objects.requireNonNull(etWalletMnemonic.getEditText()).setText(scalaSeed);
                    ledgerDialog.dismiss();
                    ledgerDialog = null;
                } else {
                    etSeed.setError(getString(R.string.bad_ledger_seed));
                }
            });
        });

        if (Helper.preventScreenshot()) {
            Objects.requireNonNull(ledgerDialog.getWindow()).setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }

        ledgerDialog.show();
    }
}
