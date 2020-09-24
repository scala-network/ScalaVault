/*
 * Copyright (c) 2018 m2049r
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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;

import io.scalaproject.vault.data.Contact;
import io.scalaproject.vault.layout.ContactInfoAdapter;
import io.scalaproject.vault.model.Wallet;
import io.scalaproject.vault.util.Helper;
import io.scalaproject.vault.widget.Toolbar;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import timber.log.Timber;

public class AddressBookFragment extends Fragment
        implements ContactInfoAdapter.OnSelectContactListener, ContactInfoAdapter.OnDeleteContactListener, View.OnClickListener {

    public static final String REQUEST_MODE = "mode";

    static final public String MODE_TYPE_WRITE = "write";
    static final public String MODE_TYPE_READONLY = "readonly";

    private boolean readonly = false;

    private View fabAddContact;
    private RecyclerView rvContacts;
    private LinearLayout llNoContact;

    private Set<Contact> contacts = new HashSet<>();

    private ContactInfoAdapter contactsAdapter;

    private AddressBookFragment.Listener activityCallback;

    public interface Listener {
        void setToolbarButton(int type);

        void setSubtitle(String title);

        Set<Contact> getContacts();

        void saveContacts(final List<Contact> contactItems);

        void onBackPressed();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof AddressBookFragment.Listener) {
            this.activityCallback = (Listener) context;
        } else {
            throw new ClassCastException(context.toString()
                    + " must implement Listener");
        }
    }

    @Override
    public void onPause() {
        Timber.d("onPause() %d", contactsAdapter.getContacts().size());

        if (activityCallback != null)
            activityCallback.saveContacts(contactsAdapter.getContacts());

        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        Timber.d("onResume()");
        activityCallback.setSubtitle("Address Book");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Timber.d("onCreateView");
        View view = inflater.inflate(R.layout.fragment_address_book, container, false);

        Bundle args = getArguments();
        if(!args.isEmpty() && args.containsKey(REQUEST_MODE)) {
            readonly = args.getString(REQUEST_MODE).equals(MODE_TYPE_READONLY);
        }

        llNoContact = view.findViewById(R.id.llNoContact);

        fabAddContact = view.findViewById(R.id.fabAddContact);
        fabAddContact.setOnClickListener(this);
        fabAddContact.setVisibility(readonly ? View.GONE : View.VISIBLE);

        rvContacts = view.findViewById(R.id.listContacts);
        contactsAdapter = new ContactInfoAdapter(getActivity(), readonly, this, this);
        rvContacts.setAdapter(contactsAdapter);

        Helper.hideKeyboard(getActivity());

        contacts = new HashSet<>(activityCallback.getContacts());
        contactsAdapter.setContacts(contacts);

        ViewGroup llNotice = view.findViewById(R.id.llNotice);
        //Notice.showAll(llNotice, "notice_contacts");

        activityCallback.setToolbarButton(Toolbar.BUTTON_BACK);

        refreshContacts();

        return view;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.node_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    // Callbacks from ContactInfoAdapter

    @Override
    public void onDeleteContact(final View view, final Contact contact) {
        Timber.d("onDeleteContact");

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity(), R.style.MaterialAlertDialogCustom);
        builder.setMessage(getString(R.string.delete_contact_conf, contact.getName()))
                .setCancelable(true)
                .setPositiveButton(getString(R.string.details_alert_yes), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        contactsAdapter.deleteContact(contact);
                        refreshContacts();
                    }
                })
                .setNegativeButton(getString(R.string.details_alert_no), null)
                .show();
    }

    // Callbacks from NodeInfoAdapter
    @Override
    public void onSelectContact(final View view, final Contact contact) {
        Timber.d("onSelectContact");

        if(readonly) {
            Config.write(Config.CONFIG_KEY_SELECTED_ADDRESS, contact.getAddress());
            activityCallback.onBackPressed();
        }
        else {
            EditDialog diag = createEditDialog(contact);
            if (diag != null) {
                diag.show();
            }
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.fabAddContact) {
            EditDialog diag = createEditDialog(null);
            if (diag != null) {
                diag.show();
            }
        }
    }

    @Override
    public void onDetach() {
        Timber.d("detached");
        super.onDetach();
    }

    public void refreshContacts() {
        Timber.d("loadContacts");

        if(contactsAdapter.getContacts().isEmpty()) {
            rvContacts.setVisibility(View.GONE);
            llNoContact.setVisibility(View.VISIBLE);
        } else {
            rvContacts.setVisibility(View.VISIBLE);
            llNoContact.setVisibility(View.GONE);
        }
    }

    private EditDialog editDialog = null; // for preventing opening of multiple dialogs

    private EditDialog createEditDialog(final Contact contact) {
        if (editDialog != null) return null; // we are already open
        editDialog = new EditDialog(contact);
        return editDialog;
    }

    class EditDialog {
        Contact contactEdit = null;
        Contact contactEditBackup = null;

        private boolean applyChanges() {
            final String contactName = etContactName.getEditText().getText().toString().trim();
            if (contactName.isEmpty()) {
                etContactName.setError(getString(R.string.contact_value_empty));
                return false;
            } else {
                contactEdit.setName(etContactName.getEditText().getText().toString().trim());
            }

            final String walletAddress = etWalletAddress.getEditText().getText().toString().trim();
            if (walletAddress.isEmpty()) {
                etWalletAddress.setError(getString(R.string.contact_value_empty));
                return false;
            } else if (!Wallet.isAddressValid(walletAddress)){
                etWalletAddress.setError(getString(R.string.generate_check_address));
                return false;
            } else {
                contactEdit.setAddress(etWalletAddress.getEditText().getText().toString().trim());
            }

            return true;
        }

        private boolean shutdown = false;

        private void apply() {
            if (applyChanges()) {
                closeDialog();

                contactsAdapter.addContact(contactEdit);

                shutdown = true;

                refreshContacts();
            }
        }

        private void closeDialog() {
            if (editDialog == null) throw new IllegalStateException();

            Helper.hideKeyboardAlways(getActivity());

            editDialog.dismiss();
            editDialog = null;

            AddressBookFragment.this.editDialog = null;
        }

        private void undoChanges() {
            if (contactEditBackup != null)
                contactEdit.overwriteWith(contactEditBackup);
        }

        private void show() {
            editDialog.show();
        }

        private void showKeyboard() {
            Helper.showKeyboard(editDialog);
        }

        androidx.appcompat.app.AlertDialog editDialog = null;

        TextInputLayout etContactName;
        TextInputLayout etWalletAddress;

        EditDialog(final Contact contact) {
            MaterialAlertDialogBuilder alertDialogBuilder = new MaterialAlertDialogBuilder(getActivity(), R.style.MaterialAlertDialogCustom);
            LayoutInflater li = LayoutInflater.from(alertDialogBuilder.getContext());
            View promptsView = li.inflate(R.layout.prompt_editcontact, null);
            alertDialogBuilder.setView(promptsView);

            etContactName = promptsView.findViewById(R.id.etContactName);
            etWalletAddress = promptsView.findViewById(R.id.etWalletAddress);

            if (contact != null) {
                contactEdit = contact;
                contactEditBackup = new Contact(contact);
                etContactName.getEditText().setText(contact.getName());
                etWalletAddress.getEditText().setText(contact.getAddress());
            } else {
                contactEdit = new Contact();
                contactEditBackup = null;
            }

            // set dialog message
            alertDialogBuilder
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.label_ok), null)
                    .setNegativeButton(getString(R.string.label_cancel),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    undoChanges();
                                    closeDialog();
                                    contactsAdapter.dataSetChanged(); // to refresh test results
                                }
                            });

            editDialog = alertDialogBuilder.create();
            // these need to be here, since we don't always close the dialog
            editDialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(final DialogInterface dialog) {
                    Button button = editDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    button.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            apply();
                        }
                    });
                }
            });

            if (Helper.preventScreenshot()) {
                editDialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            }

            refreshContacts();
        }
    }
}