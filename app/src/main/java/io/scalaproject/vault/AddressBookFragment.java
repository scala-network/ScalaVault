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
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.yalantis.ucrop.UCrop;

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

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import timber.log.Timber;

public class AddressBookFragment extends Fragment implements ContactInfoAdapter.OnSelectContactListener, ContactInfoAdapter.OnDeleteContactListener, View.OnClickListener {

    public static final String REQUEST_MODE = "mode";
    static final public String MODE_TYPE_READONLY = "readonly";
    private boolean readonly = false;
    private RecyclerView rvContacts;
    private LinearLayout llNoContact;
    private ContactInfoAdapter contactsAdapter;
    private AddressBookFragment.Listener activityCallback;
    private static ActivityResultLauncher<String> mGetContentLauncher;
    private ActivityResultLauncher<Intent> mCropImageLauncher;

    public interface Listener {
        void setToolbarButton(int type);
        void setSubtitle(String title);
        Set<Contact> getContacts();
        void saveContacts(final List<Contact> contactItems);
        void onBackPressed();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof AddressBookFragment.Listener) {
            this.activityCallback = (Listener) context;
        } else {
            throw new ClassCastException(context.toString() + " must implement Listener");
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Timber.d("onCreateView");
        View view = inflater.inflate(R.layout.fragment_address_book, container, false);

        Bundle args = getArguments();
        assert args != null;
        if(!args.isEmpty() && args.containsKey(REQUEST_MODE)) {
            readonly = Objects.equals(args.getString(REQUEST_MODE), MODE_TYPE_READONLY);
        }

        llNoContact = view.findViewById(R.id.llNoContact);

        View fabAddContact = view.findViewById(R.id.fabAddContact);
        fabAddContact.setOnClickListener(this);
        fabAddContact.setVisibility(readonly ? View.GONE : View.VISIBLE);

        rvContacts = view.findViewById(R.id.listContacts);
        contactsAdapter = new ContactInfoAdapter(getActivity(), readonly, this, this);
        rvContacts.setAdapter(contactsAdapter);

        Helper.hideKeyboard(getActivity());

        Set<Contact> contacts = new HashSet<>(activityCallback.getContacts());
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
        initLaunchers();
    }

    private void initLaunchers() {
        mGetContentLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::startCropActivity
        );

        mCropImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::handleCropResult
        );
    }

    private void startCropActivity(Uri uri) {
        Uri destinationUri = Uri.fromFile(new File(requireContext().getCacheDir(), "cropped.jpg"));
        UCrop uCrop = UCrop.of(uri, destinationUri);
        uCrop.withAspectRatio(1, 1);
        uCrop.withMaxResultSize(256, 256);
        mCropImageLauncher.launch(uCrop.getIntent(requireContext()));
    }

    private void handleCropResult(ActivityResult result) {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            Uri resultUri = UCrop.getOutput(result.getData());
            if (resultUri != null) {
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(requireContext().getContentResolver(), resultUri);
                    contactEdit.setAvatar(bitmap);
                    if (editDialog.isShowing()) {
                        editDialog.updateAvatar(bitmap);
                    }
                } catch (IOException e) {
                    Timber.e(e, "Failed to load image from UCrop result");
                }
            } else {
                Timber.e("Result URI from UCrop is null");
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.address_book_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    // Callbacks from ContactInfoAdapter

    @Override
    public void onDeleteContact(final View view, final Contact contact) {
        Timber.d("onDeleteContact");

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity(), R.style.MaterialAlertDialogCustom);
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
            final String contactName = Objects.requireNonNull(etContactName.getEditText()).getText().toString().trim();
            if (contactName.isEmpty()) {
                etContactName.setError(getString(R.string.contact_value_empty));
                return false;
            } else {
                contactEdit.setName(contactName);
            }

            final String walletAddress = Objects.requireNonNull(etWalletAddress.getEditText()).getText().toString().trim();
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

        private void applyChangesTmp() {
            final String contactName = Objects.requireNonNull(etContactName.getEditText()).getText().toString().trim();
            contactEdit.setName(contactName);

            final String walletAddress = Objects.requireNonNull(etWalletAddress.getEditText()).getText().toString().trim();
            contactEdit.setAddress(walletAddress);
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

            Helper.hideKeyboardAlways(requireActivity());

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
            MaterialAlertDialogBuilder alertDialogBuilder = new MaterialAlertDialogBuilder(requireActivity(), R.style.MaterialAlertDialogCustom);
            LayoutInflater li = LayoutInflater.from(alertDialogBuilder.getContext());
            View promptsView = li.inflate(R.layout.prompt_editcontact, null);
            alertDialogBuilder.setView(promptsView);

            etContactName = promptsView.findViewById(R.id.etContactName);
            etWalletAddress = promptsView.findViewById(R.id.etWalletAddress);
            ivAvatar = promptsView.findViewById(R.id.ivAvatar);

            ImageButton bPasteAddress = promptsView.findViewById(R.id.bPasteAddress);
            bPasteAddress.setOnClickListener(view -> {
                final String clip = Helper.getClipBoardText(requireActivity());
                if (clip == null) return;

                Objects.requireNonNull(etWalletAddress.getEditText()).setText(clip);
            });

            Button btnSelectImage = promptsView.findViewById(R.id.btnSelectImage);
            btnSelectImage.setOnClickListener(view -> {
                applyChangesTmp();
                pickImage();
            });

            if (contact != null) {
                contactEdit = contact;

                if(contactEditBackup == null)
                    contactEditBackup = new Contact(contact);

                Objects.requireNonNull(etContactName.getEditText()).setText(contact.getName());
                Objects.requireNonNull(etWalletAddress.getEditText()).setText(contact.getAddress());

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
                Objects.requireNonNull(editDialog.getWindow()).setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            }

            refreshContacts();
        }

        public void pickImage() {
            if(Helper.getReadExternalStoragePermission(requireActivity()))
                mGetContentLauncher.launch("image/*");
        }

        public void updateAvatar(Bitmap bitmap) {
            if (ivAvatar != null) {
                ivAvatar.setImageBitmap(bitmap);
            }
        }

        public boolean isShowing() {
            return editDialog.isShowing();
        }
    }

    public static void pickImageImpl() {
        mGetContentLauncher.launch("image/*");
    }
}