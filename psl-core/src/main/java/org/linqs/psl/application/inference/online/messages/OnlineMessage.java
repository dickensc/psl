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
package org.linqs.psl.application.inference.online.messages;

import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;

import java.util.UUID;

public abstract class OnlineMessage {
    private UUID identifier;

    public OnlineMessage(UUID identifier, String message) {
        this.identifier = identifier;
        parse(message);
    }

    /**
     * Parse the original client command.
     */
    protected abstract void parse(String string);

    public UUID getIdentifier() {
        return identifier;
    }

    /**
     * Parse an atom.
     * The given starting index should point to the predicate.
     */
    protected static AtomInfo parseAtom(String[] parts, int startIndex) {
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
