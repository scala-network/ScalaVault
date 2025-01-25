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
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.textfield.TextInputLayout;
import androidx.fragment.app.Fragment;
import androidx.core.content.FileProvider;
import androidx.core.view.MenuItemCompat;
import androidx.appcompat.widget.ShareActionProvider;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import io.scalaproject.vault.data.BarcodeData;
import io.scalaproject.vault.ledger.LedgerProgressDialog;
import io.scalaproject.vault.model.Wallet;
import io.scalaproject.vault.model.WalletManager;
import io.scalaproject.vault.util.Helper;
import io.scalaproject.vault.util.ScalaThreadPoolExecutor;
import io.scalaproject.vault.widget.ExchangeView;
import io.scalaproject.vault.widget.Toolbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import timber.log.Timber;

public class ReceiveFragment extends Fragment {

    private ProgressBar pbProgress;
    private TextView tvAddressLabel;
    private TextView tvAddress;
    private TextInputLayout etNotes;
    private ExchangeView evAmount;
    private TextView tvQrCode;
    private ImageView ivQrCode;
    private ImageView ivQrCodeFull;
    private EditText etDummy;
    private ImageButton bCopyAddress;
    private Button bSubaddress;

    private Wallet wallet = null;
    private boolean isMyWallet = false;

    public interface Listener {
        void setToolbarButton(int type);

        void setTitle(String title);

        void setSubtitle(String subtitle);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_receive, container, false);

        pbProgress = view.findViewById(R.id.pbProgress);
        tvAddressLabel = view.findViewById(R.id.tvAddressLabel);
        tvAddress = view.findViewById(R.id.tvAddress);
        etNotes = view.findViewById(R.id.etNotes);
        evAmount = view.findViewById(R.id.evAmount);
        ivQrCode = view.findViewById(R.id.qrCode);
        tvQrCode = view.findViewById(R.id.tvQrCode);
        ivQrCodeFull = view.findViewById(R.id.qrCodeFull);
        etDummy = view.findViewById(R.id.etDummy);
        bCopyAddress = view.findViewById(R.id.bCopyAddress);
        bSubaddress = view.findViewById(R.id.bSubaddress);

        etDummy.setRawInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        bCopyAddress.setOnClickListener(v -> copyAddress());
        enableCopyAddress(false);

        evAmount.setOnNewAmountListener(xla -> {
            Timber.d("new amount = %s", xla);
            generateQr();
        });

        evAmount.setOnFailedExchangeListener(() -> {
            if (isAdded()) {
                clearQR();
                Toast.makeText(getActivity(), getString(R.string.message_exchange_failed), Toast.LENGTH_LONG).show();
            }
        });

        final EditText notesEdit = etNotes.getEditText();
        assert notesEdit != null;
        notesEdit.setRawInputType(InputType.TYPE_CLASS_TEXT);
        notesEdit.setOnEditorActionListener((v, actionId, event) -> {
            if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) && (event.getAction() == KeyEvent.ACTION_DOWN))
                    || (actionId == EditorInfo.IME_ACTION_DONE)) {
                generateQr();
                return true;
            }
            return false;
        });
        notesEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearQR();
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        bSubaddress.setOnClickListener(v -> {
            enableSubaddressButton(false);
            enableCopyAddress(false);

            final Runnable newAddress = this::getNewSubaddress;

            tvAddress.animate().alpha(0).setDuration(250)
                    .withEndAction(newAddress).start();
        });

        ivQrCode.setOnClickListener(v -> {
            Helper.hideKeyboard(getActivity());
            etDummy.requestFocus();
            if (qrValid) {
                ivQrCodeFull.setImageBitmap(((BitmapDrawable) ivQrCode.getDrawable()).getBitmap());
                ivQrCodeFull.setVisibility(View.VISIBLE);
            } else {
                evAmount.doExchange();
            }
        });

        ivQrCodeFull.setOnClickListener(v -> {
            ivQrCodeFull.setImageBitmap(null);
            ivQrCodeFull.setVisibility(View.GONE);
        });

        showProgress();
        clearQR();

        Bundle b = getArguments();
        assert b != null;
        String address = b.getString("address");
        String walletName = b.getString("name");
        Timber.d("%s/%s", address, walletName);
        if (address == null) {
            String path = b.getString("path");
            String password = b.getString("password");
            loadAndShow(path, password);
        } else {
            if (getActivity() instanceof GenerateReviewFragment.ListenerWithWallet) {
                wallet = ((GenerateReviewFragment.ListenerWithWallet) getActivity()).getWallet();
                show();
            } else {
                throw new IllegalStateException("no wallet info");
            }
        }

        return view;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    private ShareActionProvider shareActionProvider;

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.receive_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);

        // Locate MenuItem with ShareActionProvider
        MenuItem item = menu.findItem(R.id.menu_item_share);

        // Fetch and store ShareActionProvider
        shareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(item);

        assert shareActionProvider != null;
        shareActionProvider.setOnShareTargetSelectedListener((shareActionProvider, intent) -> {
            saveQrCode(); // save it only if we need it
            return false;
        });
    }

    private void setShareIntent() {
        if (shareActionProvider != null) {
            if (qrValid) {
                shareActionProvider.setShareIntent(getShareIntent());
            } else {
                shareActionProvider.setShareIntent(null);
            }
        }
    }

    private void saveQrCode() {
        if (!qrValid) throw new IllegalStateException("trying to save null qr code!");

        File cachePath = new File(requireActivity().getCacheDir(), "images");
        if (!cachePath.exists())
            if (!cachePath.mkdirs()) throw new IllegalStateException("cannot create images folder");
        File png = new File(cachePath, "QR.png");
        try {
            FileOutputStream stream = new FileOutputStream(png);
            Bitmap qrBitmap = ((BitmapDrawable) ivQrCode.getDrawable()).getBitmap();
            qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();
        } catch (IOException ex) {
            Timber.e(ex);
            // make sure we don't share an old qr code
            if (!png.delete()) throw new IllegalStateException("cannot delete old qr code");
            // if we manage to delete it, the URI points to nothing and the user gets a toast with the error
        }
    }

    private Intent getShareIntent() {
        File imagePath = new File(requireActivity().getCacheDir(), "images");
        File png = new File(imagePath, "QR.png");
        Uri contentUri = FileProvider.getUriForFile(requireActivity(),
                BuildConfig.APPLICATION_ID + ".fileprovider", png);
        if (contentUri != null) {
            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // temp permission for receiving app to read this file
            shareIntent.setDataAndType(contentUri, requireActivity().getContentResolver().getType(contentUri));
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.putExtra(Intent.EXTRA_TEXT, bcData.getUriString());
            return shareIntent;
        }
        return null;
    }

    void enableSubaddressButton(boolean enable) {
        bSubaddress.setEnabled(enable);
        if (enable) {
            bSubaddress.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_settings_blue_24dp, 0, 0);
        } else {
            bSubaddress.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_settings_gray_24dp, 0, 0);
        }
    }

    void copyAddress() {
        Helper.clipBoardCopy(requireActivity(), getString(R.string.label_copy_address), tvAddress.getText().toString());
        Toast.makeText(getActivity(), getString(R.string.message_copy_address), Toast.LENGTH_SHORT).show();
    }

    private boolean qrValid = false;

    void clearQR() {
        if (qrValid) {
            ivQrCode.setImageBitmap(null);
            qrValid = false;
            setShareIntent();
            if (isLoaded)
                tvQrCode.setVisibility(View.VISIBLE);
        }
    }

    void setQR(Bitmap qr) {
        ivQrCode.setImageBitmap(qr);
        qrValid = true;
        setShareIntent();
        tvQrCode.setVisibility(View.GONE);
    }

    @Override
    public void onResume() {
        super.onResume();
        Timber.d("onResume()");
        listenerCallback.setToolbarButton(Toolbar.BUTTON_BACK);
        if (wallet != null) {
            listenerCallback.setSubtitle(wallet.getAccountLabel());
            generateQr();
        } else {
            listenerCallback.setSubtitle(getString(R.string.status_wallet_loading));
            clearQR();
        }
    }

    private boolean isLoaded = false;

    private void show() {
        Timber.d("name=%s", wallet.getName());
        isLoaded = true;
        listenerCallback.setTitle(wallet.getName());
        listenerCallback.setSubtitle(wallet.getAccountLabel());
        tvAddress.setText(wallet.getAddress());
        enableCopyAddress(true);
        hideProgress();
        generateQr();
    }

    private void enableCopyAddress(boolean enable) {
        bCopyAddress.setClickable(enable);
        if (enable)
            bCopyAddress.setImageResource(R.drawable.ic_content_copy_24dp);
        else
            bCopyAddress.setImageResource(R.drawable.ic_content_nocopy_black_24dp);
    }

    private void loadAndShow(String walletPath, String password) {
        new AsyncShow(walletPath, password).executeOnExecutor(ScalaThreadPoolExecutor.SCALA_THREAD_POOL_EXECUTOR);
    }

    GenerateReviewFragment.ProgressListener progressCallback = null;

    @SuppressLint("StaticFieldLeak")
    private class AsyncShow extends AsyncTask<Void, Void, Boolean> {
        final private String walletPath;
        final private String password;


        AsyncShow(String walletPath, String passsword) {
            super();
            this.walletPath = walletPath;
            this.password = passsword;
        }

        boolean dialogOpened = false;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showProgress();
            if ((walletPath != null)
                    && (WalletManager.getInstance().queryWalletDevice(walletPath + ".keys", password)
                    == Wallet.Device.Device_Ledger)
                    && (progressCallback != null)) {
                progressCallback.showLedgerProgressDialog(LedgerProgressDialog.TYPE_RESTORE);
                dialogOpened = true;
            }
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            if (params.length != 0) return false;
            wallet = WalletManager.getInstance().openWallet(walletPath, password);
            isMyWallet = true;
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (dialogOpened)
                progressCallback.dismissProgressDialog();
            if (!isAdded()) return; // never mind
            if (result) {
                show();
            } else {
                Toast.makeText(getActivity(), getString(R.string.receive_cannot_open), Toast.LENGTH_LONG).show();
                hideProgress();
            }
        }
    }

    private void storeWallet() {
        new AsyncStore().executeOnExecutor(ScalaThreadPoolExecutor.SCALA_THREAD_POOL_EXECUTOR);
    }

    @SuppressLint("StaticFieldLeak")
    private class AsyncStore extends AsyncTask<String, Void, Boolean> {

        @Override
        protected Boolean doInBackground(String... params) {
            if (params.length != 0) return false;
            if (wallet != null) wallet.store();
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            enableSubaddressButton(true);
            super.onPostExecute(result);
        }
    }

    public BarcodeData getBarcodeData() {
        if (qrValid)
            return bcData;
        else
            return null;
    }

    private BarcodeData bcData = null;

    private void generateQr() {
        Timber.d("GENQR");
        String address = tvAddress.getText().toString();
        String notes = Objects.requireNonNull(etNotes.getEditText()).getText().toString();
        String xlaAmount = evAmount.getAmount();
        Timber.d("%s/%s/%s", xlaAmount, notes, address);
        if ((xlaAmount == null) || !Wallet.isAddressValid(address)) {
            clearQR();
            Timber.d("CLEARQR");
            return;
        }
        bcData = new BarcodeData(BarcodeData.Asset.XLA, address, null, notes, xlaAmount);
        int size = Math.max(ivQrCode.getWidth(), ivQrCode.getHeight());
        Bitmap qr = generate(bcData.getUriString(), size, size);
        if (qr != null) {
            setQR(qr);
            Timber.d("SETQR");
            etDummy.requestFocus();
            Helper.hideKeyboard(getActivity());
        }
    }

    public Bitmap generate(String text, int width, int height) {
        if ((width <= 0) || (height <= 0)) return null;
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "utf-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        try {
            BitMatrix bitMatrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, width, height, hints);
            int[] pixels = new int[width * height];
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    if (bitMatrix.get(j, i)) {
                        pixels[i * width + j] = 0x00000000;
                    } else {
                        pixels[i * height + j] = 0xffffffff;
                    }
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.RGB_565);
            bitmap = addLogo(bitmap);
            return bitmap;
        } catch (WriterException ex) {
            Timber.e(ex);
        }
        return null;
    }

    private Bitmap addLogo(Bitmap qrBitmap) {
        Bitmap logo = getScalaLogo();
        int qrWidth = qrBitmap.getWidth();
        int qrHeight = qrBitmap.getHeight();
        int logoWidth = logo.getWidth();
        int logoHeight = logo.getHeight();

        Bitmap logoBitmap = Bitmap.createBitmap(qrWidth, qrHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(logoBitmap);
        canvas.drawBitmap(qrBitmap, 0, 0, null);
        canvas.save();
        // figure out how to scale the logo
        float scaleSize = 1.0f;
        while ((logoWidth / scaleSize) > ((float) qrWidth / 5) || (logoHeight / scaleSize) > ((float) qrHeight / 5)) {
            scaleSize *= 2;
        }
        float sx = 1.0f / scaleSize;
        canvas.scale(sx, sx, (float) qrWidth / 2, (float) qrHeight / 2);
        canvas.drawBitmap(logo, (float) (qrWidth - logoWidth) / 2, (float) (qrHeight - logoHeight) / 2, null);
        canvas.restore();
        return logoBitmap;
    }

    private Bitmap logo = null;

    private Bitmap getScalaLogo() {
        if (logo == null) {
            logo = Helper.getBitmap(getContext(), R.drawable.ic_scala_logo_b);
        }
        return logo;
    }

    public void showProgress() {
        pbProgress.setVisibility(View.VISIBLE);
    }

    public void hideProgress() {
        pbProgress.setVisibility(View.GONE);
    }

    Listener listenerCallback = null;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Listener) {
            this.listenerCallback = (Listener) context;
        } else {
            throw new ClassCastException(context.toString()
                    + " must implement Listener");
        }
        if (context instanceof GenerateReviewFragment.ProgressListener) {
            this.progressCallback = (GenerateReviewFragment.ProgressListener) context;
        }
    }

    @Override
    public void onPause() {
        Timber.d("onPause()");
        Helper.hideKeyboard(getActivity());
        super.onPause();
    }

    @Override
    public void onDetach() {
        Timber.d("onDetach()");
        if ((wallet != null) && (isMyWallet)) {
            wallet.close();
            wallet = null;
            isMyWallet = false;
        }
        super.onDetach();
    }

    private void getNewSubaddress() {
        new AsyncSubaddress().executeOnExecutor(ScalaThreadPoolExecutor.SCALA_THREAD_POOL_EXECUTOR);
    }

    @SuppressLint("StaticFieldLeak")
    private class AsyncSubaddress extends AsyncTask<Void, Void, Boolean> {
        private String newSubaddress;

        boolean dialogOpened = false;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if ((wallet.getDeviceType() == Wallet.Device.Device_Ledger) && (progressCallback != null)) {
                progressCallback.showLedgerProgressDialog(LedgerProgressDialog.TYPE_SUBADDRESS);
                dialogOpened = true;
            }
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            if (params.length != 0) return false;
            newSubaddress = wallet.getNewSubaddress();
            storeWallet();
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (dialogOpened)
                progressCallback.dismissProgressDialog();
            tvAddress.setText(newSubaddress);
            tvAddressLabel.setText(getString(R.string.generate_address_label_sub,
                    wallet.getNumSubaddresses() - 1));
            generateQr();
            enableCopyAddress(true);
            final Runnable resetSize = () -> tvAddress.animate().setDuration(125).scaleX(1).scaleY(1).start();
            tvAddress.animate().alpha(1).setDuration(125)
                    .scaleX(1.2f).scaleY(1.2f)
                    .withEndAction(resetSize).start();
        }
    }
}
