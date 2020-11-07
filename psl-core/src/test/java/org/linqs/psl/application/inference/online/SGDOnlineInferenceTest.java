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
package org.linqs.psl.application.inference.online;

import org.linqs.psl.OnlineTest;
import org.linqs.psl.TestModel;
import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.predicate.StandardPredicate;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class SGDOnlineInferenceTest {
    private TestModel.ModelInformation modelInfo;
    private Database inferDB;
    private OnlineInferenceThread onlineInferenceThread;

    public SGDOnlineInferenceTest() {
        modelInfo = null;
        inferDB = null;
    }

    @Before
    public void setup() {
        cleanup();

        Options.ONLINE.set(true);

        modelInfo = TestModel.getModel(true);

        // Close the predicates we are using.
        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();

        inferDB = modelInfo.dataStore.getDatabase(modelInfo.targetPartition, toClose, modelInfo.observationPartition);

        // Start up inference on separate thread.
        onlineInferenceThread = new OnlineInferenceThread();
        onlineInferenceThread.start();
    }

    @After
    public void cleanup() {
        if (onlineInferenceThread != null) {
            OnlineTest.clientSession("STOP");

            try {
                // Will wait 10 seconds for thread to finish otherwise will interrupt.
                onlineInferenceThread.join(10000);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }

            onlineInferenceThread.close();
            onlineInferenceThread = null;
        }

        if (inferDB != null) {
            inferDB.close();
            inferDB = null;
        }

        if (modelInfo != null) {
            modelInfo.dataStore.close();
            modelInfo = null;
        }
    }

    /**
     * Test that a non-existent atom results in the expected server response.
     */
    @Test
    public void testBadQuery() {
        // Check that a non-existent new atom results in the expected server response.
        OnlineTest.assertAtomValues( "Query\tFriends\tBob\tBob\nExit", new double[] {-1.0});
    }

    /**
     * Make sure that updates issued by client commands are made as expected.
     */
    @Test
    public void testUpdateObservation() {
        String commands =
                "UPDATE\tNice\tAlice\t0.0\n" +
                "Query\tNice\tAlice\n" +
                "EXIT";

        OnlineTest.assertAtomValues( commands, new double[] {0.0});
    }

    /**
     * Make sure that new atoms are added to model, are considered during inference, and
     * result in the expected groundings.
     */
//    @Test
//    public void testAddAtoms() {
//        // Check that adding atoms will not create new random variable atoms.
//        String commands =
//                "ADD\tRead\tPerson\tConnor\t1.0\n" +
//                "ADD\tRead\tNice\tConnor\t1.0\n" +
//                "Query\tFriends\tConnor\tAlice\n" +
//                "Query\tFriends\tConnor\tBob\n" +
//                "Query\tFriends\tAlice\tConnor\n" +
//                "Query\tFriends\tBob\tConnor\n" +
//                "EXIT";
//
//        OnlineTest.assertAtomValues(commands, new double[] {-1.0, -1.0, -1.0, -1.0});
//
//        // Reset model.
//        cleanup();
//        setup();
//
//        // Check that atoms are added to the model and hold the expected values.
//        commands =
//                "ADD\tRead\tPerson\tConnor\t1.0\n" +
//                "ADD\tRead\tNice\tConnor\t0.0\n" +
//                "ADD\tWrite\tFriends\tAlice\tConnor\n" +
//                "ADD\tWrite\tFriends\tConnor\tAlice\n" +
//                "ADD\tWrite\tFriends\tConnor\tBob\n" +
//                "ADD\tWrite\tFriends\tBob\tConnor\n" +
//                "Query\tPerson\tConnor\n" +
//                "Query\tNice\tConnor\n" +
//                "Query\tFriends\tAlice\tConnor\n" +
//                "Query\tFriends\tConnor\tAlice\n" +
//                "Query\tFriends\tConnor\tBob\n" +
//                "Query\tFriends\tBob\tConnor\n" +
//                "EXIT";
//
//        OnlineTest.assertAtomValues(commands, new double[] {1.0, 0.0, 0.0, 0.0, 0.0, 0.0});
//    }

    @Test
    public void testAtomDeleting() {
        // TODO (Charles): This order of commands will catch a behavior where there may be an unexpected outcome.
        //  The atom will not be deleted if there is an add and then a delete of the same atom before the atoms are
        //  activated. This behavior is also noted in streaming term store deleteAtom.
//        String commands =
//                "DELETE\tRead\tNice\tAlice\n" +
//                "ADD\tRead\tNice\tAlice\t1.0\n" +
//                "DELETE\tRead\tNice\tAlice\n" +
//                "EXIT";

        String commands =
                "DELETE\tRead\tNice\tAlice\n" +
                "DELETE\tRead\tPerson\tAlice\n" +
                "Query\tPerson\tAlice\n" +
                "Query\tNice\tAlice\n" +
                "Exit";

        double[] values = {-1.0, -1.0};

        OnlineTest.assertAtomValues(commands, values);
    }

    /**
     * There are three ways to effectively change the partition of an atom.
     * 1. Delete and then Add an atom.
     * 2. Add an atom with predicates and arguments that already exists in the model but with a different partition.
     * 3. Using the Observed or Unobserved actions for random variables and observations respectively. (preferred).
     */
    @Test
    public void testChangeAtomPartition() {
        String commands =
                "ADD\tRead\tFriends\tAlice\tBob\t0.5\n" +
                "Query\tFriends\tAlice\tBob\n" +
                "EXIT";

        double[] values = {0.5};

        OnlineTest.assertAtomValues(commands, values);

        // Reset model.
        cleanup();
        setup();

        commands =
                "DELETE\tWrite\tFriends\tAlice\tBob\n" +
                "ADD\tRead\tFriends\tAlice\tBob\t0.5\n" +
                "Query\tFriends\tAlice\tBob\n" +
                "EXIT";

        OnlineTest.assertAtomValues(commands, values);

        // Reset model.
        cleanup();
        setup();

        commands =
                "OBSERVE\tFriends\tAlice\tBob\t0.5\n" +
                "Query\tFriends\tAlice\tBob\n" +
                "EXIT";

        OnlineTest.assertAtomValues(commands, values);
    }

    private class OnlineInferenceThread extends Thread {
        SGDOnlineInference onlineInference;

        public OnlineInferenceThread() {
            onlineInference = new SGDOnlineInference(modelInfo.model.getRules(), inferDB);
        }

        @Override
        public void run() {
            onlineInference.inference();
        }

        public void close() {
            if (onlineInference != null) {
                onlineInference.close();
                onlineInference = null;
            }
        }
    }
}
