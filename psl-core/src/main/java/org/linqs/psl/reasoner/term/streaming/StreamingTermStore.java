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
package org.linqs.psl.reasoner.term.streaming;

import org.linqs.psl.config.Options;
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.database.atom.OnlineAtomManager;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.reasoner.term.VariableTermStore;
import org.linqs.psl.reasoner.term.HyperplaneTermGenerator;
import org.linqs.psl.reasoner.term.OnlineTermStore;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.util.IteratorUtils;
import org.linqs.psl.util.SystemUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A term store that does not hold all the terms in memory, but instead keeps most terms on disk.
 * Variables are kept in memory, but terms are kept on disk.
 */
public abstract class StreamingTermStore<T extends ReasonerTerm> implements VariableTermStore<T, GroundAtom>, OnlineTermStore<T, GroundAtom> {
    private static final Logger log = LoggerFactory.getLogger(StreamingTermStore.class);

    public static final int INITIAL_PATH_CACHE_SIZE = 100;

    protected List<WeightedRule> rules;
    protected AtomManager atomManager;

    // Mapping from observed and random variable atoms to indices into the instance arrays.
    protected Map<GroundAtom, Integer> variables;

    // Matching arrays for variables and observations values and atoms.
    private float[] variableValues;
    // TODO (connor): Change variableAtoms to boolean array.
    private GroundAtom[] variableAtoms;
    private boolean[] deletedAtoms;
    private int variableIndex;
    private int numRandomVariables;

    protected List<String> termPagePaths;
    protected List<String> volatilePagePaths;

    protected boolean initialRound;
    protected Iterator<T> activeIterator;
    protected long seenTermCount;
    protected int numPages;

    protected HyperplaneTermGenerator<T, GroundAtom> termGenerator;

    protected int pageSize;
    protected String pageDir;
    protected boolean shufflePage;
    protected boolean randomizePageAccess;

    protected boolean warnRules;

    /**
     * The IO buffer for terms.
     * This buffer is only written on the first iteration,
     * and contains only components of the terms that do not change.
     */
    protected ByteBuffer termBuffer;

    /**
     * The IO buffer for volatile values.
     * These values change every iteration, and need to be updated.
     */
    protected ByteBuffer volatileBuffer;

    /**
     * Terms in the current page.
     * On the initial round, this is filled from DB and flushed to disk.
     * On subsequent rounds, this is filled from disk.
     */
    protected List<T> termCache;

    /**
     * Terms that we will reuse when we start pulling from the cache.
     * This should be a fill page's worth.
     * After the initial round, terms will bounce between here and the term cache.
     */
    protected List<T> termPool;

    /**
     * When we shuffle pages, we need to know how they were shuffled so the volatile
     * cache can be writtten in the same order.
     * So we will shuffle this list of sequential ints in the same order as the page.
     */
    protected int[] shuffleMap;

    protected boolean online;

    public StreamingTermStore(List<Rule> rules, AtomManager atomManager,
            HyperplaneTermGenerator<T, GroundAtom> termGenerator) {
        online = Options.ONLINE.getBoolean();
        pageSize = Options.STREAMING_TS_PAGE_SIZE.getInt();
        pageDir = Options.STREAMING_TS_PAGE_LOCATION.getString();
        shufflePage = Options.STREAMING_TS_SHUFFLE_PAGE.getBoolean();
        randomizePageAccess = Options.STREAMING_TS_RANDOMIZE_PAGE_ACCESS.getBoolean();
        warnRules = Options.STREAMING_TS_WARN_RULES.getBoolean();

        this.rules = new ArrayList<WeightedRule>();
        for (Rule rule : rules) {
            if (!rule.isWeighted()) {
                if (warnRules) {
                    log.warn("Streaming term stores do not support hard constraints: " + rule);
                }
                continue;
            }

            // HACK(eriq): This is not actually true,
            //  but I am putting it in place for efficiency reasons.
            if (((WeightedRule)rule).getWeight() < 0.0) {
                if (warnRules) {
                    log.warn("Streaming term stores do not support negative weights: " + rule);
                }
                continue;
            }

            if (!rule.supportsIndividualGrounding()) {
                if (warnRules) {
                    log.warn("Streaming term stores do not support rules that cannot individually ground (arithmetic rules with summations): " + rule);
                }
                continue;
            }

            if (!supportsRule(rule)) {
                if (warnRules) {
                    log.warn("Rule not supported: " + rule);
                }

                continue;
            }

            this.rules.add((WeightedRule)rule);
        }

        if (rules.size() == 0) {
            throw new IllegalArgumentException("Found no valid rules for a streaming term store.");
        }

        this.atomManager = atomManager;
        this.termGenerator = termGenerator;

        int atomCapacity = online ? atomManager.getCachedRVACount() + atomManager.getCachedObsCount() :
                atomManager.getCachedRVACount();
        ensureVariableCapacity(atomCapacity);

        termPagePaths = new ArrayList<String>(INITIAL_PATH_CACHE_SIZE);
        volatilePagePaths = new ArrayList<String>(INITIAL_PATH_CACHE_SIZE);

        initialRound = true;
        activeIterator = null;
        numPages = 0;
        seenTermCount = 0;
        variableIndex = 0;

        termBuffer = null;
        volatileBuffer = null;

        SystemUtils.recursiveDelete(pageDir);
        if (pageSize <= 1) {
            throw new IllegalArgumentException("Page size is too small.");
        }

        termCache = new ArrayList<T>(pageSize);
        termPool = new ArrayList<T>(pageSize);
        shuffleMap = new int[pageSize];

        (new File(pageDir)).mkdirs();
    }

    @Override
    public boolean isLoaded() {
        return !(initialRound || (online && ((OnlineAtomManager)atomManager).hasNewAtoms()));
    }

    public boolean isInitialRound() {
        return initialRound;
    }

    public boolean isCachedAtom(GroundAtom atom) {
        return variables.containsKey(atom);
    }

    @Override
    public int getNumVariables() {
        return numRandomVariables;
    }

    @Override
    public Iterable<GroundAtom> getVariables() {
        return variables.keySet();
    }

    @Override
    public GroundAtom[] getVariableAtoms() {
        return variableAtoms;
    }

    @Override
    public float[] getVariableValues() {
        return variableValues;
    }

    @Override
    public float getVariableValue(int index) {
        return variableValues[index];
    }

    @Override
    public int getVariableIndex(GroundAtom variable) {
        return variables.get(variable);
    }

    public boolean[] getDeletedAtoms() {
        return deletedAtoms;
    }

    @Override
    public void syncAtoms() {
        for (int i = 0; i < variableIndex; i++) {
            variableAtoms[i].setValue(variableValues[i]);
        }
    }

    @Override
    public synchronized GroundAtom createLocalVariable(GroundAtom atom) {
        if (isCachedAtom(atom)) {
            return atom;
        }

        // Got a new variable.
        if (variableIndex >= variableAtoms.length) {
            ensureVariableCapacity(variableAtoms.length * 2 + 1);
        }

        variables.put(atom, variableIndex);
        variableValues[variableIndex] = atom.getValue();
        variableAtoms[variableIndex] = atom;
        variableIndex++;

        if (atom instanceof RandomVariableAtom) {
            numRandomVariables++;
        }

        return atom;
    }

    @Override
    public void ensureVariableCapacity(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Variable capacity must be non-negative. Got: " + capacity);
        }

        if (variables == null || variables.size() == 0) {
            // If there are no variables, then (re-)allocate the variable storage.
            // The default load factor for Java HashSets is 0.75.
            variables = new HashMap<GroundAtom, Integer>((int)Math.ceil(capacity / 0.75));

            variableValues = new float[capacity];
            variableAtoms = new GroundAtom[capacity];
            deletedAtoms = new boolean[capacity];
        } else if (variableAtoms.length < capacity) {
            // Don't bother with small reallocations, if we are reallocating make a lot of room.
            if (capacity < variableAtoms.length * 2) {
                capacity = variableAtoms.length * 2;
            }

            // Reallocate and copy over variables.
            Map<GroundAtom, Integer> newVariables = new HashMap<GroundAtom, Integer>((int)Math.ceil(capacity / 0.75));
            newVariables.putAll(variables);
            variables = newVariables;

            variableValues = Arrays.copyOf(variableValues, capacity);
            variableAtoms = Arrays.copyOf(variableAtoms, capacity);
            deletedAtoms = Arrays.copyOf(deletedAtoms, capacity);
        }
    }

    // TODO(Charles): seenTermCount is not updated for deleted terms at this point so seenTermCount includes
    // the number of deleted terms
    @Override
    public long size() {
        return seenTermCount;
    }

    @Override
    public void add(GroundRule rule, T term) {
        throw new UnsupportedOperationException();
    }

    @Override
    public T get(long index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void ensureCapacity(long capacity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean deletedTerm(T term) { return false; }

    @Override
    public void addAtom(Predicate predicate, Constant[] arguments, float newValue, boolean readPartition) {
        GroundAtom atom;

        if (atomManager.getDatabase().hasCachedAtom(new QueryAtom(predicate, arguments))) {
            deleteAtom(predicate, arguments);
        }

        if (readPartition) {
            atom = ((OnlineAtomManager)atomManager).addObservedAtom(predicate, newValue, arguments);
        } else {
            atom = ((OnlineAtomManager)atomManager).addRandomVariableAtom((StandardPredicate) predicate, arguments);
        }

        createLocalVariable(atom);
    }

    @Override
    public void deleteAtom(Predicate predicate, Constant[] arguments) {
        GroundAtom atom = atomManager.getAtom(predicate, arguments);

        // TODO (Charles): If the atom being deleted has yet to be activated, for example
        //  if optimization has not been called since there was an add then a delete of the same atom,
        //  then the atom will have never of been added to variables, thus it will not be removed.
        // TODO (Charles): If the atom being deleted has yet to be "created" by the createLocalVariable method,
        //  then it will not exist in the variables map, and hence will not be deleted.
        if (variables.containsKey(atom)) {
            deletedAtoms[getVariableIndex(atom)] = true;
            if (atom instanceof RandomVariableAtom) {
                numRandomVariables--;
            }
            variables.remove(atom);
        }

        if (atomManager.getDatabase().hasCachedAtom(new QueryAtom(predicate, arguments))) {
            atomManager.getDatabase().deleteAtom(atom);
        }
    }

    @Override
    public synchronized void updateAtom(Predicate predicate, Constant[] arguments, float newValue) {
        // add the atom and newValue to the updates map for cache iterator
        GroundAtom atom = atomManager.getAtom(predicate, arguments);
        if (variables.containsKey(atom)) {
            variableValues[getVariableIndex(atom)] = newValue;
            variableAtoms[getVariableIndex(atom)].setValue(newValue);
        }
    }

    public String getTermPagePath(int index) {
        // Make sure the path is built.
        for (int i = termPagePaths.size(); i <= index; i++) {
            termPagePaths.add(Paths.get(pageDir, String.format("%08d_term.page", i)).toString());
        }

        return termPagePaths.get(index);
    }

    public String getVolatilePagePath(int index) {
        // Make sure the path is built.
        for (int i = volatilePagePaths.size(); i <= index; i++) {
            volatilePagePaths.add(Paths.get(pageDir, String.format("%08d_volatile.page", i)).toString());
        }

        return volatilePagePaths.get(index);
    }

    /**
     * A callback for the grounding iterator.
     * The ByterBuffers are here because of possible reallocation.
     */
    public void groundingIterationComplete(long termCount, int numPages, ByteBuffer termBuffer, ByteBuffer volatileBuffer) {
        seenTermCount += termCount;
        this.numPages = numPages;
        this.termBuffer = termBuffer;
        this.volatileBuffer = volatileBuffer;

        initialRound = false;
        activeIterator = null;
    }

    /**
     * A callback for the non-initial round iterator.
     */
    public void cacheIterationComplete() {
        activeIterator = null;
    }

    /**
     * Get an iterator that goes over all the terms for only reading.
     * Before this method can be called, a full iteration must have already been done.
     * (The cache will need to have been built.)
     */
    public Iterator<T> noWriteIterator() {
        if (activeIterator != null) {
            throw new IllegalStateException("Iterator already exists for this StreamingTermStore. Exhaust the iterator first.");
        }

        if (initialRound) {
            throw new IllegalStateException("A full iteration must have already been completed before asking for a read-only iterator.");
        }

        activeIterator = getNoWriteIterator();

        return activeIterator;
    }

    @Override
    public Iterator<T> iterator() {
        if (activeIterator != null) {
            throw new IllegalStateException("Iterator already exists for this StreamingTermStore. Exhaust the iterator first.");
        }

        if (initialRound) {
            activeIterator = getGroundingIterator();
        } else if (online && ((OnlineAtomManager)atomManager).hasNewAtoms()) {
            activeIterator = IteratorUtils.join(getCacheIterator(), getGroundingIterator());
        } else {
            activeIterator = getCacheIterator();
        }

        return activeIterator;
    }

    @Override
    public void clear() {
        initialRound = true;
        numPages = 0;

        if (activeIterator != null) {
            activeIterator = null;
        }

        if (variables != null) {
            variables.clear();
        }

        if (termCache != null) {
            termCache.clear();
        }

        if (termPool != null) {
            termPool.clear();
        }

        SystemUtils.recursiveDelete(pageDir);
    }

    @Override
    public void reset() {
        for (int i = 0; i < variableIndex; i++) {
            variableValues[i] = variableAtoms[i].getValue();
        }
    }

    @Override
    public void close() {
        clear();

        if (variables != null) {
            variables = null;
        }

        if (termBuffer != null) {
            termBuffer.clear();
            termBuffer = null;
        }

        if (volatileBuffer != null) {
            volatileBuffer.clear();
            volatileBuffer = null;
        }

        if (termCache != null) {
            termCache = null;
        }

        if (termPool != null) {
            termPool = null;
        }
    }

    @Override
    public void initForOptimization() {
    }

    @Override
    public void iterationComplete() {
    }

    @Override
    public void variablesExternallyUpdated() {
    }

    /**
     * Check if this term store supports this rule.
     * @return true if the rule is supported.
     */
    protected abstract boolean supportsRule(Rule rule);

    /**
     * Get an iterator that will perform grounding queries and write the initial pages to disk.
     */
    protected abstract StreamingIterator<T> getGroundingIterator();

    /**
     * Get an iterator that will read and write from disk.
     */
    protected abstract StreamingIterator<T> getCacheIterator();

    /**
     * Get an iterator that will not write to disk.
     */
    protected abstract StreamingIterator<T> getNoWriteIterator();

}
