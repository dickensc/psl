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
package org.linqs.psl.reasoner.sgd.term;

import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.term.online.OnlineIterator;
import org.linqs.psl.reasoner.term.online.OnlineTermStore;

import java.util.List;

/**
 * A term store that iterates over ground queries directly (obviating the GroundRuleStore).
 * Note that the iterators given by this class are meant to be exhaustd (at least the first time).
 * Remember that this class will internally iterate over an unknown number of groundings.
 * So interrupting the iteration can cause the term count to be incorrect.
 */
public class SGDOnlineTermStore extends OnlineTermStore<SGDOnlineObjectiveTerm> {
    public SGDOnlineTermStore(List<Rule> rules, AtomManager atomManager) {
        super(rules, atomManager, new SGDOnlineTermGenerator());
    }

    @Override
    protected boolean supportsRule(Rule rule) {
        // No special requirements for rules.
        return true;
    }

    @Override
    protected OnlineIterator<SGDOnlineObjectiveTerm> getInitialRoundIterator() {
        return new SGDOnlineInitialRoundIterator(
                this, rules, atomManager, termGenerator,
                termCache, termPool, termBuffer, volatileBuffer, pageSize);
    }

    @Override
    protected OnlineIterator<SGDOnlineObjectiveTerm> getCacheIterator() {
        return new SGDOnlineCacheIterator(
                this, false, termCache, termPool,
                termBuffer, volatileBuffer, shufflePage, shuffleMap, randomizePageAccess, numPages);
    }

    @Override
    protected OnlineIterator<SGDOnlineObjectiveTerm> getNoWriteIterator() {
        return new SGDOnlineCacheIterator(
                this, true, termCache, termPool,
                termBuffer, volatileBuffer, shufflePage, shuffleMap, randomizePageAccess, numPages);
    }
}
