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

package io.scalaproject.vault.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.hardware.fingerprint.FingerprintManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.StrictMode;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import androidx.core.content.ContextCompat;
import android.system.ErrnoException;
import android.system.Os;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;

import io.scalaproject.vault.BuildConfig;
import io.scalaproject.vault.R;
import io.scalaproject.vault.model.NetworkType;
import io.scalaproject.vault.model.WalletManager;
import io.scalaproject.vault.service.exchange.api.ExchangeApi;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.HttpsURLConnection;

import okhttp3.HttpUrl;
import timber.log.Timber;

public class Helper {
    static public final String NOCRAZYPASS_FLAGFILE = ".nocrazypass";

    static public final String BASE_CRYPTO = "XLA";

    static private final String WALLET_DIR = "wallets";
    static private final String SCALA_DIR = "scala";

    static public int DISPLAY_DIGITS_INFO = 5;

    static public File getWalletRoot(Context context) {
        return getStorage(context, WALLET_DIR);
    }

    static public File getStorage(Context context, String folderName) {
        File dir = new File(context.getFilesDir(), folderName);
        if (!dir.exists()) {
            Timber.i("Creating %s", dir.getAbsolutePath());
            dir.mkdirs(); // try to make it
        }
        if (!dir.isDirectory()) {
            String msg = "Directory " + dir.getAbsolutePath() + " does not exist.";
            Timber.e(msg);
            throw new IllegalStateException(msg);
        }
        return dir;
    }

    static public final int PERMISSIONS_REQUEST_CAMERA = 7;
    static public final int PERMISSIONS_REQUEST_READ_IMAGES = 8;

    static public boolean getCameraPermission(Activity context) {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
            Timber.w("Permission denied for CAMERA - requesting it");
            String[] permissions = {Manifest.permission.CAMERA};
            context.requestPermissions(permissions, PERMISSIONS_REQUEST_CAMERA);
            return false;
        } else {
            return true;
        }
    }

    static public boolean getReadExternalStoragePermission(Activity context) {
        String perm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (context.checkSelfPermission(perm) == PackageManager.PERMISSION_DENIED) {
            Timber.w("Permission denied for READ_EXTERNAL_STORAGE - requesting it");
            String[] permissions = {perm};
            context.requestPermissions(permissions, PERMISSIONS_REQUEST_READ_IMAGES);
            return false;
        } else {
            return true;
        }
    }

    static public File getWalletFile(Context context, String aWalletName) {
        File walletDir = getWalletRoot(context);
        File f = new File(walletDir, aWalletName);
        Timber.d("wallet=%s size= %d", f.getAbsolutePath(), f.length());
        return f;
    }

    static public void showKeyboard(Activity act) {
        if (act == null) return;
        if (act.getCurrentFocus() == null) {
            act.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        } else {
            InputMethodManager imm = (InputMethodManager) act.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm == null) return;
            imm.showSoftInput(act.getCurrentFocus(), InputMethodManager.SHOW_IMPLICIT);
        }
    }

    static public void hideKeyboard(Activity act) {
        if (act == null) return;
        if (act.getCurrentFocus() == null) {
            act.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        } else {
            InputMethodManager imm = (InputMethodManager) act.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm == null) return;
            imm.hideSoftInputFromWindow((null == act.getCurrentFocus()) ? null : act.getCurrentFocus().getWindowToken(),
                    InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    static public void showKeyboard(Dialog dialog) {
        Objects.requireNonNull(dialog.getWindow()).setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    static public void hideKeyboardAlways(Activity act) {
        act.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    static public BigDecimal getDecimalAmount(long amount) {
        return new BigDecimal(amount).scaleByPowerOfTen(-2);
    }

    static public String getDisplayAmount(long amount) {
        return getDisplayAmount(amount, 12);
    }

    static public String getDisplayAmount(long amount, int maxDecimals) {
        // a Java bug does not strip zeros properly if the value is 0
        if (amount == 0) return "0.00";
        BigDecimal d = getDecimalAmount(amount)
                .setScale(maxDecimals, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        if (d.scale() < 2)
            d = d.setScale(2, RoundingMode.UNNECESSARY);
        return d.toPlainString();
    }

    static public String getFormattedAmount(double amount, boolean isCrypto) {
        // at this point selection is XLA in case of error
        String displayB;
        if (isCrypto) {
            if (amount >= 0) {
                displayB = String.format(Locale.US, "%,.2f", amount);
            } else {
                displayB = null;
            }
        } else { // not crypto
            displayB = String.format(Locale.US, "%,.2f", amount);
        }
        return displayB;
    }

    // min 2 significant digits after decimal point
    static public String getFormattedAmount(double amount) {
        if ((amount >= 1.0d) || (amount == 0))
            return String.format(Locale.US, "%,.2f", amount);
        else { // amount < 1
            int decimals = 1 - (int) Math.floor(Math.log10(amount));
            if (decimals < 2) decimals = 2;
            if (decimals > 12) decimals = 12;
            // Create the format string
            String formatString = "%,." + decimals + "f";
            return String.format(Locale.US, formatString, amount);
        }
    }


    static public Bitmap getBitmap(Context context, int drawableId) {
        Drawable drawable = ContextCompat.getDrawable(context, drawableId);
        if (drawable instanceof BitmapDrawable) {
            return BitmapFactory.decodeResource(context.getResources(), drawableId);
        } else if (drawable instanceof VectorDrawable) {
            return getBitmap((VectorDrawable) drawable);
        } else {
            throw new IllegalArgumentException("unsupported drawable type");
        }
    }

    static public Bitmap getCroppedBitmap(Bitmap bitmap) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawCircle((float) bitmap.getWidth() / 2, (float) bitmap.getHeight() / 2, (float) bitmap.getWidth() / 2, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);

        return output;
    }

    static private Bitmap getBitmap(VectorDrawable vectorDrawable) {
        Bitmap bitmap = Bitmap.createBitmap(vectorDrawable.getIntrinsicWidth(),
                vectorDrawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        vectorDrawable.draw(canvas);
        return bitmap;
    }

    static final int HTTP_TIMEOUT = 5000;

    static public String getUrl(String httpsUrl) {
        HttpsURLConnection urlConnection = null;
        try {
            URL url = new URL(httpsUrl);
            urlConnection = (HttpsURLConnection) url.openConnection();
            urlConnection.setConnectTimeout(HTTP_TIMEOUT);
            urlConnection.setReadTimeout(HTTP_TIMEOUT);
            InputStreamReader in = new InputStreamReader(urlConnection.getInputStream());
            StringBuilder sb = new StringBuilder();
            final int BUFFER_SIZE = 512;
            char[] buffer = new char[BUFFER_SIZE];
            int length = in.read(buffer, 0, BUFFER_SIZE);
            while (length >= 0) {
                sb.append(buffer, 0, length);
                length = in.read(buffer, 0, BUFFER_SIZE);
            }
            return sb.toString();
        } catch (SocketTimeoutException ex) {
            Timber.w("C %s", ex.getLocalizedMessage());
        } catch (MalformedURLException ex) {
            Timber.e("A %s", ex.getLocalizedMessage());
        } catch (IOException ex) {
            Timber.e("B %s", ex.getLocalizedMessage());
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
        return null;
    }

    static public void clipBoardCopy(Context context, String label, String text) {
        ClipboardManager clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboardManager.setPrimaryClip(clip);
    }

    static public String getClipBoardText(Context context) {
        final ClipboardManager clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        try {
            if (clipboardManager.hasPrimaryClip()
                    && Objects.requireNonNull(clipboardManager.getPrimaryClipDescription()).hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                final ClipData.Item item = Objects.requireNonNull(clipboardManager.getPrimaryClip()).getItemAt(0);
                return item.getText().toString();
            }
        } catch (NullPointerException ex) {
            // if we have don't find a text in the clipboard
            return null;
        }
        return null;
    }

    static private volatile Animation ShakeAnimation;

    static public Animation getShakeAnimation(Context context) {
        if (ShakeAnimation == null) {
            synchronized (Helper.class) {
                if (ShakeAnimation == null) {
                    ShakeAnimation = AnimationUtils.loadAnimation(context, R.anim.shake);
                }
            }
        }
        return ShakeAnimation;
    }

    // Domain xla.to don't exist can be register
    // TODO Fix this function
    static public HttpUrl getxlaToBaseUrl() {
        if ((WalletManager.getInstance() == null) || (WalletManager.getInstance().getNetworkType() != NetworkType.NetworkType_Mainnet)) {
            return HttpUrl.parse("https://test.xla.to/api/v3/xla2btc/");
        } else {
            return HttpUrl.parse("https://xla.to/api/v3/xla2btc/");
        }
    }

    private final static char[] HexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] data) {
        if ((data != null) && (data.length > 0))
            return String.format("%0" + (data.length * 2) + "X", new BigInteger(1, data));
        else return "";
    }

    public static byte[] hexToBytes(String hex) {
        final int len = hex.length();
        final byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    static public void setScalaHome(Context context) {
        try {
            String home = getStorage(context, SCALA_DIR).getAbsolutePath();
            Os.setenv("HOME", home, true);
        } catch (ErrnoException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static public void initLogger(Context context) {
        if (BuildConfig.DEBUG) {
            initLogger(context, WalletManager.LOGLEVEL_DEBUG);
        }
        // no logger if not debug
    }

    // TODO make the log levels refer to the  WalletManagerFactory::LogLevel enum ?
    static public void initLogger(Context context, int level) {
        String home = getStorage(context, SCALA_DIR).getAbsolutePath();
        WalletManager.initLogger(home + "/monerujo", "monerujo.log");
        if (level >= WalletManager.LOGLEVEL_SILENT)
            WalletManager.setLogLevel(level);
    }

    static public boolean useCrazyPass(Context context) {
        // Remove for now - sometimes it messes up the password
        /*File flagFile = new File(getWalletRoot(context), NOCRAZYPASS_FLAGFILE);
        return !flagFile.exists();*/
        return false;
    }

    // try to figure out what the real wallet password is given the user password
    // which could be the actual wallet password or a (maybe malformed) CrAzYpass
    // or the password used to derive the CrAzYpass for the wallet
    static public String getWalletPassword(Context context, String walletName, String password) {
        String walletPath = new File(getWalletRoot(context), walletName + ".keys").getAbsolutePath();

        // try with entered password (which could be a legacy password or a CrAzYpass)
        if (WalletManager.getInstance().verifyWalletPasswordOnly(walletPath, password)) {
            return password;
        }

        // maybe this is a malformed CrAzYpass?
        String possibleCrazyPass = CrazyPassEncoder.reformat(password);
        if (possibleCrazyPass != null) { // looks like a CrAzYpass
            if (WalletManager.getInstance().verifyWalletPasswordOnly(walletPath, possibleCrazyPass)) {
                return possibleCrazyPass;
            }
        }

        // generate & try with CrAzYpass
        String crazyPass = KeyStoreHelper.getCrazyPass(context, password);
        if (WalletManager.getInstance().verifyWalletPasswordOnly(walletPath, crazyPass)) {
            return crazyPass;
        }

        // or maybe it is a broken CrAzYpass? (of which we have two variants)
        String brokenCrazyPass2 = KeyStoreHelper.getBrokenCrazyPass(context, password, 2);
        if ((brokenCrazyPass2 != null)
                && WalletManager.getInstance().verifyWalletPasswordOnly(walletPath, brokenCrazyPass2)) {
            return brokenCrazyPass2;
        }
        String brokenCrazyPass1 = KeyStoreHelper.getBrokenCrazyPass(context, password, 1);
        if ((brokenCrazyPass1 != null)
                && WalletManager.getInstance().verifyWalletPasswordOnly(walletPath, brokenCrazyPass1)) {
            return brokenCrazyPass1;
        }

        return null;
    }

    static androidx.appcompat.app.AlertDialog openDialog = null; // for preventing opening of multiple dialogs
    static AsyncTask<Void, Void, Boolean> loginTask = null;

    static public void promptPassword(final Context context, final String wallet, boolean fingerprintDisabled, final PasswordAction action) {
        if (openDialog != null) return; // we are already asking for password
        LayoutInflater li = LayoutInflater.from(context);
        final View promptsView = li.inflate(R.layout.prompt_password, null);

        MaterialAlertDialogBuilder alertDialogBuilder = new MaterialAlertDialogBuilder(context, R.style.MaterialAlertDialogCustom);
        alertDialogBuilder.setView(promptsView);

        final TextInputLayout etPassword = promptsView.findViewById(R.id.etPassword);
        etPassword.setHint(context.getString(R.string.prompt_password, wallet));

        final TextView tvOpenPrompt = promptsView.findViewById(R.id.tvOpenPrompt);
        @SuppressLint("UseCompatLoadingForDrawables")
        final Drawable icFingerprint = context.getDrawable(R.drawable.ic_fingerprint);
        @SuppressLint("UseCompatLoadingForDrawables")
        final Drawable icError = context.getDrawable(R.drawable.ic_error_red_36dp);
        @SuppressLint("UseCompatLoadingForDrawables")
        final Drawable icInfo = context.getDrawable(R.drawable.ic_info_green);

        final boolean fingerprintAuthCheck = FingerprintHelper.isFingerPassValid(context, wallet);

        final boolean fingerprintAuthAllowed = !fingerprintDisabled && fingerprintAuthCheck;
        final CancellationSignal cancelSignal = new CancellationSignal();

        final AtomicBoolean incorrectSavedPass = new AtomicBoolean(false);

        class LoginWalletTask extends AsyncTask<Void, Void, Boolean> {
            private final String pass;
            private final boolean fingerprintUsed;

            LoginWalletTask(String pass, boolean fingerprintUsed) {
                this.pass = pass;
                this.fingerprintUsed = fingerprintUsed;
            }

            @Override
            protected void onPreExecute() {
                tvOpenPrompt.setCompoundDrawablesRelativeWithIntrinsicBounds(icInfo, null, null, null);
                tvOpenPrompt.setText(context.getText(R.string.prompt_open_wallet));
                tvOpenPrompt.setVisibility(View.VISIBLE);
            }

            @Override
            protected Boolean doInBackground(Void... unused) {
                return processPasswordEntry(context, wallet, pass, fingerprintUsed, action);
            }

            @Override
            protected void onPostExecute(Boolean result) {
                if (result) {
                    Helper.hideKeyboardAlways((Activity) context);
                    cancelSignal.cancel();
                    openDialog.dismiss();
                    openDialog = null;
                } else {
                    if (fingerprintUsed) {
                        incorrectSavedPass.set(true);
                        tvOpenPrompt.setCompoundDrawablesRelativeWithIntrinsicBounds(icError, null, null, null);
                        tvOpenPrompt.setText(context.getText(R.string.bad_saved_password));
                    } else {
                        if (!fingerprintAuthAllowed) {
                            tvOpenPrompt.setVisibility(View.GONE);
                        } else if (incorrectSavedPass.get()) {
                            tvOpenPrompt.setCompoundDrawablesRelativeWithIntrinsicBounds(icError, null, null, null);
                            tvOpenPrompt.setText(context.getText(R.string.bad_password));
                        } else {
                            tvOpenPrompt.setCompoundDrawablesRelativeWithIntrinsicBounds(icFingerprint, null, null, null);
                            tvOpenPrompt.setText(context.getText(R.string.prompt_fingerprint_auth));
                        }
                        etPassword.setError(context.getString(R.string.bad_password));
                    }
                }
                loginTask = null;
            }
        }

        Objects.requireNonNull(etPassword.getEditText()).addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {
                if (etPassword.getError() != null) {
                    etPassword.setError(null);
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start,  int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }
        });

        // set dialog message
        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton(context.getString(R.string.label_ok), null)
                .setNegativeButton(context.getString(R.string.label_cancel),
                        (dialog, id) -> {
                            Helper.hideKeyboardAlways((Activity) context);
                            cancelSignal.cancel();
                            if (loginTask != null) {
                                loginTask.cancel(true);
                                loginTask = null;
                            }
                            dialog.cancel();
                            openDialog = null;
                        });

        openDialog = alertDialogBuilder.create();

        final FingerprintManager.AuthenticationCallback fingerprintAuthCallback;
        fingerprintAuthCallback = new FingerprintManager.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errMsgId, CharSequence errString) {
                tvOpenPrompt.setCompoundDrawablesRelativeWithIntrinsicBounds(icError, null, null, null);
                tvOpenPrompt.setText(errString);
            }

            @Override
            public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
                tvOpenPrompt.setCompoundDrawablesRelativeWithIntrinsicBounds(icError, null, null, null);
                tvOpenPrompt.setText(helpString);
            }

            @Override
            public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
                try {
                    String userPass = KeyStoreHelper.loadWalletUserPass(context, wallet);
                    if (loginTask == null) {
                        loginTask = new LoginWalletTask(userPass, true);
                        loginTask.execute();
                    }
                } catch (KeyStoreHelper.BrokenPasswordStoreException ex) {
                    etPassword.setError(context.getString(R.string.bad_password));
                    // TODO: better error message here - what would it be?
                }
            }

            @Override
            public void onAuthenticationFailed() {
                tvOpenPrompt.setCompoundDrawablesRelativeWithIntrinsicBounds(icError, null, null, null);
                tvOpenPrompt.setText(context.getString(R.string.bad_fingerprint));
            }
        };

        openDialog.setOnShowListener(dialog -> {
            if (fingerprintAuthAllowed) {
                tvOpenPrompt.setCompoundDrawablesRelativeWithIntrinsicBounds(icFingerprint, null, null, null);
                tvOpenPrompt.setText(context.getText(R.string.prompt_fingerprint_auth));
                tvOpenPrompt.setVisibility(View.VISIBLE);
                FingerprintHelper.authenticate(context, cancelSignal, fingerprintAuthCallback);
            } else {
                etPassword.requestFocus();
            }
            Button posButton = openDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            posButton.setOnClickListener(view -> {
                String pass = etPassword.getEditText().getText().toString();
                if (loginTask == null) {
                    loginTask = new LoginWalletTask(pass, false);
                    loginTask.execute();
                }
            });
        });

        // accept keyboard "ok"
        etPassword.getEditText().setOnEditorActionListener((v, actionId, event) -> {
            if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) && (event.getAction() == KeyEvent.ACTION_DOWN))
                    || (actionId == EditorInfo.IME_ACTION_DONE)) {
                String pass = etPassword.getEditText().getText().toString();
                if (loginTask == null) {
                    loginTask = new LoginWalletTask(pass, false);
                    loginTask.execute();
                }
                return true;
            }
            return false;
        });

        if (Helper.preventScreenshot()) {
            Objects.requireNonNull(openDialog.getWindow()).setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }

        Helper.showKeyboard(openDialog);
        openDialog.show();
    }

    public interface PasswordAction {
        void action(String walletName, String password, boolean fingerprintUsed);
    }

    static private boolean processPasswordEntry(Context context, String walletName, String pass, boolean fingerprintUsed, PasswordAction action) {
        String walletPassword = Helper.getWalletPassword(context, walletName, pass);
        if (walletPassword != null) {
            action.action(walletName, walletPassword, fingerprintUsed);
            return true;
        } else {
            return false;
        }
    }

    static public ExchangeApi getExchangeApi() {
        // This returns ecb first?
        return new io.scalaproject.vault.service.exchange.main.ExchangeApiImpl(OkHttpHelper.getOkHttpClient());
    }

    public interface Action {
        boolean run();
    }

    static public boolean runWithNetwork(Action action) {
        StrictMode.ThreadPolicy currentPolicy = StrictMode.getThreadPolicy();
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitNetwork().build();
        StrictMode.setThreadPolicy(policy);
        try {
            return action.run();
        } finally {
            StrictMode.setThreadPolicy(currentPolicy);
        }
    }

    static public boolean preventScreenshot() {
        return false;
        //return !(BuildConfig.DEBUG || BuildConfig.FLAVOR_type.equals("alpha"));
    }

    static public String getPrettyAddress(String text) {
        return text.substring(0, 7) + "..." + text.substring(text.length() - 7);
    }

    static public String getTruncatedString(String text, Integer max) {
        if(text.length() <= max)
            return text;

        return text.substring(0, 16).trim() + "...";
    }

    static public boolean isURLReachable(String strURL) {
        try {
            URL url = new URL(strURL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setReadTimeout(2000);
            return connection.getResponseCode() == 200;
        } catch (java.net.SocketTimeoutException e) {
            return false;
        } catch (IOException e) {
            //e.printStackTrace();
            return false;
        }
    }

    public static String fetchJSON(String url) {

        StringBuilder data = new StringBuilder();
        try {
            URL urlFetch = new URL(url);
            HttpURLConnection httpURLConnection = (HttpURLConnection) urlFetch.openConnection();
            InputStream inputStream = httpURLConnection.getInputStream();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                data.append(line);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return data.toString();
    }
}
