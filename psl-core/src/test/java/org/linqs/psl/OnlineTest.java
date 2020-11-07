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

import org.linqs.psl.application.inference.online.OnlineClient;
import org.linqs.psl.application.inference.online.messages.actions.OnlineAction;
import org.linqs.psl.application.inference.online.messages.actions.controls.Exit;
import org.linqs.psl.application.inference.online.messages.actions.controls.QueryAtom;
import org.linqs.psl.application.inference.online.messages.actions.controls.Stop;
import org.linqs.psl.application.inference.online.messages.actions.controls.Sync;
import org.linqs.psl.application.inference.online.messages.actions.controls.WriteInferredPredicates;
import org.linqs.psl.application.inference.online.messages.actions.model.updates.AddAtom;
import org.linqs.psl.application.inference.online.messages.actions.model.updates.DeleteAtom;
import org.linqs.psl.application.inference.online.messages.actions.model.updates.ObserveAtom;
import org.linqs.psl.application.inference.online.messages.actions.model.updates.UpdateObservation;
import org.linqs.psl.application.inference.online.messages.actions.template.modifications.AddRule;
import org.linqs.psl.application.inference.online.messages.responses.OnlineResponse;
import org.linqs.psl.application.inference.online.messages.responses.QueryAtomResponse;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.Assert.assertEquals;

/**
 * Utilities for Online PSL Inference Tests.
 */
public class OnlineTest {
    public static BlockingQueue<OnlineAction> parseCommands(String commands) {
        BlockingQueue<OnlineAction> onlineActions = new LinkedBlockingQueue<OnlineAction>();

        for (String actionString : commands.split("\n")) {
            try {
                onlineActions.put(getAction(actionString));
            } catch (InterruptedException ex) {
                // Ignore.
            }
        }

        return onlineActions;
    }

    public static List<OnlineResponse> clientSession(String commands) {
        OnlineClient onlineClient = null;
        List<OnlineResponse> sessionOutput = new ArrayList<OnlineResponse>();
        BlockingQueue<OnlineAction> onlineActions = parseCommands(commands);

        onlineClient = new OnlineClient(new PrintStream(new ByteArrayOutputStream()), onlineActions, sessionOutput);
        Thread onlineClientThread = new Thread(onlineClient);
        onlineClientThread.start();

        try {
            onlineClientThread.join();
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }

        return sessionOutput;
    }

    public static void assertAtomValues(String commands, double[] values) {
        List<OnlineResponse> onlineResponses = null;

        onlineResponses = clientSession(commands);

        int i = 0;
        for (OnlineResponse onlineResponse : onlineResponses) {
            if (onlineResponse instanceof QueryAtomResponse) {
                assertEquals(values[i], ((QueryAtomResponse)onlineResponse).getAtomValue(), 0.1);
                i++;
            }
        }

        assertEquals(i, values.length);
    }

    /**
     * Construct an OnlineAction given the name and necessary information.
     */
    public static OnlineAction getAction(String clientCommand) {
        String actionClass = clientCommand.split("\t")[0].trim();

        if (actionClass.equalsIgnoreCase("Add")) {
            return parseAddAtom(clientCommand);
        } else if (actionClass.equalsIgnoreCase("AddRule")) {
            return parseAddRule(clientCommand);
        } else if (actionClass.equalsIgnoreCase("Observe")) {
            return parseObserveAtom(clientCommand);
        } else if (actionClass.equalsIgnoreCase("Stop")) {
            return parseStop(clientCommand);
        } else if (actionClass.equalsIgnoreCase("Sync")) {
            return parseSync(clientCommand);
        } else if (actionClass.equalsIgnoreCase("Exit")) {
            return parseExit(clientCommand);
        } else if (actionClass.equalsIgnoreCase("Delete")) {
            return parseDeleteAtom(clientCommand);
        } else if (actionClass.equalsIgnoreCase("Update")) {
            return parseUpdateObservation(clientCommand);
        } else if (actionClass.equalsIgnoreCase("Query")) {
            return parseQueryAtom(clientCommand);
        } else if (actionClass.equalsIgnoreCase("Write")) {
            return parseWriteInferredPredicates(clientCommand);
        } else {
            throw new IllegalArgumentException("Unknown online action: '" + actionClass + "'.");
        }
    }

    private static AddAtom parseAddAtom(String string) {
        String partition;

        String[] parts = string.split("\t");

        assert(parts[0].equalsIgnoreCase("add"));

        if (parts.length < 4) {
            throw new IllegalArgumentException("Not enough arguments.");
        }

        partition = parts[1].toUpperCase();
        if (!(partition.equals("READ") || partition.equals("WRITE"))) {
            throw new IllegalArgumentException("Expecting 'READ' or 'WRITE' for partition, got '" + parts[1] + "'.");
        }

        AtomInfo atomInfo = parseAtom(parts, 2);

        if (partition.equals("READ")) {
            return new AddAtom(partition, new ObservedAtom(atomInfo.predicate, atomInfo.arguments, atomInfo.value), atomInfo.value);
        } else {
            return new AddAtom(partition, new RandomVariableAtom(atomInfo.predicate, atomInfo.arguments, atomInfo.value), atomInfo.value);
        }
    }

    private static DeleteAtom parseDeleteAtom(String string) {
        String partition;

        String[] parts = string.split("\t");

        assert(parts[0].equalsIgnoreCase("delete"));

        if (parts.length < 4) {
            throw new IllegalArgumentException("Not enough arguments.");
        }

        partition = parts[1].toUpperCase();
        if (!(partition.equals("READ") || partition.equals("WRITE"))) {
            throw new IllegalArgumentException("Expecting 'READ' or 'WRITE' for partition, got '" + parts[1] + "'.");
        }

        AtomInfo atomInfo = parseAtom(parts, 2);

        if (parts.length == (3 + atomInfo.predicate.getArity() + 1)) {
            throw new IllegalArgumentException("Values cannot be supplied to DELETE actions.");
        }

        if (partition.equals("READ")) {
            return new DeleteAtom(partition, new ObservedAtom(atomInfo.predicate, atomInfo.arguments, atomInfo.value));
        } else {
            return new DeleteAtom(partition, new RandomVariableAtom(atomInfo.predicate, atomInfo.arguments, atomInfo.value));
        }
    }

    private static ObserveAtom parseObserveAtom(String string) {
        String[] parts = string.split("\t");

        assert(parts[0].equalsIgnoreCase("observe"));

        if (parts.length < 3) {
            throw new IllegalArgumentException("Not enough arguments.");
        }

        AtomInfo atomInfo = parseAtom(parts, 1);

        return new ObserveAtom(new RandomVariableAtom(atomInfo.predicate, atomInfo.arguments, atomInfo.value), atomInfo.value);
    }

    private static UpdateObservation parseUpdateObservation(String string) {
        String[] parts = string.split("\t");

        assert(parts[0].equalsIgnoreCase("update"));

        if (parts.length < 3) {
            throw new IllegalArgumentException("Not enough arguments.");
        }

        AtomInfo atomInfo = parseAtom(parts, 1);

        return new UpdateObservation(new ObservedAtom(atomInfo.predicate, atomInfo.arguments, atomInfo.value), atomInfo.value);
    }

    private static Exit parseExit(String string) {
        String[] parts = string.split("\t");

        assert(parts[0].equalsIgnoreCase("exit"));

        return new Exit();
    }

    private static QueryAtom parseQueryAtom(String string) {
        String[] parts = string.split("\t");

        assert(parts[0].equalsIgnoreCase("query"));

        if (parts.length < 2) {
            throw new IllegalArgumentException("Not enough arguments.");
        }

        AtomInfo atomInfo = parseAtom(parts, 1);

        return new QueryAtom(new ObservedAtom(atomInfo.predicate, atomInfo.arguments, 1.0f));
    }

    private static Stop parseStop(String string) {
        String[] parts = string.split("\t");

        assert(parts[0].equalsIgnoreCase("stop"));

        return new Stop();
    }

    private static Sync parseSync(String string) {
        String[] parts = string.split("\t");

        assert(parts[0].equalsIgnoreCase("sync"));

        return new Sync();
    }

    private static WriteInferredPredicates parseWriteInferredPredicates(String string) {
        String outputDirectoryPath = null;

        String[] parts = string.split("\t");

        assert(parts[0].equalsIgnoreCase("write"));

        if (parts.length > 2) {
            throw new IllegalArgumentException("Too many arguments.");
        }

        outputDirectoryPath = null;
        if (parts.length == 2) {
            outputDirectoryPath = parts[1];
        }

        return new WriteInferredPredicates(outputDirectoryPath);
    }

    private static AddRule parseAddRule(String string) {
        String[] parts = string.split("\t", 2);

        assert(parts[0].equalsIgnoreCase("AddRule"));

//        ModelLoader.loadRule(string);

        return new AddRule(null);
    }

    /**
     * Parse an atom.
     * The given starting index should point to the predicate.
     */
    private static AtomInfo parseAtom(String[] parts, int startIndex) {
        StandardPredicate predicate = StandardPredicate.get(parts[startIndex]);
        if (predicate == null) {
            throw new IllegalArgumentException("Unknown predicate: " + parts[startIndex] + ".");
        }

        // The final +1 is for the optional value.
        if (parts.length > (startIndex + 1 + predicate.getArity() + 1)) {
            throw new IllegalArgumentException("Too many arguments.");
        }

        float value = 1.0f;
        if (parts.length == (startIndex + 1 + predicate.getArity() + 1)) {
            value = Float.valueOf(parts[parts.length - 1]);
        }

        Constant[] arguments = new Constant[predicate.getArity()];
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = ConstantType.getConstant(parts[startIndex + 1 + i], predicate.getArgumentType(i));
        }

        return new AtomInfo(predicate, arguments, value);
    }

    protected static class AtomInfo {
        public StandardPredicate predicate;
        public Constant[] arguments;
        public float value;

        AtomInfo(StandardPredicate predicate, Constant[] arguments, float value) {
            this.predicate = predicate;
            this.arguments = arguments;
            this.value = value;
        }
    }
}
