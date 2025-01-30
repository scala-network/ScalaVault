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
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;

import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import io.scalaproject.vault.data.BarcodeData;
import io.scalaproject.vault.data.Contact;
import io.scalaproject.vault.data.TxData;
import io.scalaproject.vault.data.UserNotes;
import io.scalaproject.vault.dialog.CreditsFragment;
import io.scalaproject.vault.dialog.HelpFragment;
import io.scalaproject.vault.fragment.send.SendAddressWizardFragment;
import io.scalaproject.vault.fragment.send.SendFragment;
import io.scalaproject.vault.ledger.LedgerProgressDialog;
import io.scalaproject.vault.model.PendingTransaction;
import io.scalaproject.vault.model.TransactionInfo;
import io.scalaproject.vault.model.Wallet;
import io.scalaproject.vault.service.WalletService;
import io.scalaproject.vault.util.Helper;
import io.scalaproject.vault.util.ScalaThreadPoolExecutor;
import io.scalaproject.vault.widget.Toolbar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import timber.log.Timber;

public class WalletActivity extends BaseActivity implements WalletFragment.Listener,
        WalletService.Observer, SendFragment.Listener, TxFragment.Listener, AddressBookFragment.Listener,
        GenerateReviewFragment.ListenerWithWallet,
        GenerateReviewFragment.Listener,
        GenerateReviewFragment.PasswordChangedListener,
        GenerateReviewFragment.AcceptListener,
        ScannerFragment.OnScannedListener, ReceiveFragment.Listener,
        SendAddressWizardFragment.OnScanListener,
        WalletFragment.DrawerLocker,
        NavigationView.OnNavigationItemSelectedListener {

    public static final String REQUEST_ID = "id";
    public static final String REQUEST_ADDRESS = "address";
    public static final String REQUEST_PW = "pw";
    public static final String REQUEST_FINGERPRINT_USED = "fingerprint";
    public static final String REQUEST_STEALTHMODE = "stealthmode";
    public static final String REQUEST_URI = "uri";

    private NavigationView accountsView;
    private DrawerLayout drawer;
    private ActionBarDrawerToggle drawerToggle;

    private Toolbar toolbar;
    private boolean needVerifyIdentity;
    private boolean requestStealthMode = false;

    private String walletName;
    private String password;

    private String uri = null;

    private long stealthMode = 0;

    private static final String CONTACTS_NAME = "contacts";
    Set<Contact> allContacts = new HashSet<>();

    @Override
    public void onPasswordChanged(String newPassword) {
        password = newPassword;
    }

    @Override
    public void onAccept(String name, String password) {
        onBackPressed();
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public void setToolbarButton(int type) {
        toolbar.setButton(type);
    }

    @Override
    public void setTitle(String title, String subtitle) {
        //toolbar.setTitle(title, subtitle);
    }

    @Override
    public void setTitle(String title) {
        Timber.d("setTitle:%s.", title);
        //toolbar.setTitle(title);
    }

    @Override
    public void setSubtitle(String subtitle) {
        //toolbar.setSubtitle(subtitle);
    }

    private boolean synced = false;

    @Override
    public boolean isSynced() {
        return synced;
    }

    @Override
    public boolean isStealthMode() {
        return stealthMode > 0;
    }

    private void enableStealthMode(boolean enable) {
        if (enable) {
            needVerifyIdentity = true;
            stealthMode = getWallet().getDaemonBlockChainHeight();
        } else {
            stealthMode = 0;
        }
        final WalletFragment walletFragment = (WalletFragment)
                getSupportFragmentManager().findFragmentByTag(WalletFragment.class.getName());
        if (walletFragment != null) walletFragment.resetDismissedTransactions();
        forceUpdate();
        runOnUiThread(this::updateAccountsBalance);
    }

    @Override
    public long getStealthModeHeight() {
        return stealthMode;
    }

    @Override
    public boolean isWatchOnly() {
        return getWallet().isWatchOnly();
    }

    @Override
    public String getTxKey(String txId) {
        return getWallet().getTxKey(txId);
    }

    @Override
    public String getTxNotes(String txId) {
        return getWallet().getUserNote(txId);
    }

    @Override
    public void setTxNotes(String txId, String txNotes) {
        getWallet().setUserNote(txId, txNotes);
    }

    @Override
    public String getTxAddress(int major, int minor) {
        return getWallet().getSubaddress(major, minor);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Timber.d("onStart()");
    }

    private void startWalletService() {
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            acquireWakeLock();
            walletName = extras.getString(REQUEST_ID);
            needVerifyIdentity = extras.getBoolean(REQUEST_FINGERPRINT_USED);
            // we can set the stealthMode height AFTER opening the wallet
            requestStealthMode = extras.getBoolean(REQUEST_STEALTHMODE);
            password = extras.getString(REQUEST_PW);
            uri = extras.getString(REQUEST_URI);
            connectWalletService();
        } else {
            finish();
            //throw new IllegalStateException("No extras passed! Panic!");
        }
    }

    private void stopWalletService() {
        disconnectWalletService();
        releaseWakeLock();
    }

    private void onWalletRescan() {
        try {
            final WalletFragment walletFragment = (WalletFragment) getSupportFragmentManager().findFragmentByTag(WalletFragment.class.getName());
            getWallet().rescanBlockchainAsync();
            synced = false;
            assert walletFragment != null;
            walletFragment.unsync();
            invalidateOptionsMenu();
        } catch (ClassCastException ex) {
            Timber.d(ex.getLocalizedMessage());
            // keep calm and carry on
        }
    }

    private void initWalletFragmentName(String walletName) {
        final WalletFragment walletFragment = (WalletFragment) getSupportFragmentManager().findFragmentByTag(WalletFragment.class.getName());
        assert walletFragment != null;
        walletFragment.initWalletName(walletName);
    }

    private void initWalletFragmentAddress(String walletAddress) {
        final WalletFragment walletFragment = (WalletFragment) getSupportFragmentManager().findFragmentByTag(WalletFragment.class.getName());
        assert walletFragment != null;
        walletFragment.initWalletAddress(walletAddress);
    }

    @Override
    protected void onStop() {
        Timber.d("onStop()");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Timber.d("onDestroy()");
        if (drawer != null) drawer.removeDrawerListener(drawerToggle);

        super.onDestroy();
    }

    @Override
    public boolean hasWallet() {
        return haveWallet;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem renameItem = menu.findItem(R.id.action_rename);
        if (renameItem != null)
            renameItem.setEnabled(hasWallet() && getWallet().isSynchronized());

        MenuItem stealthModeItem = menu.findItem(R.id.action_stealthmode);
        if (stealthModeItem != null) {
            if (isStealthMode()) {
                stealthModeItem.setIcon(R.drawable.ic_stealth_mode_off);
            } else {
                stealthModeItem.setIcon(R.drawable.ic_stealth_mode_on);
            }
        }

        final MenuItem rescanItem = menu.findItem(R.id.action_rescan);
        if (rescanItem != null)
            rescanItem.setEnabled(isSynced());

        return super.onPrepareOptionsMenu(menu);
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return switch (item.getItemId()) {
            case R.id.action_addressbook -> {
                onAddressBook();
                yield true;
            }
            case R.id.action_wallets -> {
                showLoginFragment();
                yield true;
            }
            case R.id.action_rescan -> {
                onWalletRescan();
                yield true;
            }
            case R.id.action_info -> {
                onWalletDetails();
                yield true;
            }
            case R.id.action_credits -> {
                CreditsFragment.display(getSupportFragmentManager());
                yield true;
            }
            case R.id.action_share -> {
                onShareTxInfo();
                yield true;
            }
            case R.id.action_help_tx_info -> {
                HelpFragment.display(getSupportFragmentManager(), R.string.help_tx_details);
                yield true;
            }
            case R.id.action_help_wallet -> {
                HelpFragment.display(getSupportFragmentManager(), R.string.help_wallet);
                yield true;
            }
            case R.id.action_details_help -> {
                HelpFragment.display(getSupportFragmentManager(), R.string.help_details);
                yield true;
            }
            case R.id.action_details_changepw -> {
                onWalletChangePassword();
                yield true;
            }
            case R.id.action_help_send -> {
                HelpFragment.display(getSupportFragmentManager(), R.string.help_send);
                yield true;
            }
            case R.id.action_help_address_book -> {
                HelpFragment.display(getSupportFragmentManager(), R.string.help_address_book);
                yield true;
            }
            case R.id.action_rename -> {
                onAccountRename();
                yield true;
            }
            case R.id.action_mobile_miner -> {
                startActivity(new Intent(this, MobileMinerActivity.class));
                yield true;
            }
            case R.id.action_stealthmode -> {
                if (isStealthMode()) { // disable stealthMode
                    item.setIcon(R.drawable.ic_stealth_mode_on);
                    onDisableStealthMode();
                } else {
                    item.setIcon(R.drawable.ic_stealth_mode_off);
                    onEnableStealthMode();
                }
                yield true; // disable stealthMode
            }
            default -> super.onOptionsItemSelected(item);
        };
    }

    private void showLoginFragment() {
        Timber.d("showLoginFragment()");
        try {
            onBackPressed();
        } catch (ClassCastException ex) {
            Timber.d(ex.getLocalizedMessage());
        }
    }

    private void loadContacts() {
        allContacts.clear();

        // Load Userdefined nodes
        Map<String, ?> contacts = getSharedPreferences(CONTACTS_NAME, Context.MODE_PRIVATE).getAll();
        for (Map.Entry<String, ?> contactEntry : contacts.entrySet()) {
            if (contactEntry != null) // just in case, ignore possible future errors
                addContact((String) contactEntry.getValue());
        }
    }

    private void addContact(String contactString) {
        Contact contact = Contact.fromString(contactString);
        if (contact != null) {
            allContacts.add(contact);
        } else {
            Timber.w("contactString invalid: %s", contactString);
        }
    }

    public Set<Contact> getContacts() {
        return new HashSet<>(allContacts);
    }

    public void saveContacts(final List<Contact> contactItems) {
        Timber.d("Save contacts");

        if(contactItems.isEmpty())
            return;

        allContacts.clear();
        allContacts.addAll(contactItems);

        SharedPreferences.Editor editor = getSharedPreferences(CONTACTS_NAME, Context.MODE_PRIVATE).edit();
        editor.clear();

        int i = 1;
        for (Contact contact : contactItems) {
            String contactString = contact.toContactString();
            editor.putString(Integer.toString(i), contactString);
            Timber.d("saved %d:%s", i, contactString);
            i++;
        }

        editor.apply();
    }

    private void onAddressBook() {
        Timber.d("onAddressBook()");
        try {
            Bundle extras = new Bundle();
            replaceFragment(new AddressBookFragment(), extras);
        } catch (ClassCastException ignored) {
            Timber.d("onAddressBook() called, but no AddressBookFragment active");
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void onEnableStealthMode() {
        enableStealthMode(true);
    }

    private void onDisableStealthMode() {
        Helper.promptPassword(WalletActivity.this, getWallet().getName(), false, (walletName, password, fingerprintUsed) -> runOnUiThread(() -> enableStealthMode(false)));
    }

    public void onWalletChangePassword() {
        try {
            GenerateReviewFragment detailsFragment = (GenerateReviewFragment)
                    getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            assert detailsFragment != null;
            AlertDialog dialog = detailsFragment.createChangePasswordDialog();
            if (dialog != null) {
                Helper.showKeyboard(dialog);
                dialog.show();
            }
        } catch (ClassCastException ex) {
            Timber.w("onWalletChangePassword() called, but no GenerateReviewFragment active");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Timber.d("onCreate()");
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            // activity restarted
            // we don't want that - finish it and fall back to previous activity
            Log.d("BaseActivity", "onCreate() - activity restarted");
            finish();
            return;
        }

        setContentView(R.layout.activity_wallet);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(false);

        toolbar.setOnButtonListener(type -> {
            switch (type) {
                case Toolbar.BUTTON_BACK:
                    onDisposeRequest();
                    onBackPressed();
                    break;
                case Toolbar.BUTTON_CANCEL:
                    onDisposeRequest();
                    Helper.hideKeyboard(WalletActivity.this);
                    WalletActivity.super.onBackPressed();
                    break;
                case Toolbar.BUTTON_CLOSE:
                    finish();
                    break;
                case Toolbar.BUTTON_CREDITS:
                    CreditsFragment.display(getSupportFragmentManager());
                case Toolbar.BUTTON_NONE:
                default:
                    Timber.e("Button " + type + "pressed - how can this be?");
            }
        });

        drawer = findViewById(R.id.drawer_layout);
        drawerToggle = new ActionBarDrawerToggle(this, drawer, toolbar, 0, 0);
        drawer.addDrawerListener(drawerToggle);
        drawerToggle.syncState();
        setDrawerEnabled(false); // disable until synced

        accountsView = findViewById(R.id.accounts_nav);
        accountsView.setNavigationItemSelectedListener(this);

        Fragment walletFragment = new WalletFragment();
        getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, walletFragment, WalletFragment.class.getName()).commit();
        Timber.d("fragment added");
        loadContacts();
        startWalletService();
        Timber.d("onCreate() done.");
    }

    @Override
    public Wallet getWallet() {
        if (mBoundService == null) throw new IllegalStateException("WalletService not bound.");
        return mBoundService.getWallet();
    }

    private WalletService mBoundService = null;
    private boolean mIsBound = false;

    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mBoundService = ((WalletService.WalletServiceBinder) service).getService();
            mBoundService.setObserver(WalletActivity.this);
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                String walletName = extras.getString(REQUEST_ID);
                if (walletName != null) {
                    runOnUiThread(() -> initWalletFragmentName(walletName));
                }
            }

            updateProgress();

            Timber.d("CONNECTED");
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mBoundService = null;
            Timber.d("DISCONNECTED");
        }
    };

    void connectWalletService() {
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        Intent intent = new Intent(getApplicationContext(), WalletService.class);
        intent.putExtra(WalletService.REQUEST_WALLET, walletName);
        intent.putExtra(WalletService.REQUEST, WalletService.REQUEST_CMD_LOAD);
        intent.putExtra(WalletService.REQUEST_CMD_LOAD_PW, password);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
        Timber.d("BOUND");
    }

    void disconnectWalletService() {
        if (mIsBound && mConnection != null) {
            // Detach our existing connection.
            mBoundService.setObserver(null);
            unbindService(mConnection);
            mIsBound = false;
            Timber.d("UNBOUND");
        }
    }

    @Override
    protected void onPause() {
        Timber.d("onPause()");
        super.onPause();
    }

    @Override
    protected void onResume() {
        Timber.d("onResume()");
        super.onResume();
    }

    public void saveWallet() {
        if (mIsBound) { // no point in talking to unbound service
            Intent intent = new Intent(getApplicationContext(), WalletService.class);
            intent.putExtra(WalletService.REQUEST, WalletService.REQUEST_CMD_STORE);
            startService(intent);
            Timber.d("STORE request sent");
        } else {
            Timber.e("Service not bound");
        }
    }

//////////////////////////////////////////
// WalletFragment.Listener
//////////////////////////////////////////

    @Override
    public boolean hasBoundService() {
        return mBoundService != null;
    }

    @Override
    public Wallet.ConnectionStatus getConnectionStatus() {
        return mBoundService.getConnectionStatus();
    }

    @Override
    public long getDaemonHeight() {
        return mBoundService.getDaemonHeight();
    }

    @Override
    public void onSendRequest() {
        replaceFragment(SendFragment.newInstance(uri), null);
        uri = null; // only use uri once
    }

    @Override
    public void onTxDetailsRequest(TransactionInfo txInfo) {
        Bundle args = new Bundle();
        args.putParcelable(TxFragment.ARG_INFO, txInfo);
        replaceFragment(new TxFragment(), args);
    }

    @Override
    public Contact onFindContactRequest(TransactionInfo txInfo) {
        if(txInfo.direction == TransactionInfo.Direction.Direction_Out) { // just in case
            for (Contact contact : allContacts) {
                // assume there is only one recipient address
                if(txInfo.transfers != null && !txInfo.transfers.isEmpty()) {
                    if (contact.getAddress().equals(txInfo.transfers.get(0).address))
                        return contact;
                }
            }
        }

        return null;
    }

    @Override
    public void forceUpdate() {
        try {
            onRefreshed(getWallet(), true);
        } catch (IllegalStateException ex) {
            Timber.e(ex.getLocalizedMessage());
        }
    }

///////////////////////////
// WalletService.Observer
///////////////////////////

    private int numAccounts = -1;

    // refresh and return true if successful
    @Override
    public boolean onRefreshed(final Wallet wallet, final boolean full) {
        Timber.d("onRefreshed()");
        runOnUiThread(this::updateAccountsBalance);

        if(wallet == null)
            return false;

        if (numAccounts != wallet.getNumAccounts()) {
            numAccounts = wallet.getNumAccounts();
            runOnUiThread(this::updateAccountsList);
        }
        try {
            final WalletFragment walletFragment = (WalletFragment) getSupportFragmentManager().findFragmentByTag(WalletFragment.class.getName());
            if (wallet.isSynchronized()) {
                Timber.d("onRefreshed() synced");
                releaseWakeLock(RELEASE_WAKE_LOCK_DELAY); // the idea is to stay awake until synced
                if (!synced) { // first sync
                    onProgress(-1);
                    saveWallet(); // save on first sync
                    synced = true;
                    runOnUiThread(() -> {
                        assert walletFragment != null;
                        walletFragment.onSynced();
                    });
                }
            }
            runOnUiThread(() -> {
                assert walletFragment != null;
                walletFragment.onRefreshed(wallet, full);
            });
            return true;
        } catch (ClassCastException ex) {
            // not in wallet fragment (probably send scala)
            Timber.d(ex.getLocalizedMessage());
            // keep calm and carry on
        }
        return false;
    }

    // Send toast message to user when wallet is stored successfully or not
    @Override
    public void onWalletStored(final boolean success) {
        runOnUiThread(() -> {
            if (success) {
                Toast.makeText(WalletActivity.this, getString(R.string.status_wallet_unloaded), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(WalletActivity.this, getString(R.string.status_wallet_unload_failed), Toast.LENGTH_LONG).show();
            }
        });
    }

    boolean haveWallet = false;

    @Override
    public void onWalletOpen(final Wallet.Device device) {
        if (Objects.requireNonNull(device) == Wallet.Device.Device_Ledger) {
            runOnUiThread(() -> showLedgerProgressDialog(LedgerProgressDialog.TYPE_RESTORE));
        }
    }

    @Override
    public void onWalletStarted(final Wallet.Status walletStatus) {
        runOnUiThread(() -> {
            dismissProgressDialog();
            if (walletStatus == null) {
                // guess what went wrong
                Toast.makeText(WalletActivity.this, getString(R.string.status_wallet_connect_failed), Toast.LENGTH_LONG).show();
            } else {
                if (Wallet.ConnectionStatus.ConnectionStatus_WrongVersion == walletStatus.getConnectionStatus())
                    Toast.makeText(WalletActivity.this, getString(R.string.status_wallet_connect_wrongversion), Toast.LENGTH_LONG).show();
                else if (!walletStatus.isOk())
                    Toast.makeText(WalletActivity.this, walletStatus.getErrorString(), Toast.LENGTH_LONG).show();
            }
        });
        if ((walletStatus == null) || (Wallet.ConnectionStatus.ConnectionStatus_Connected != walletStatus.getConnectionStatus())) {
            finish();
        } else {
            haveWallet = true;
            invalidateOptionsMenu();

            enableStealthMode(requestStealthMode);

            final WalletFragment walletFragment = (WalletFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            runOnUiThread(() -> {
                updateAccountsHeader();
                if (walletFragment != null) {
                    walletFragment.onLoaded();
                }
            });
        }

        Wallet wallet = getWallet();
        if(wallet != null ) {
            String walletAddress = wallet.getAddress();
            if (walletAddress != null) {
                runOnUiThread(() -> initWalletFragmentAddress(walletAddress));
            }
        }
    }

    @Override
    public void onTransactionCreated(final String txTag, final PendingTransaction pendingTransaction) {
        try {
            final SendFragment sendFragment = (SendFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            runOnUiThread(() -> {
                dismissProgressDialog();
                PendingTransaction.Status status = pendingTransaction.getStatus();
                if (status != PendingTransaction.Status.Status_Ok) {
                    String errorText = pendingTransaction.getErrorString();
                    getWallet().disposePendingTransaction();
                    assert sendFragment != null;
                    sendFragment.onCreateTransactionFailed(errorText);
                } else {
                    assert sendFragment != null;
                    sendFragment.onTransactionCreated(txTag, pendingTransaction);
                }
            });
        } catch (ClassCastException ex) {
            // not in spend fragment
            Timber.d(ex.getLocalizedMessage());
            // don't need the transaction any more
            getWallet().disposePendingTransaction();
        }
    }

    @Override
    public void onSendTransactionFailed(final String error) {
        try {
            final SendFragment sendFragment = (SendFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            runOnUiThread(() -> {
                assert sendFragment != null;
                sendFragment.onSendTransactionFailed(error);
            });
        } catch (ClassCastException ex) {
            // not in spend fragment
            Timber.d(ex.getLocalizedMessage());
        }
    }

    @Override
    public void onTransactionSent(final String txId) {
        try {
            final SendFragment sendFragment = (SendFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            runOnUiThread(() -> {
                assert sendFragment != null;
                sendFragment.onTransactionSent(txId);
            });
        } catch (ClassCastException ex) {
            // not in spend fragment
            Timber.d(ex.getLocalizedMessage());
        }
    }

    @Override
    public void onProgress(final String text) {
        try {
            final WalletFragment walletFragment = (WalletFragment) getSupportFragmentManager().findFragmentByTag(WalletFragment.class.getName());
            if(walletFragment != null) {
                runOnUiThread(() -> walletFragment.setProgress(text));
            }
        } catch (ClassCastException ex) {
            // not in wallet fragment (probably send scala)
            Timber.d(ex.getLocalizedMessage());
            // keep calm and carry on
        }
    }

    @Override
    public void onProgress(final int n) {
        runOnUiThread(() -> {
            try {
                WalletFragment walletFragment = (WalletFragment) getSupportFragmentManager().findFragmentByTag(WalletFragment.class.getName());
                if (walletFragment != null)
                    walletFragment.setProgress(n);
            } catch (ClassCastException ex) {
                // not in wallet fragment (probably send scala)
                Timber.d(ex.getLocalizedMessage());
                // keep calm and carry on
            }
        });
    }

    private void updateProgress() {
        // TODO maybe show real state of WalletService (like "still closing previous wallet")
        if (hasBoundService()) {
            onProgress(mBoundService.getProgressText());
            onProgress(mBoundService.getProgressValue());
        }
    }


///////////////////////////
// SendFragment.Listener
///////////////////////////

    @Override
    public void onSend(UserNotes notes) {
        if (mIsBound) { // no point in talking to unbound service
            Intent intent = new Intent(getApplicationContext(), WalletService.class);
            intent.putExtra(WalletService.REQUEST, WalletService.REQUEST_CMD_SEND);
            intent.putExtra(WalletService.REQUEST_CMD_SEND_NOTES, notes.txNotes);
            startService(intent);
            Timber.d("SEND TX request sent");
        } else {
            Timber.e("Service not bound");
        }
    }

    public void onSaveContact(String name, String address) {
        Contact contact = new Contact(name, address);
        allContacts.add(contact);
        List<Contact> contactItems = new ArrayList<>(allContacts);
        saveContacts(contactItems);
    }

    @Override
    public void onPrepareSend(final String tag, final TxData txData) {
        if (mIsBound) { // no point in talking to unbound service
            Intent intent = new Intent(getApplicationContext(), WalletService.class);
            intent.putExtra(WalletService.REQUEST, WalletService.REQUEST_CMD_TX);
            intent.putExtra(WalletService.REQUEST_CMD_TX_DATA, txData);
            intent.putExtra(WalletService.REQUEST_CMD_TX_TAG, tag);
            startService(intent);
            Timber.d("CREATE TX request sent");
            if (getWallet().getDeviceType() == Wallet.Device.Device_Ledger)
                showLedgerProgressDialog(LedgerProgressDialog.TYPE_SEND);
        } else {
            Timber.e("Service not bound");
        }
    }

    @Override
    public String getWalletSubaddress(int accountIndex, int subaddressIndex) {
        return getWallet().getSubaddress(accountIndex, subaddressIndex);
    }

    public String getWalletName() {
        return getWallet().getName();
    }

    void popFragmentStack() {
        getSupportFragmentManager().popBackStack();
    }

    void replaceFragment(Fragment newFragment, Bundle extras) {
        if (extras != null) {
            newFragment.setArguments(extras);
        }
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, newFragment);
        transaction.addToBackStack(null);
        transaction.commit();
    }

    private void onWalletDetails() {
        DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    final Bundle extras = new Bundle();
                    extras.putString(GenerateReviewFragment.REQUEST_TYPE, GenerateReviewFragment.VIEW_TYPE_WALLET);

                    if (needVerifyIdentity) {
                        Helper.promptPassword(WalletActivity.this, getWallet().getName(), true, (walletName, password, fingerprintUsed) -> {
                            replaceFragment(new GenerateReviewFragment(), extras);
                            needVerifyIdentity = false;
                        });
                    } else {
                        replaceFragment(new GenerateReviewFragment(), extras);
                    }

                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    // do nothing
                    break;
            }
        };

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogCustom);
        builder.setMessage(getString(R.string.details_alert_message))
                .setPositiveButton(getString(R.string.details_alert_yes), dialogClickListener)
                .setNegativeButton(getString(R.string.details_alert_no), dialogClickListener)
                .show();
    }

    void onShareTxInfo() {
        try {
            TxFragment fragment = (TxFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            assert fragment != null;
            fragment.shareTxInfo();
        } catch (ClassCastException ex) {
            // not in wallet fragment
            Timber.e(ex.getLocalizedMessage());
            // keep calm and carry on
        }
    }

    @Override
    public void onDisposeRequest() {
        //TODO consider doing this through the WalletService to avoid concurrency issues
        getWallet().disposePendingTransaction();
    }

    private boolean startScanFragment = false;

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        if (startScanFragment) {
            startScanFragment();
            startScanFragment = false;
        }
    }

    private void startScanFragment() {
        Bundle extras = new Bundle();
        replaceFragment(new ScannerFragment(), extras);
    }

    /// QR scanner callbacks
    @Override
    public void onScan() {
        if (Helper.getCameraPermission(this)) {
            startScanFragment();
        } else {
            Timber.i("Waiting for permissions");
        }

    }

    @Override
    public void onScanned(String qrCode, ScannedCallbackListener listener) {
        // #gurke
        BarcodeData.fromString(qrCode, (bcData) -> {
            if (bcData != null) {
                popFragmentStack();
                Timber.d("AAA");
                onUriScanned(bcData);
                listener.onScanned(true);
            } else {
                listener.onScanned(false);
            }
        });
    }

    OnUriScannedListener onUriScannedListener = null;

    @Override
    public void setOnUriScannedListener(OnUriScannedListener onUriScannedListener) {
        this.onUriScannedListener = onUriScannedListener;
    }

    @Override
    void onUriScanned(BarcodeData barcodeData) {
        super.onUriScanned(barcodeData);

        if (onUriScannedListener != null) {
            onUriScannedListener.onUriScanned(barcodeData);
        }
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Timber.d("onRequestPermissionsResult()");
        switch (requestCode) {
            case Helper.PERMISSIONS_REQUEST_CAMERA: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startScanFragment = true;
                } else {
                    String msg = getString(R.string.message_camera_not_permitted);
                    Timber.e(msg);
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                }
                break;
            }
            case Helper.PERMISSIONS_REQUEST_READ_IMAGES:
            {
                AddressBookFragment.pickImageImpl();
            }
            default:
            {
                // nothing
                Log.d("BaseActivity", "Unknown request code: " + requestCode);
            }
        }
    }

    @Override
    public void onWalletReceive() {
        startReceive(getWallet().getAddress());
    }

    void startReceive(String address) {
        Timber.d("startReceive()");
        Bundle b = new Bundle();
        b.putString("address", address);
        b.putString("name", getWalletName());
        startReceiveFragment(b);
    }

    void startReceiveFragment(Bundle extras) {
        replaceFragment(new ReceiveFragment(), extras);
        Timber.d("ReceiveFragment placed");
    }

    @Override
    public long getTotalFunds() {
        return getWallet().getUnlockedBalance();
    }

    @Override
    public boolean verifyWalletPassword(String password) {
        String walletPassword = Helper.getWalletPassword(getApplicationContext(), getWalletName(), password);
        return walletPassword != null;
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
            return;
        }

        final Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (fragment instanceof OnBackPressedListener) {
            if (!((OnBackPressedListener) fragment).onBackPressed()) {
                super.onBackPressed();
            }
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onFragmentDone() {
        popFragmentStack();
    }

    @Override
    public SharedPreferences getPrefs() {
        return getPreferences(Context.MODE_PRIVATE);
    }

    private final List<Integer> accountIds = new ArrayList<>();

    // generate and cache unique ids for use in accounts list
    private int getAccountId(int accountIndex) {
        final int n = accountIds.size();
        for (int i = n; i <= accountIndex; i++) {
            accountIds.add(View.generateViewId());
        }
        return accountIds.get(accountIndex);
    }

    // drawer stuff

    void updateAccountsBalance() {
        final TextView tvBalance = accountsView.getHeaderView(0).findViewById(R.id.tvBalance);
        if (!isStealthMode()) {
            Wallet w = getWallet();
            if(w != null) {
                tvBalance.setText(getString(R.string.accounts_balance, Helper.getDisplayAmount(w.getBalanceAll(), 5)));
            }
        } else {
            tvBalance.setText(null);
        }
        updateAccountsList();
    }

    void updateAccountsHeader() {
        final Wallet wallet = getWallet();
        final TextView tvName = accountsView.getHeaderView(0).findViewById(R.id.tvName);
        tvName.setText(wallet.getName());
    }

    void updateAccountsList() {
        final Wallet wallet = getWallet();
        Menu menu = accountsView.getMenu();
        menu.removeGroup(R.id.accounts_list);
        final int n = wallet.getNumAccounts();
        final boolean showBalances = (n > 1) && !isStealthMode();
        for (int i = 0; i < n; i++) {
            final String label = (showBalances ?
                    getString(R.string.label_account, i == 0 ? getString(R.string.primary_address) : wallet.getAccountLabel(i), Helper.getDisplayAmount(wallet.getBalance(i), 2))
                    : i == 0 ? getString(R.string.primary_address) : wallet.getAccountLabel(i));

            final MenuItem item = menu.add(R.id.accounts_list, getAccountId(i), 2 * i, label);
            item.setIcon(i == 0 ? R.drawable.ic_primary_address : R.drawable.ic_stealth_address);

            if (i == wallet.getAccountIndex())
                item.setChecked(true);
        }

        menu.setGroupCheckable(R.id.accounts_list, true, true);
    }

    @Override
    public void setDrawerEnabled(boolean enabled) {
        Timber.d("setDrawerEnabled %b", enabled);
        final int lockMode = enabled ? DrawerLayout.LOCK_MODE_UNLOCKED :
                DrawerLayout.LOCK_MODE_LOCKED_CLOSED;
        drawer.setDrawerLockMode(lockMode);
        drawerToggle.setDrawerIndicatorEnabled(enabled);
        invalidateOptionsMenu(); // menu may need to be changed
    }

    void updateAccountName() {
        setSubtitle(getWallet().getAccountLabel());
        updateAccountsList();
    }

    public void onAccountRename() {
        final LayoutInflater li = LayoutInflater.from(this);
        final View promptsView = li.inflate(R.layout.prompt_rename, null);

        final MaterialAlertDialogBuilder alertDialogBuilder = new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogCustom);
        alertDialogBuilder.setView(promptsView);

        final EditText etRename = promptsView.findViewById(R.id.etRename);
        final TextView tvRenameLabel = promptsView.findViewById(R.id.tvRenameLabel);
        final Wallet wallet = getWallet();
        tvRenameLabel.setText(getString(R.string.prompt_rename, wallet.getAccountLabel()));

        // set dialog message
        alertDialogBuilder.setCancelable(false).setPositiveButton(getString(R.string.label_ok), (dialog, id) -> {
            Helper.hideKeyboardAlways(WalletActivity.this);
            String newName = etRename.getText().toString();
            wallet.setAccountLabel(newName);
            updateAccountName();
        }).setNegativeButton(getString(R.string.label_cancel), (dialog, id) -> {
            Helper.hideKeyboardAlways(WalletActivity.this);
            dialog.cancel();
        });

        final androidx.appcompat.app.AlertDialog dialog = alertDialogBuilder.create();
        Helper.showKeyboard(dialog);

        // accept keyboard "ok"
        /*
            TODO check if IME creates bug in android, no action needed, this dosnt affect in the app,
            and
         */
        etRename.setOnEditorActionListener((v, actionId, event) -> {
            if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) && (event.getAction() == KeyEvent.ACTION_DOWN)) || (actionId == EditorInfo.IME_ACTION_DONE)) {
                Helper.hideKeyboardAlways(WalletActivity.this);
                String newName = etRename.getText().toString();
                dialog.cancel();
                wallet.setAccountLabel(newName);
                updateAccountName();
                return false;
            }
            return false;
        });

        dialog.show();
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.account_new) {
            addAccount();
        } else {
            Timber.d("NavigationDrawer ID=%d", id);
            int accountIdx = accountIds.indexOf(id);
            if (accountIdx >= 0) {
                Timber.d("found @%d", accountIdx);
                getWallet().setAccountIndex(accountIdx);
            }
            forceUpdate();
            drawer.closeDrawer(GravityCompat.START);
        }
        return true;
    }

    private void addAccount() {
        new AsyncAddAccount().executeOnExecutor(ScalaThreadPoolExecutor.SCALA_THREAD_POOL_EXECUTOR);
    }

    public void onCopyAddress(View view) {
        Wallet wallet = getWallet();
        if(wallet != null) {
            Helper.clipBoardCopy(this, getString(R.string.label_copy_address), getWallet().getAddress());
            Toast.makeText(this, getString(R.string.message_copy_address), Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class AsyncAddAccount extends AsyncTask<Void, Void, Boolean> {
        boolean dialogOpened = false;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            switch (getWallet().getDeviceType()) {
                case Device_Ledger:
                    showLedgerProgressDialog(LedgerProgressDialog.TYPE_ACCOUNT);
                    dialogOpened = true;
                    break;
                case Device_Software:
                    showProgressDialog(R.string.accounts_progress_new);
                    dialogOpened = true;
                    break;
                default:
                    throw new IllegalStateException("Hardware backing not supported. At all!");
            }
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            if (params.length != 0) return false;
            getWallet().addAccount();
            getWallet().setAccountIndex(getWallet().getNumAccounts() - 1);
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            forceUpdate();
            drawer.closeDrawer(GravityCompat.START);
            if (dialogOpened)
                dismissProgressDialog();
            Toast.makeText(WalletActivity.this,
                    getString(R.string.accounts_new, getWallet().getNumAccounts() - 1),
                    Toast.LENGTH_SHORT).show();
        }
    }
}
