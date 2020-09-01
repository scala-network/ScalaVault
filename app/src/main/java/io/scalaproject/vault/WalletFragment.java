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

import android.content.Context;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.github.brnunes.swipeablerecyclerview.SwipeableRecyclerViewTouchListener;
import io.scalaproject.vault.layout.TransactionInfoAdapter;
import io.scalaproject.vault.model.TransactionInfo;
import io.scalaproject.vault.model.Wallet;
import io.scalaproject.vault.service.exchange.api.ExchangeApi;
import io.scalaproject.vault.service.exchange.api.ExchangeCallback;
import io.scalaproject.vault.service.exchange.api.ExchangeRate;
import io.scalaproject.vault.util.Helper;
import io.scalaproject.vault.widget.Toolbar;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import timber.log.Timber;

public class WalletFragment extends Fragment
        implements TransactionInfoAdapter.OnInteractionListener {
    private TransactionInfoAdapter txInfoAdapter;
    private NumberFormat formatter = NumberFormat.getInstance();

    private TextView tvStealthMode;
    private LinearLayout llBalance, llWalletAddress;
    private FrameLayout flExchange;
    private TextView tvBalance, tvUnconfirmedAmount, tvAddressType;
    private TextView tvProgress, tvWalletName, tvWalletAddress;
    private ImageView ivSynced, ivAddressType;
    private ProgressBar pbProgress;
    private Button bReceive, bSend;
    private TextView tvNoTransaction;
    private RecyclerView rvTransactions;

    private Spinner sCurrency;

    private List<String> dismissedTransactions = new ArrayList<>();

    public void resetDismissedTransactions() {
        dismissedTransactions.clear();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (activityCallback.hasWallet()) {
            inflater.inflate(R.menu.wallet_menu, menu);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_wallet, container, false);

        tvStealthMode = view.findViewById(R.id.tvStreetView);
        llBalance = view.findViewById(R.id.llBalance);
        flExchange = view.findViewById(R.id.flExchange);
        ((ProgressBar) view.findViewById(R.id.pbExchange)).getIndeterminateDrawable().
                setColorFilter(getResources().getColor(R.color.trafficGray),
                        android.graphics.PorterDuff.Mode.MULTIPLY);

        tvWalletName = view.findViewById(R.id.tvWalletName);
        tvWalletAddress = view.findViewById(R.id.tvWalletAddress);
        tvProgress = view.findViewById(R.id.tvProgress);
        pbProgress = view.findViewById(R.id.pbProgress);
        tvBalance = view.findViewById(R.id.tvBalance);
        showBalance(Helper.getDisplayAmount(0));

        tvUnconfirmedAmount = view.findViewById(R.id.tvUnconfirmedAmount);
        showUnconfirmed(0);

        ivSynced = view.findViewById(R.id.ivSynced);

        llWalletAddress = view.findViewById(R.id.llWalletAddress);
        llWalletAddress.setVisibility(View.INVISIBLE);

        tvAddressType = view.findViewById(R.id.tvAddressType);
        ivAddressType = view.findViewById(R.id.ivAddressType);

        tvNoTransaction = view.findViewById(R.id.tvNoTransaction);
        rvTransactions = view.findViewById(R.id.rvTransactions);

        sCurrency = view.findViewById(R.id.sCurrency);
        List<String> currencies = new ArrayList<>();
        currencies.add(Helper.BASE_CRYPTO);
        currencies.addAll(Arrays.asList(getResources().getStringArray(R.array.currency)));
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(getContext(), R.layout.item_spinner_balance, currencies);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sCurrency.setAdapter(spinnerAdapter);

        bSend = view.findViewById(R.id.bSend);
        bReceive = view.findViewById(R.id.bReceive);

        txInfoAdapter = new TransactionInfoAdapter(getActivity(), this);
        rvTransactions.setAdapter(txInfoAdapter);

        SwipeableRecyclerViewTouchListener swipeTouchListener =
                new SwipeableRecyclerViewTouchListener(rvTransactions,
                        new SwipeableRecyclerViewTouchListener.SwipeListener() {
                            @Override
                            public boolean canSwipeLeft(int position) {
                                return activityCallback.isStealthMode();
                            }

                            @Override
                            public boolean canSwipeRight(int position) {
                                return activityCallback.isStealthMode();
                            }

                            @Override
                            public void onDismissedBySwipeLeft(RecyclerView recyclerView, int[] reverseSortedPositions) {
                                for (int position : reverseSortedPositions) {
                                    dismissedTransactions.add(txInfoAdapter.getItem(position).hash);
                                    txInfoAdapter.removeItem(position);
                                }
                                txInfoAdapter.notifyDataSetChanged();
                            }

                            @Override
                            public void onDismissedBySwipeRight(RecyclerView recyclerView, int[] reverseSortedPositions) {
                                for (int position : reverseSortedPositions) {
                                    dismissedTransactions.add(txInfoAdapter.getItem(position).hash);
                                    txInfoAdapter.removeItem(position);
                                }
                                txInfoAdapter.notifyDataSetChanged();
                            }
                        });

        rvTransactions.addOnItemTouchListener(swipeTouchListener);

        bSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activityCallback.onSendRequest();
            }
        });
        bReceive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activityCallback.onWalletReceive();
            }
        });

        sCurrency.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                refreshBalance();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // nothing (yet?)
            }
        });

        if (activityCallback.isSynced()) {
            onSynced();
        }

        activityCallback.forceUpdate();

        return view;
    }

    void showBalance(String balance) {
        tvBalance.setText(balance);
        if (!activityCallback.isStealthMode()) {
            llBalance.setVisibility(View.VISIBLE);
            tvStealthMode.setVisibility(View.INVISIBLE);
        } else {
            llBalance.setVisibility(View.INVISIBLE);
            tvStealthMode.setVisibility(View.VISIBLE);
        }
    }

    void showUnconfirmed(double unconfirmedAmount) {
        if (!activityCallback.isStealthMode() && unconfirmedAmount > 0.0) {
            tvUnconfirmedAmount.setVisibility(View.VISIBLE);
            String unconfirmed = Helper.getFormattedAmount(unconfirmedAmount, true);
            tvUnconfirmedAmount.setText(getResources().getString(R.string.xla_unconfirmed_amount, unconfirmed));
        } else {
            tvUnconfirmedAmount.setVisibility(View.GONE);
        }
    }

    void updateBalance() {
        if (isExchanging) return; // wait for exchange to finish - it will fire this itself then.
        // at this point selection is XLA in case of error
        String displayB;
        double amountA = Helper.getDecimalAmount(unlockedBalance).doubleValue();
        if (!Helper.BASE_CRYPTO.equals(balanceCurrency)) { // not XLA
            double amountB = amountA * balanceRate;
            displayB = Helper.getFormattedAmount(amountB, false);
        } else { // XLA
            displayB = Helper.getFormattedAmount(amountA, true);
        }
        showBalance(displayB);
    }

    String balanceCurrency = Helper.BASE_CRYPTO;
    double balanceRate = 1.0;

    private final ExchangeApi exchangeApi = Helper.getExchangeApi();

    void refreshBalance() {
        double unconfirmedxla = Helper.getDecimalAmount(balance - unlockedBalance).doubleValue();
        showUnconfirmed(unconfirmedxla);
        if (sCurrency.getSelectedItemPosition() == 0) { // XLA
            double amountxla = Helper.getDecimalAmount(unlockedBalance).doubleValue();
            showBalance(Helper.getFormattedAmount(amountxla, true));
        } else { // not XLA
            String currency = (String) sCurrency.getSelectedItem();
            Timber.d(currency);
            if (!currency.equals(balanceCurrency) || (balanceRate <= 0)) {
                showExchanging();
                exchangeApi.queryExchangeRate(Helper.BASE_CRYPTO, currency,
                        new ExchangeCallback() {
                            @Override
                            public void onSuccess(final ExchangeRate exchangeRate) {
                                if (isAdded())
                                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                                        @Override
                                        public void run() {
                                            exchange(exchangeRate);
                                        }
                                    });
                            }

                            @Override
                            public void onError(final Exception e) {
                                Timber.e(e.getLocalizedMessage());
                                if (isAdded())
                                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                                        @Override
                                        public void run() {
                                            exchangeFailed();
                                        }
                                    });
                            }
                        });
            } else {
                updateBalance();
            }
        }
    }

    boolean isExchanging = false;

    void showExchanging() {
        isExchanging = true;
        tvBalance.setVisibility(View.GONE);
        flExchange.setVisibility(View.VISIBLE);
        sCurrency.setEnabled(false);
    }

    void hideExchanging() {
        isExchanging = false;
        tvBalance.setVisibility(View.VISIBLE);
        flExchange.setVisibility(View.GONE);
        sCurrency.setEnabled(true);
    }

    public void exchangeFailed() {
        sCurrency.setSelection(0, true); // default to XLA
        double amountxla = Helper.getDecimalAmount(unlockedBalance).doubleValue();
        showBalance(Helper.getFormattedAmount(amountxla, true));
        hideExchanging();
    }

    public void exchange(final ExchangeRate exchangeRate) {
        hideExchanging();
        if (!Helper.BASE_CRYPTO.equals(exchangeRate.getBaseCurrency())) {
            Timber.e("Not XLA");
            sCurrency.setSelection(0, true);
            balanceCurrency = Helper.BASE_CRYPTO;
            balanceRate = 1.0;
        } else {
            int spinnerPosition = ((ArrayAdapter) sCurrency.getAdapter()).getPosition(exchangeRate.getQuoteCurrency());
            if (spinnerPosition < 0) { // requested currency not in list
                Timber.e("Requested currency not in list %s", exchangeRate.getQuoteCurrency());
                sCurrency.setSelection(0, true);
            } else {
                sCurrency.setSelection(spinnerPosition, true);
            }
            balanceCurrency = exchangeRate.getQuoteCurrency();
            balanceRate = exchangeRate.getRate();
        }
        updateBalance();
    }

    // Callbacks from TransactionInfoAdapter
    @Override
    public void onInteraction(final View view, final TransactionInfo infoItem) {
        activityCallback.onTxDetailsRequest(infoItem);
    }

    // called from activity

    public void onRefreshed(final Wallet wallet, final boolean full) {
        Timber.d("onRefreshed(%b)", full);
        if (full) {
            List<TransactionInfo> list = new ArrayList<>();
            final long streetHeight = activityCallback.getStealthModeHeight();
            Timber.d("StreetHeight=%d", streetHeight);
            for (TransactionInfo info : wallet.getHistory().getAll()) {
                Timber.d("TxHeight=%d", info.blockheight);
                if ((info.isPending || (info.blockheight >= streetHeight))
                        && !dismissedTransactions.contains(info.hash))
                    list.add(info);
            }
            txInfoAdapter.setInfos(list);
            txInfoAdapter.notifyDataSetChanged();
        }

        updateStatus(wallet);
    }

    public void onSynced() {
        if (!activityCallback.isWatchOnly()) {
            //bSend.setVisibility(View.VISIBLE);
            bSend.setEnabled(true);
        }
        if (isVisible()) {
            enableAccountsList(true); //otherwise it is enabled in onResume()
            activityCallback.setToolbarButton(Toolbar.BUTTON_NONE);
        }
    }

    public void unsync() {
        if (!activityCallback.isWatchOnly()) {
            //bSend.setVisibility(View.INVISIBLE);
            bSend.setEnabled(false);
        }
        if (isVisible()) enableAccountsList(false); //otherwise it is enabled in onResume()
        firstBlock = 0;
    }

    boolean walletLoaded = false;

    public void onLoaded() {
        walletLoaded = true;
        showReceive();
    }

    private void showReceive() {
        if (walletLoaded) {
            //bReceive.setVisibility(View.VISIBLE);
            bReceive.setEnabled(true);
        }
    }

    private String syncText = null;

    public void setProgress(final String text) {
        syncText = text;
        tvProgress.setText(text);
    }

    private int syncProgress = -1;

    public void setProgress(final int n) {
        syncProgress = n;
        if (n > 100) {
            pbProgress.setIndeterminate(true);
            pbProgress.setVisibility(View.VISIBLE);
        } else if (n >= 0) {
            pbProgress.setIndeterminate(false);

            pbProgress.setProgress(n);
            pbProgress.setVisibility(View.VISIBLE);
        } else { // <0
            pbProgress.setVisibility(View.GONE);
        }
    }

    public void initWalletText(String walletName, String walletAddress) {
        llWalletAddress.setVisibility(View.VISIBLE);

        ivAddressType.setImageDrawable(getResources().getDrawable(R.drawable.ic_primary_address));
        tvAddressType.setText("Primary Address");

        tvWalletName.setText(walletName);
        tvWalletAddress.setText(Helper.getPrettyString(walletAddress));
    }

    public void setActivityTitle(Wallet wallet) {
        if (wallet == null) return;

        llWalletAddress.setVisibility(View.VISIBLE);

        walletTitle = wallet.getName();
        tvWalletName.setText(walletTitle);

        if(accountIdx <= 0) { // Primary Address
            ivAddressType.setImageDrawable(getResources().getDrawable(R.drawable.ic_primary_address));
            tvAddressType.setText("Primary Address");
        } else { // Stealth Address
            ivAddressType.setImageDrawable(getResources().getDrawable(R.drawable.ic_stealth_address));
            tvAddressType.setText("Stealth Address");
        }

        tvWalletAddress.setText(Helper.getPrettyString(wallet.getAddress()));

        activityCallback.setTitle(walletTitle, walletSubtitle);
        Timber.d("wallet title is %s", walletTitle);
    }

    private long firstBlock = 0;
    private String walletTitle = null;
    private String walletSubtitle = null;
    private long unlockedBalance = 0;
    private long balance = 0;

    private int accountIdx = -1;

    private void updateStatus(Wallet wallet) {
        if (!isAdded()) return;

        Timber.d("updateStatus()");
        if ((walletTitle == null) || (accountIdx != wallet.getAccountIndex())) {
            accountIdx = wallet.getAccountIndex();
            setActivityTitle(wallet);
        }

        balance = wallet.getBalance();
        unlockedBalance = wallet.getUnlockedBalance();
        refreshBalance();

        String sync = "";
        if (!activityCallback.hasBoundService())
            throw new IllegalStateException("WalletService not bound.");

        Wallet.ConnectionStatus daemonConnected = activityCallback.getConnectionStatus();
        if (daemonConnected == Wallet.ConnectionStatus.ConnectionStatus_Connected) {
            if (!wallet.isSynchronized()) {
                long daemonHeight = activityCallback.getDaemonHeight();
                long walletHeight = wallet.getBlockChainHeight();
                long n = daemonHeight - walletHeight;
                sync = getString(R.string.status_syncing) + " " + formatter.format(n) + " " + getString(R.string.status_remaining);
                if (firstBlock == 0) {
                    firstBlock = walletHeight;
                }
                int x = 100 - Math.round(100f * n / (1f * daemonHeight - firstBlock));
                if (x == 0) x = 101; // indeterminate
                setProgress(x);
                ivSynced.setVisibility(View.GONE);
            } else {
                sync = getString(R.string.status_synced) + " " + formatter.format(wallet.getBlockChainHeight());
                ivSynced.setVisibility(View.VISIBLE);
            }
        } else {
            sync = getString(R.string.status_wallet_connecting);
            setProgress(101);
        }
        setProgress(sync);

        if(txInfoAdapter.getItemCount() > 0) {
            rvTransactions.setVisibility(View.VISIBLE);
            tvNoTransaction.setVisibility(View.GONE);
        } else {
            rvTransactions.setVisibility(View.GONE);
            tvNoTransaction.setVisibility(View.VISIBLE);
        }
    }

    Listener activityCallback;

    // Container Activity must implement this interface
    public interface Listener {
        boolean hasBoundService();

        void forceUpdate();

        Wallet.ConnectionStatus getConnectionStatus();

        long getDaemonHeight(); //mBoundService.getDaemonHeight();

        void onSendRequest();

        void onTxDetailsRequest(TransactionInfo info);

        boolean isSynced();

        boolean isStealthMode();

        long getStealthModeHeight();

        boolean isWatchOnly();

        String getTxKey(String txId);

        void onWalletReceive();

        boolean hasWallet();

        Wallet getWallet();

        void setToolbarButton(int type);

        void setTitle(String title, String subtitle);

        void setSubtitle(String subtitle);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof Listener) {
            this.activityCallback = (Listener) context;
        } else {
            throw new ClassCastException(context.toString()
                    + " must implement Listener");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Timber.d("onResume()");
        activityCallback.setTitle(walletTitle, walletSubtitle);
        //activityCallback.setToolbarButton(Toolbar.BUTTON_CLOSE); // TODO: Close button somewhere else

        setProgress(syncProgress);
        setProgress(syncText);
        showReceive();

        if (activityCallback.isSynced()) {
            enableAccountsList(true);
            activityCallback.setToolbarButton(Toolbar.BUTTON_NONE);
        }
    }


    @Override
    public void onPause() {
        enableAccountsList(false);
        super.onPause();
    }

    public interface DrawerLocker {
        void setDrawerEnabled(boolean enabled);
    }

    private void enableAccountsList(boolean enable) {
        if (activityCallback instanceof DrawerLocker) {
            ((DrawerLocker) activityCallback).setDrawerEnabled(enable);
        }
    }
}
