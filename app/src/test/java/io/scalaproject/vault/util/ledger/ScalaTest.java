/*
 * Copyright (c) 2019 m2049r
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

package io.scalaproject.vault.util.ledger;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ScalaTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void aRealTest() {
        String ledgerMnemonic = "weird cloth shiver soda music slight system slender daughter magic design story gospel bulk teach between spice kangaroo inside satoshi convince load morning income";
        String ledgerPassphrase = "";
        String scala_mnemonic = "maverick aimless laptop eating vibrate sensible bugs dreams " +
                "journal sincerely renting obtains boss mullet rustled cuddled " +
                "goblet nightly jailed hamburger getting benches haggled hesitate laptop";
        String test_scala = Scala.convert(ledgerMnemonic, ledgerPassphrase);
        assertTrue(scala_mnemonic.equals(test_scala));
    }

    @Test
    public void bRealTest() {
        String ledgerMnemonic = "weird cloth shiver soda music slight system slender daughter magic design story gospel bulk teach between spice kangaroo inside satoshi convince load morning income";
        String ledgerPassphrase = "secret";
        String scala_mnemonic = "surfer hemlock afraid huddle mostly yanks revamp pairing " +
                "northern yodel obliged vials azure huddle mowing melting " +
                "ruthless subtly civilian midst playful vats nabbing nowhere mowing";
        String test_scala = Scala.convert(ledgerMnemonic, ledgerPassphrase);
        assertTrue(scala_mnemonic.equals(test_scala));
    }

    @Test
    public void aTest() {
        String ledgerMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
        String ledgerPassphrase = "";
        String scala_mnemonic = "tavern judge beyond bifocals deepest mural onward dummy " +
                "eagle diode gained vacation rally cause firm idled " +
                "jerseys moat vigilant upload bobsled jobs cunning doing jobs";
        String test_scala = Scala.convert(ledgerMnemonic, ledgerPassphrase);
        assertTrue(scala_mnemonic.equals(test_scala));
    }

    @Test
    public void bTest() {
        String ledgerMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
        String ledgerPassphrase = "xyz";
        String scala_mnemonic = "gambit observant swiftly metro hoax pheasants agile oozed " +
                "fibula nuns picked stellar nibs cause gained phase " +
                "lettuce tomorrow pierce awakened pistons pheasants sorry tedious gambit";
        String test_scala = Scala.convert(ledgerMnemonic, ledgerPassphrase);
        assertTrue(scala_mnemonic.equals(test_scala));
    }

    @Test
    public void whitespaceTest() {
        String ledgerMnemonic = "   abandon  abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
        String ledgerPassphrase = "xyz";
        String scala_mnemonic = "gambit observant swiftly metro hoax pheasants agile oozed " +
                "fibula nuns picked stellar nibs cause gained phase " +
                "lettuce tomorrow pierce awakened pistons pheasants sorry tedious gambit";
        String test_scala = Scala.convert(ledgerMnemonic, ledgerPassphrase);
        assertTrue(scala_mnemonic.equals(test_scala));
    }

    @Test
    public void caseTest() {
        String ledgerMnemonic = "Abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
        String ledgerPassphrase = "xyz";
        String scala_mnemonic = "gambit observant swiftly metro hoax pheasants agile oozed " +
                "fibula nuns picked stellar nibs cause gained phase " +
                "lettuce tomorrow pierce awakened pistons pheasants sorry tedious gambit";
        String test_scala = Scala.convert(ledgerMnemonic, ledgerPassphrase);
        assertTrue(scala_mnemonic.equals(test_scala));
    }

    @Test
    public void nullTest() {
        String ledgerMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
        String ledgerPassphrase = "xyz";
        String test_scala = Scala.convert(ledgerMnemonic, ledgerPassphrase);
        assertNull(test_scala);
    }
}
