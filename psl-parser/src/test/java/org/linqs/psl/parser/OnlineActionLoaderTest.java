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
package org.linqs.psl.parser;

import org.junit.Before;
import org.junit.Test;
import org.linqs.psl.OnlinePSLTest;
import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.ConstantType;

public class OnlineActionLoaderTest {
    private DataStore dataStore;

    private StandardPredicate singlePredicate;
    private StandardPredicate doublePredicate;

    @Before
    public void setup() {
        dataStore = new RDBMSDataStore(new H2DatabaseDriver(H2DatabaseDriver.Type.Memory, this.getClass().getName(), true));

        singlePredicate = StandardPredicate.get("Single", ConstantType.UniqueStringID);
        dataStore.registerPredicate(singlePredicate);

        doublePredicate = StandardPredicate.get("Double", ConstantType.UniqueStringID, ConstantType.UniqueStringID);
        dataStore.registerPredicate(doublePredicate);
    }

    @Test
    public void testAddAtom() {
        String input =
            "AddAtom Read SINGLE('A') 1.0\n" +
            "AddAtom Write DOUBLE('A', 'B')";
        String[] expected = new String[]{
            "ADD\tREAD\tSINGLE\tA\t1.00",
            "ADD\tWRITE\tDOUBLE\tA\tB"
        };

        OnlinePSLTest.assertActions(input, expected);
    }

    @Test
    public void testDeleteAtom() {
        String input =
                "DeleteAtom Read SINGLE('A')\n" +
                "DeleteAtom Write DOUBLE('A', 'B')";
        String[] expected = new String[]{
                "DELETE\tREAD\tSINGLE\tA",
                "DELETE\tWRITE\tDOUBLE\tA\tB"
        };

        OnlinePSLTest.assertActions(input, expected);
    }

    @Test
    public void testObserveAtom() {
        String input =
                "ObserveAtom SINGLE('A') 0.5\n" +
                "ObserveAtom DOUBLE('A', 'B') 1";
        String[] expected = new String[]{
                "OBSERVE\tSINGLE\tA\t0.50",
                "OBSERVE\tDOUBLE\tA\tB\t1.00"
        };

        OnlinePSLTest.assertActions(input, expected);
    }

    @Test
    public void testUpdateObservation() {
        String input =
                "UpdateAtom SINGLE('A') 0.5\n" +
                "UpdateAtom DOUBLE('A', 'B') 1";
        String[] expected = new String[]{
                "UPDATE\tSINGLE\tA\t0.50",
                "UPDATE\tDOUBLE\tA\tB\t1.00"
        };

        OnlinePSLTest.assertActions(input, expected);
    }

    @Test
    public void testExit() {
        String input =
                "Exit";
        String[] expected = new String[]{
                "EXIT"
        };

        OnlinePSLTest.assertActions(input, expected);
    }

    @Test
    public void testQueryAtom() {
        String input =
                "QueryAtom SINGLE('A')\n" +
                "QueryAtom DOUBLE('A', 'B')";
        String[] expected = new String[]{
                "QUERY\tSINGLE\tA",
                "QUERY\tDOUBLE\tA\tB"
        };

        OnlinePSLTest.assertActions(input, expected);
    }

    @Test
    public void testStop() {
        String input =
                "Stop";
        String[] expected = new String[]{
                "STOP"
        };

        OnlinePSLTest.assertActions(input, expected);
    }

    @Test
    public void testSync() {
        String input =
                "Sync";
        String[] expected = new String[]{
                "SYNC"
        };

        OnlinePSLTest.assertActions(input, expected);
    }

    @Test
    public void testWriteInferredPredicates() {
        String input =
                "WriteInferredPredicates 'file/path'";
        String[] expected = new String[]{
                "WRITE\tfile/path"
        };

        OnlinePSLTest.assertActions(input, expected);
    }

    @Test
    public void testAddRule() {
        String input =
                "AddRule 1: Single(A) & Double(A, B) >> Single(B) ^2";
        String[] expected = new String[]{
                "ADDRULE\t1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) ^2"
        };

        OnlinePSLTest.assertActions(input, expected);
    }
}
