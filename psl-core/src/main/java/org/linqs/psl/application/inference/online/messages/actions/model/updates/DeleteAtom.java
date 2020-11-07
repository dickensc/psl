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
package org.linqs.psl.application.inference.online.messages.actions.model.updates;

import org.linqs.psl.application.inference.online.messages.actions.OnlineAction;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.util.StringUtils;

/**
 * Delete an atom from the existing model.
 * String format: UUID DELETE <READ/WRITE> <predicate> <args> ...
 */
public class DeleteAtom extends OnlineAction {
    private StandardPredicate predicate;
    private String partition;
    private Constant[] arguments;

    public DeleteAtom(String partition, Atom atom) {
        super();
        this.predicate = (StandardPredicate) atom.getPredicate();
        this.arguments = (Constant[]) atom.getArguments();
        this.partition = partition.toUpperCase();
    }

    public StandardPredicate getPredicate() {
        return predicate;
    }

    public String getPartitionName() {
        return partition;
    }

    public Constant[] getArguments() {
        return arguments;
    }

    @Override
    public String toString() {
        return String.format(
                "DELETE\t%s\t%s\t%s",
                partition, predicate.getName(),
                StringUtils.join("\t", arguments).replace("'", ""));
    }
}
