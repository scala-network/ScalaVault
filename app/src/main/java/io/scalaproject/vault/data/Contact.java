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

package io.scalaproject.vault.data;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.util.Comparator;

import io.scalaproject.vault.util.Helper;
import timber.log.Timber;

public class Contact {
    private String name = "";
    private String address = "";
    private Bitmap avatar = null;

    @Override
    public int hashCode() {
        return address.hashCode();
    }

    // Contacts are equal if they are the same address
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Contact)) return false;

        final Contact anotherContact = (Contact) other;
        return name.equals(((Contact) other).name) && address.equals(anotherContact.address);
    }

    static public Contact fromString(String contactString) {
        try {
            return new Contact(contactString);
        } catch (IllegalArgumentException ex) {
            Timber.w(ex);
            return null;
        }
    }

    public Contact(String name, String address) {
        if ((name.isEmpty()) || address.isEmpty())
            throw new IllegalArgumentException("contact name or address is empty");

        this.name = name;
        this.address = address;
    }

    public Contact(String contactString) {
        if ((contactString == null) || contactString.isEmpty())
            throw new IllegalArgumentException("contact is empty");

        String a[] = contactString.split(":");
        if (a.length == 2) {
            this.name = a[0];

            String av[] = a[1].split("@");
            this.address = av[0];

            if(av.length == 2) { // there is an avatar
                byte[] b = Base64.decode(av[1], Base64.DEFAULT);
                this.avatar = Helper.getCroppedBitmap(BitmapFactory.decodeByteArray(b, 0, b.length));
            }
        } else {
            throw new IllegalArgumentException("Too many :");
        }
    }

    public String toContactString() {
        return toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (!name.isEmpty() && !address.isEmpty()) {
            sb.append(name).append(":").append(address);
        }

        if(avatar != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            avatar.compress(Bitmap.CompressFormat.JPEG, 100, baos);

            byte[] compressImage = baos.toByteArray();
            String sEncodedImage = Base64.encodeToString(compressImage, Base64.DEFAULT);
            sb.append("@").append(sEncodedImage);
        }

        return sb.toString();
    }

    public String getName() {
        return this.name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }
    public void setAddress(String address) {
        this.address = address;
    }

    public Bitmap getAvatar() {
        return this.avatar;
    }
    public void setAvatar(Bitmap avatar) {
        this.avatar = avatar;
    }

    public Contact() {
        this.name = "";
        this.address = "";
        this.avatar = null;
    }

    public Contact(Contact anotherContact) {
        overwriteWith(anotherContact);
    }

    public void overwriteWith(Contact anotherContact) {
        name = anotherContact.name;
        address = anotherContact.address;
        avatar = anotherContact.avatar;
    }

    static public Comparator<Contact> ContactComparator = new Comparator<Contact>() {
        @Override
        public int compare(Contact o1, Contact o2) {
            return o1.getName().compareToIgnoreCase(o2.getName());
        }
    };
}
