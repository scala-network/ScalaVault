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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;


import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
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
        inflater.inflate(R.menu.address_book_menu, menu);
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

    private boolean newContact = false;

    // Callbacks from NodeInfoAdapter
    @Override
    public void onSelectContact(final View view, final Contact contact) {
        Timber.d("onSelectContact");

        newContact = false;

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
            newContact = true;

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
        if (editDialog != null) {
            editDialog.closeDialog();
            editDialog = null;
        }

        editDialog = new EditDialog(contact);

        return editDialog;
    }

    private Contact contactEdit = null;
    private Contact contactEditBackup = null;

    class EditDialog {
        private boolean applyChanges() {
            final String contactName = etContactName.getEditText().getText().toString().trim();
            if (contactName.isEmpty()) {
                etContactName.setError(getString(R.string.contact_value_empty));
                return false;
            } else {
                contactEdit.setName(contactName);
            }

            final String walletAddress = etWalletAddress.getEditText().getText().toString().trim();
            if (walletAddress.isEmpty()) {
                etWalletAddress.setError(getString(R.string.contact_value_empty));
                return false;
            } else if (!Wallet.isAddressValid(walletAddress)) {
                etWalletAddress.setError(getString(R.string.generate_check_address));
                return false;
            } else {
                contactEdit.setAddress(walletAddress);
            }

            return true;
        }

        private boolean applyChangesTmp() {
            final String contactName = etContactName.getEditText().getText().toString().trim();
            contactEdit.setName(contactName);

            final String walletAddress = etWalletAddress.getEditText().getText().toString().trim();
            contactEdit.setAddress(walletAddress);

            return true;
        }

        private void apply() {
            if (applyChanges()) {
                closeDialog();

                if (newContact)
                    contactsAdapter.addContact(contactEdit);

                contactsAdapter.dataSetChanged();

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

        androidx.appcompat.app.AlertDialog editDialog = null;

        TextInputLayout etContactName;
        TextInputLayout etWalletAddress;
        ImageView ivAvatar;

        public static final int GET_FROM_GALLERY = 1;

        EditDialog(final Contact contact) {
            MaterialAlertDialogBuilder alertDialogBuilder = new MaterialAlertDialogBuilder(getActivity(), R.style.MaterialAlertDialogCustom);
            LayoutInflater li = LayoutInflater.from(alertDialogBuilder.getContext());
            View promptsView = li.inflate(R.layout.prompt_editcontact, null);
            alertDialogBuilder.setView(promptsView);

            etContactName = promptsView.findViewById(R.id.etContactName);
            etWalletAddress = promptsView.findViewById(R.id.etWalletAddress);
            ivAvatar = promptsView.findViewById(R.id.ivAvatar);

            ImageButton bPasteAddress = promptsView.findViewById(R.id.bPasteAddress);
            bPasteAddress.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    final String clip = Helper.getClipBoardText(getActivity());
                    if (clip == null) return;

                    etWalletAddress.getEditText().setText(clip);
                }
            });

            Button btnSelectImage = promptsView.findViewById(R.id.btnSelectImage);
            btnSelectImage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    applyChangesTmp();
                    pickImage();
                }
            });

            if (contact != null) {
                contactEdit = contact;

                if(contactEditBackup == null)
                    contactEditBackup = new Contact(contact);

                etContactName.getEditText().setText(contact.getName());
                etWalletAddress.getEditText().setText(contact.getAddress());

                Bitmap avatar = contact.getAvatar();
                if(avatar != null)
                    ivAvatar.setImageBitmap(contact.getAvatar());
                else {
                    ivAvatar.setImageBitmap(Helper.getBitmap(getContext(), R.drawable.ic_contact_avatar));
                }
            } else {
                contactEdit = new Contact();
                contactEditBackup = null;
                ivAvatar.setImageBitmap(Helper.getBitmap(getContext(), R.drawable.ic_contact_avatar));
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

        public void pickImage() {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI);
            intent.setType("image/*");
            intent.putExtra("crop", "true");
            intent.putExtra("scale", true);
            intent.putExtra("outputX", 256);
            intent.putExtra("outputY", 256);
            intent.putExtra("aspectX", 1);
            intent.putExtra("aspectY", 1);
            intent.putExtra("return-data", true);

            startActivityForResult(intent, 1);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode== EditDialog.GET_FROM_GALLERY & resultCode== Activity.RESULT_OK) {
            Timber.d("AddressBook.onActivityResult");

            // Already save the cropped image
            Bitmap bitmap = Helper.getCroppedBitmap((Bitmap) data.getExtras().get("data"));

            contactEdit.setAvatar(bitmap);

            EditDialog diag = createEditDialog(contactEdit);
            if (diag != null) {
                diag.show();
            }
        }
    }
}