/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2019 The Regents of the University of California
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
package org.linqs.psl.application.inference;

import org.linqs.psl.database.Database;
import org.linqs.psl.database.atom.PersistedAtomManager;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.model.Model;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.sgd.SGDOnlineReasoner;
import org.linqs.psl.reasoner.sgd.term.SGDOnlineTermStore;
import org.linqs.psl.reasoner.term.TermGenerator;
import org.linqs.psl.reasoner.term.TermStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SGDOnlineInference extends InferenceApplication {
    private static final Logger log = LoggerFactory.getLogger(SGDStreamingInference.class);

    public SGDOnlineInference(Model model, Database db) {
        super(model, db);
    }

    @Override
    protected Reasoner createReasoner() {
        return new SGDOnlineReasoner(atomManager);
    }

    @Override
    protected TermStore createTermStore() {
        return new SGDOnlineTermStore(model.getRules(), atomManager);
    }

    @Override
    protected GroundRuleStore createGroundRuleStore() {
        return null;
    }

    @Override
    protected TermGenerator createTermGenerator() {
        return null;
    }

    @Override
    public void close() {
        termStore.close();
        reasoner.close();

        termStore = null;
        reasoner = null;

        model = null;
        db = null;
    }
}
