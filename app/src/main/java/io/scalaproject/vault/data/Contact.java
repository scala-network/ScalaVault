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

import io.scalaproject.levin.scanner.Dispatcher;
import io.scalaproject.vault.model.NetworkType;
import io.scalaproject.vault.model.WalletManager;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.Comparator;

import timber.log.Timber;

public class Contact {
    private String name = "";
    private String address = "";

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

    Contact(String contactString) {
        if ((contactString == null) || contactString.isEmpty())
            throw new IllegalArgumentException("contact is empty");

        String a[] = contactString.split(":");
        if (a.length == 2) {
            this.name = a[0];
            this.address = a[1];
        } else {
            throw new IllegalArgumentException("Too many @");
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

    public Contact() {
        this.name = "";
        this.address = "";
    }

    public Contact(Contact anotherContact) {
        overwriteWith(anotherContact);
    }

    public void overwriteWith(Contact anotherContact) {
        name = anotherContact.name;
        address = anotherContact.address;
    }

    static public Comparator<Contact> ContactComparator = new Comparator<Contact>() {
        @Override
        public int compare(Contact o1, Contact o2) {
            return o1.getName().compareToIgnoreCase(o2.getName());
        }
    };
}
