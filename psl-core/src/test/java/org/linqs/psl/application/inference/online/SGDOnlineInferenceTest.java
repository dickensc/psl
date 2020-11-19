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
import org.linqs.psl.application.inference.online.messages.actions.OnlineAction;
import org.linqs.psl.application.inference.online.messages.actions.controls.Exit;
import org.linqs.psl.application.inference.online.messages.actions.controls.QueryAtom;
import org.linqs.psl.application.inference.online.messages.actions.controls.Stop;
import org.linqs.psl.application.inference.online.messages.actions.model.updates.AddAtom;
import org.linqs.psl.application.inference.online.messages.actions.model.updates.DeleteAtom;
import org.linqs.psl.application.inference.online.messages.actions.model.updates.ObserveAtom;
import org.linqs.psl.application.inference.online.messages.actions.model.updates.UpdateObservation;
import org.linqs.psl.application.inference.online.messages.actions.template.modifications.AddRule;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Implication;
import org.linqs.psl.model.predicate.GroundingOnlyPredicate;
import org.linqs.psl.model.predicate.StandardPredicate;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.UniqueStringID;
import org.linqs.psl.model.term.Variable;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

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
            OnlineTest.clientSession(new Stop());

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
        BlockingQueue<OnlineAction> commands = new LinkedBlockingQueue<OnlineAction>();

        commands.add(new QueryAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Bob"), new UniqueStringID("Bob")}));
        commands.add(new Exit());

        // Check that a non-existent new atom results in the expected server response.
        OnlineTest.assertAtomValues(commands, new double[] {-1.0});
    }

    /**
     * Make sure that updates issued by client commands are made as expected.
     */
    @Test
    public void testUpdateObservation() {
        BlockingQueue<OnlineAction> commands = new LinkedBlockingQueue<OnlineAction>();

        commands.add(new UpdateObservation(StandardPredicate.get("Nice"), new Constant[]{new UniqueStringID("Alice")}, 0.0f));
        commands.add(new QueryAtom(StandardPredicate.get("Nice"), new Constant[]{new UniqueStringID("Alice")}));
        commands.add(new Exit());

        OnlineTest.assertAtomValues(commands, new double[] {0.0});
    }

//    /**
//     * Make sure that new atoms are added to model, are considered during inference, and
//     * result in the expected groundings.
//     */
//    @Test
//    public void testAddAtoms() {
//        BlockingQueue<OnlineAction> commands = new LinkedBlockingQueue<OnlineAction>();
//
//        // Check that adding atoms will not create new random variable atoms.
//        commands.add(new AddAtom("Read", StandardPredicate.get("Person"), new Constant[]{new UniqueStringID("Connor")}, 1.0f));
//        commands.add(new AddAtom("Read", StandardPredicate.get("Nice"), new Constant[]{new UniqueStringID("Connor")}, 1.0f));
//        commands.add(new QueryAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Connor"), new UniqueStringID("Alice")}));
//        commands.add(new QueryAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Connor"), new UniqueStringID("Bob")}));
//        commands.add(new QueryAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Connor")}));
//        commands.add(new QueryAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Bob"), new UniqueStringID("Connor")}));
//        commands.add(new Exit());
//
//        OnlineTest.assertAtomValues(commands, new double[] {-1.0, -1.0, -1.0, -1.0});
//
//        // Reset model.
//        cleanup();
//        setup();
//
//        // Check that atoms are added to the model and hold the expected values.
//        commands.add(new AddAtom("Read", StandardPredicate.get("Person"), new Constant[]{new UniqueStringID("Connor")}, 1.0f));
//        commands.add(new AddAtom("Read", StandardPredicate.get("Nice"), new Constant[]{new UniqueStringID("Connor")}, 0.0f));
//        commands.add(new AddAtom("Write", StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Connor")}, 0.0f));
//        commands.add(new AddAtom("Write", StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Connor"), new UniqueStringID("Alice")}, 0.0f));
//        commands.add(new AddAtom("Write", StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Bob"), new UniqueStringID("Connor")}, 0.0f));
//        commands.add(new AddAtom("Write", StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Connor"), new UniqueStringID("Bob")}, 0.0f));
//        commands.add(new QueryAtom(StandardPredicate.get("Person"), new Constant[]{new UniqueStringID("Connor")}));
//        commands.add(new QueryAtom(StandardPredicate.get("Nice"), new Constant[]{new UniqueStringID("Connor")}));
//        commands.add(new QueryAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Connor")}));
//        commands.add(new QueryAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Connor"), new UniqueStringID("Alice")}));
//        commands.add(new QueryAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Bob"), new UniqueStringID("Connor")}));
//        commands.add(new QueryAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Connor"), new UniqueStringID("Bob")}));
//        commands.add(new Exit());
//
//        OnlineTest.assertAtomValues(commands, new double[] {1.0, 0.0, 0.0, 0.0, 0.0, 0.0});
//    }

    @Test
    public void testRuleAddition() {
        BlockingQueue<OnlineAction> commands = new LinkedBlockingQueue<OnlineAction>();
        Rule newRule = new WeightedLogicalRule(
                new Implication(
                        new Conjunction(
                                new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Friends"), new Variable("A"), new Variable("B")),
                                new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Friends"), new Variable("B"), new Variable("C")),
                                new org.linqs.psl.model.atom.QueryAtom(GroundingOnlyPredicate.NotEqual, new Variable("A"), new Variable("B")),
                                new org.linqs.psl.model.atom.QueryAtom(GroundingOnlyPredicate.NotEqual, new Variable("A"), new Variable("C")),
                                new org.linqs.psl.model.atom.QueryAtom(GroundingOnlyPredicate.NotEqual, new Variable("B"), new Variable("C"))
                        ),
                        new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Friends"), new Variable("A"), new Variable("C"))
                ),
                5.0,
                true);

        commands.add(new AddRule(newRule));
        commands.add(new Exit());

        OnlineTest.clientSession(commands);
    }

    @Test
    public void testAtomDeleting() {
        BlockingQueue<OnlineAction> commands = new LinkedBlockingQueue<OnlineAction>();

        // TODO (Charles): This order of commands will catch a behavior where there may be an unexpected outcome.
        //  The atom will not be deleted if there is an add and then a delete of the same atom before the atoms are
        //  activated. This behavior is also noted in streaming term store deleteAtom.
//        commands.add(new DeleteAtom("Read", StandardPredicate.get("Nice"), new Constant[]{new UniqueStringID("Alice")}));
//        commands.add(new AddAtom("Read", StandardPredicate.get("Nice"), new Constant[]{new UniqueStringID("Alice")}, 1.0f));
//        commands.add(new DeleteAtom("Read", StandardPredicate.get("Nice"), new Constant[]{new UniqueStringID("Alice")}));
//        commands.add(new QueryAtom(StandardPredicate.get("Nice"), new Constant[]{new UniqueStringID("Alice")}));
//        commands.add(new Exit());
//
//        // Reset model.
//        cleanup();
//        setup();
//
//        double[] values = {-1.0};
//
//        OnlineTest.assertAtomValues(commands, values);

        commands.add(new DeleteAtom("Read", StandardPredicate.get("Nice"), new Constant[]{new UniqueStringID("Alice")}));
        commands.add(new DeleteAtom("Read", StandardPredicate.get("Person"), new Constant[]{new UniqueStringID("Alice")}));
        commands.add(new QueryAtom(StandardPredicate.get("Person"), new Constant[]{new UniqueStringID("Alice")}));
        commands.add(new QueryAtom(StandardPredicate.get("Nice"), new Constant[]{new UniqueStringID("Alice")}));
        commands.add(new Exit());

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
        BlockingQueue<OnlineAction> commands = new LinkedBlockingQueue<OnlineAction>();

        commands.add(new AddAtom("Read", StandardPredicate.get("Friends"),
                new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Bob")}, 0.5f));
        commands.add(new QueryAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Bob")}));
        commands.add(new Exit());

        double[] values = {0.5};

        OnlineTest.assertAtomValues(commands, values);

        // Reset model.
        cleanup();
        setup();

        commands.add(new DeleteAtom("Write", StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Bob")}));
        commands.add(new AddAtom("Read", StandardPredicate.get("Friends"),
                new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Bob")}, 0.5f));
        commands.add(new QueryAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Bob")}));
        commands.add(new Exit());

        OnlineTest.assertAtomValues(commands, values);

        // Reset model.
        cleanup();
        setup();

        commands.add(new ObserveAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Bob")}, 0.5f));
        commands.add(new QueryAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Bob")}));
        commands.add(new Exit());

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
