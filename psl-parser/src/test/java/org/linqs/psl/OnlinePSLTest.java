/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2020 The Regents of the University of California
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
 */
package org.linqs.psl;

import org.linqs.psl.application.inference.online.messages.actions.OnlineAction;
import org.linqs.psl.parser.OnlineActionLoader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Utilities for testing PSL.
 */
public class OnlinePSLTest {
    /**
     * Assert that two strings are equal, possibly forcing alphabetization on the strings first.
     */
    public static void assertStringEquals(String expected, String actual, boolean alphabetize, String message) {
        if (alphabetize) {
            assertEquals(
                String.format("%s. (Before alphabetize) expected: [%s], found [%s].", message, expected, actual),
                sort(expected),
                sort(actual)
            );
        } else {
            assertEquals(
                String.format("%s. Expected: [%s], found [%s].", message, expected, actual),
                expected,
                actual
            );
        }
    }

    public static void assertActions(String input, String[] expectedActions) {
        List<OnlineAction> onlineActions = OnlineActionLoader.load(input);

        assertEquals("Size mismatch.", expectedActions.length, onlineActions.size());

        for (int i = 0; i < expectedActions.length; i++) {
            assertStringEquals(expectedActions[i], onlineActions.get(i).toString(), true, String.format("Rule %d mismatch", i));
        }
    }

    private static String sort(String string) {
        char[] chars = string.toCharArray();
        Arrays.sort(chars);
        return new String(chars);
    }
}
