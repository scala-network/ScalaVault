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

package io.scalaproject.vault.layout;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.Bitmap;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import io.scalaproject.vault.R;
import io.scalaproject.vault.data.Contact;
import io.scalaproject.vault.util.Helper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ContactInfoAdapter extends RecyclerView.Adapter<ContactInfoAdapter.ViewHolder> {

    public interface OnSelectContactListener {
        void onSelectContact(View view, Contact item);
    }

    public interface OnDeleteContactListener {
        void onDeleteContact(View view, Contact item);
    }

    private final List<Contact> contactItems = new ArrayList<>();
    private final OnSelectContactListener onSelectContactListener;
    private final OnDeleteContactListener onDeleteContactListener;

    private Boolean readonly;
    private Context context;

    public ContactInfoAdapter(Context context, Boolean readonly, OnSelectContactListener onSelectContactListener, OnDeleteContactListener onDeleteContactListener) {
        this.context = context;
        this.readonly = readonly;
        this.onSelectContactListener = onSelectContactListener;
        this.onDeleteContactListener = onDeleteContactListener;
    }

    @Override
    public @NonNull
    ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_contact, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final @NonNull ViewHolder holder, int position) {
        holder.bind(position);
    }

    @Override
    public int getItemCount() {
        return contactItems.size();
    }

    public void addContact(Contact contact) {
        if(!contactItems.contains(contact))
            contactItems.add(contact);

        dataSetChanged();
    }

    public void deleteContact(Contact contact) {
        contactItems.remove(contact);

        dataSetChanged();
    }

    public void dataSetChanged() {
        Collections.sort(contactItems, Contact.ContactComparator);
        notifyDataSetChanged();
    }

    public List<Contact> getContacts() {
        return contactItems;
    }

    public void setContacts(Collection<Contact> data) {
        contactItems.clear();
        if (data != null) {
            for (Contact contact : data) {
                if (!contactItems.contains(contact))
                    contactItems.add(contact);
            }
        }

        dataSetChanged();
    }

    class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        final TextView tvName;
        final TextView tvAddress;
        final ImageView ivDelete;
        final ImageView ivAvatar;

        Contact contactItem;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvAddress = itemView.findViewById(R.id.tvAddress);

            ivDelete = itemView.findViewById(R.id.ivDelete);
            ivDelete.setVisibility(readonly ? View.GONE : View.VISIBLE);

            ivAvatar = itemView.findViewById(R.id.ivAvatar);
        }

        void bind(final int position) {
            contactItem = contactItems.get(position);

            tvName.setText(contactItem.getName());
            tvAddress.setText(Helper.getPrettyAddress(contactItem.getAddress()));

            itemView.setOnClickListener(this);

            if(!readonly) {
                ivDelete.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        onDeleteContactListener.onDeleteContact(v, contactItems.get(position));
                    }
                });
            }

            Bitmap avatar = contactItem.getAvatar();
            if(avatar != null) {
                int dim = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, itemView.getResources().getDisplayMetrics());
                ivAvatar.getLayoutParams().height = dim;
                ivAvatar.getLayoutParams().width = dim;

                ivAvatar.setImageBitmap(contactItem.getAvatar());
            }
            else {
                int dim = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36, itemView.getResources().getDisplayMetrics());
                ivAvatar.getLayoutParams().height = dim;
                ivAvatar.getLayoutParams().width = dim;

                ivAvatar.setImageBitmap(Helper.getBitmap(context, R.drawable.ic_contact_avatar));
            }
        }

        @Override
        public void onClick(View view) {
            if (onSelectContactListener != null) {
                int position = getAdapterPosition(); // gets item position
                if (position != RecyclerView.NO_POSITION) { // Check if an item was deleted, but the user clicked it before the UI removed it
                    onSelectContactListener.onSelectContact(view, contactItems.get(position));
                }
            }
        }
    }
}
