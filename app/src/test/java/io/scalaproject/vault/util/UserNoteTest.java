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

import io.scalaproject.vault.data.UserNotes;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class UserNoteTest {

    @Test
    public void createFromTxNote_noNote() {
        UserNotes userNotes = new UserNotes("{xlato-iyrpxU,0.009BTC,mjn127C5wRQCULksMYMFHLp9UTdQuCfbZ9}");
        assertTrue("xlato-iyrpxU".equals(userNotes.xlatoKey));
        assertTrue("0.009".equals(userNotes.xlatoAmount));
        assertTrue("mjn127C5wRQCULksMYMFHLp9UTdQuCfbZ9".equals(userNotes.xlatoDestination));
        assertTrue(userNotes.note.isEmpty());
    }

    @Test
    public void createFromTxNote_withNote() {
        UserNotes userNotes = new UserNotes("{xlato-iyrpxU,0.009BTC,mjn127C5wRQCULksMYMFHLp9UTdQuCfbZ9} aNote");
        assertTrue("xlato-iyrpxU".equals(userNotes.xlatoKey));
        assertTrue("0.009".equals(userNotes.xlatoAmount));
        assertTrue("mjn127C5wRQCULksMYMFHLp9UTdQuCfbZ9".equals(userNotes.xlatoDestination));
        assertTrue("aNote".equals(userNotes.note));
    }

    @Test
    public void createFromTxNote_withNoteNoSpace() {
        UserNotes userNotes = new UserNotes("{xlato-iyrpxU,0.009BTC,mjn127C5wRQCULksMYMFHLp9UTdQuCfbZ9}aNote");
        assertTrue("xlato-iyrpxU".equals(userNotes.xlatoKey));
        assertTrue("0.009".equals(userNotes.xlatoAmount));
        assertTrue("mjn127C5wRQCULksMYMFHLp9UTdQuCfbZ9".equals(userNotes.xlatoDestination));
        assertTrue("aNote".equals(userNotes.note));
    }

    @Test
    public void createFromTxNote_brokenA() {
        String brokenNote = "{mrto-iyrpxU,0.009BTC,mjn127C5wRQCULksMYMFHLp9UTdQuCfbZ9}";
        UserNotes userNotes = new UserNotes(brokenNote);
        assertNull(userNotes.xlatoKey);
        assertNull(userNotes.xlatoAmount);
        assertNull(userNotes.xlatoDestination);
        assertTrue(brokenNote.equals(userNotes.note));
    }

    @Test
    public void createFromTxNote_brokenB() {
        String brokenNote = "{xlato-iyrpxU,0.009BTC,mjn127C5wRQCULksMYMFHLp9UTdQuCfbZ9";
        UserNotes userNotes = new UserNotes(brokenNote);
        assertNull(userNotes.xlatoKey);
        assertNull(userNotes.xlatoAmount);
        assertNull(userNotes.xlatoDestination);
        assertTrue(brokenNote.equals(userNotes.note));
    }

    @Test
    public void createFromTxNote_normal() {
        String aNote = "aNote";
        UserNotes userNotes = new UserNotes(aNote);
        assertNull(userNotes.xlatoKey);
        assertNull(userNotes.xlatoAmount);
        assertNull(userNotes.xlatoDestination);
        assertTrue(aNote.equals(userNotes.note));
    }

    @Test
    public void createFromTxNote_empty() {
        String aNote = "";
        UserNotes userNotes = new UserNotes(aNote);
        assertNull(userNotes.xlatoKey);
        assertNull(userNotes.xlatoAmount);
        assertNull(userNotes.xlatoDestination);
        assertTrue(aNote.equals(userNotes.note));
    }

    @Test
    public void createFromTxNote_null() {
        UserNotes userNotes = new UserNotes(null);
        assertNull(userNotes.xlatoKey);
        assertNull(userNotes.xlatoAmount);
        assertNull(userNotes.xlatoDestination);
        assertNotNull(userNotes.note);
        assertTrue(userNotes.note.isEmpty());
    }
}
