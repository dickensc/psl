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
package org.linqs.psl.reasoner.sgd;

import org.linqs.psl.config.Config;
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.term.UniqueStringID;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.reasoner.function.FunctionTerm;
import org.linqs.psl.reasoner.function.GeneralFunction;
import org.linqs.psl.reasoner.sgd.term.SGDOnlineObjectiveTerm;
import org.linqs.psl.reasoner.sgd.term.SGDOnlineTermStore;
import org.linqs.psl.reasoner.term.Online;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.reasoner.term.VariableTermStore;
import org.linqs.psl.reasoner.term.online.OnlineTermStore;
import org.linqs.psl.util.IteratorUtils;
import org.linqs.psl.util.MathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;

/**
 * Uses an SGD optimization method to optimize its GroundRules.
 */
public class SGDOnlineReasoner implements Reasoner {
    private static final Logger log = LoggerFactory.getLogger(SGDOnlineReasoner.class);

    /**
     * Prefix of property keys used by this class.
     */
    public static final String CONFIG_PREFIX = "sgd";

    /**
     * The maximum number of iterations of SGD to perform in a round of inference.
     */
    public static final String MAX_ITER_KEY = CONFIG_PREFIX + ".maxiterations";
    public static final int MAX_ITER_DEFAULT = 200;

    /**
     * Stop if the objective has not changed since the last logging period (see LOG_PERIOD).
     */
    public static final String OBJECTIVE_BREAK_KEY = CONFIG_PREFIX + ".objectivebreak";
    public static final boolean OBJECTIVE_BREAK_DEFAULT = true;

    /**
     * The maximum number of iterations of SGD to perform in a round of inference.
     */
    public static final String OBJ_TOL_KEY = CONFIG_PREFIX + ".tolerance";
    public static final float OBJ_TOL_DEFAULT = 0.00001f;


    public static final String LEARNING_RATE_KEY = CONFIG_PREFIX + ".learningrate";
    public static final float LEARNING_RATE_DEFAULT = 1.0f;

    public static final String PRINT_OBJECTIVE = CONFIG_PREFIX + ".printobj";
    public static final boolean PRINT_OBJECTIVE_DEFAULT = true;

    /**
     * Print the objective before any optimization.
     * Note that this will require a pass through all the terms,
     * and therefore may affect performance.
     * Has no effect if printobj is false.
     */
    public static final String PRINT_INITIAL_OBJECTIVE_KEY = CONFIG_PREFIX + ".printinitialobj";
    public static final boolean PRINT_INITIAL_OBJECTIVE_DEFAULT = false;

    private int maxIter;

    private float tolerance;
    private boolean printObj;
    private boolean printInitialObj;
    private boolean objectiveBreak;

    private AtomManager atomManager;

    public SGDOnlineReasoner(AtomManager atomManager) {
        atomManager = atomManager;
        maxIter = Config.getInt(MAX_ITER_KEY, MAX_ITER_DEFAULT);
        objectiveBreak = Config.getBoolean(OBJECTIVE_BREAK_KEY, OBJECTIVE_BREAK_DEFAULT);
        printObj = Config.getBoolean(PRINT_OBJECTIVE, PRINT_OBJECTIVE_DEFAULT);
        printInitialObj = Config.getBoolean(PRINT_INITIAL_OBJECTIVE_KEY, PRINT_INITIAL_OBJECTIVE_DEFAULT);
        tolerance = Config.getFloat(OBJ_TOL_KEY, OBJ_TOL_DEFAULT);
    }

    public int getMaxIter() {
        return maxIter;
    }

    public void setMaxIter(int maxIter) {
        this.maxIter = maxIter;
    }

    @Override
    public void optimize(TermStore baseTermStore) {
        if (!(baseTermStore instanceof VariableTermStore)) {
            throw new IllegalArgumentException("SGDReasoner requires an VariableTermStore (found " + baseTermStore.getClass().getName() + ").");
        }

        @SuppressWarnings("unchecked")
         SGDOnlineTermStore termStore = (SGDOnlineTermStore)baseTermStore;

        // This must be called after the term store has to correct variable capacity.
        // A reallocation can cause this array to become out-of-date.
        float[] variableValues = termStore.getVariableValues();

        float objective = -1.0f;
        float oldObjective = Float.POSITIVE_INFINITY;

        int iteration = 1;
        if (printObj) {
            log.trace("objective:Iterations,Time(ms),Objective");

            if (printInitialObj) {
                objective = computeObjective(termStore, variableValues);
                log.trace("objective:{},{},{}", 0, 0, objective);
            }
        }

        long time = 0;
        while (iteration <= maxIter
                && (!objectiveBreak || (iteration == 1 || !MathUtils.equals(objective, oldObjective, tolerance)))) {
            long start = System.currentTimeMillis();

            for (SGDOnlineObjectiveTerm term : termStore) {
                term.minimize(iteration, variableValues);
            }

            long end = System.currentTimeMillis();
            oldObjective = objective;
            objective = computeObjective(termStore, variableValues);
            time += end - start;

            if (printObj) {
                log.info("objective:{},{},{}", iteration, time, objective);
            }

            iteration++;
        }

        termStore.syncAtoms();

        /**
         * TEST Online read and write new term
         * **/

        // Harcoded terms to append to our cache pages

        // Construct new term from example simple-acquaintances
        // 20: Lived(P1, L) & Lived(P2, L) & (P1 != P2) -> Knows(P1, P2) ^2
        // Lived: 0 0 1
        // Lived: 1 0 1
        // Knows: 0 1

        Online<RandomVariableAtom> newTerm = new Online<>(RandomVariableAtom.class, 1, 2, -1.0f * (float)2);
        GeneralFunction newFTerm = new GeneralFunction(true, true, 1);
        // Might not work because we are creating new constants instead of grabbing existing instances
        RandomVariableAtom rvAtom = (RandomVariableAtom)atomManager.getAtom(Predicate.get("Knows"), new UniqueStringID("0"), new UniqueStringID("1"));
        newFTerm.add(-1.0f, rvAtom);

        ObservedAtom obsAtom_1 = (ObservedAtom) atomManager.getAtom(Predicate.get("Lived"), new UniqueStringID("0"), new UniqueStringID("0"));
        ObservedAtom obsAtom_2 = (ObservedAtom) atomManager.getAtom(Predicate.get("Lived"), new UniqueStringID("1"), new UniqueStringID("0"));
        obsAtom_1.setValue(1);
        obsAtom_2.setValue(1);

        newFTerm.add(1.0f, obsAtom_1);
        newFTerm.add(1.0f, obsAtom_2);

        // create term
        Online<RandomVariableAtom> online = new Online<>(RandomVariableAtom.class, newFTerm.size(), newFTerm.observedSize(), -1.0f * (float)newFTerm.getConstant());

        for (int i = 0; i < newFTerm.size(); i++) {
            float coefficient = (float)newFTerm.getCoefficient(i);
            FunctionTerm term = newFTerm.getTerm(i);

            if (term instanceof RandomVariableAtom) {
                RandomVariableAtom variable = termStore.createLocalVariable((RandomVariableAtom)term);

                // Check to see if we have seen this variable before in this online.
                // Note that we are checking for existence in a List (O(n)), but there are usually a small number of
                // variables per online.
                int localIndex = online.indexOfVariable((RandomVariableAtom)variable);
                if (localIndex != -1) {

                    // If the local variable already exists, just add to its coefficient.
                    online.appendCoefficient(localIndex, coefficient);
                } else {
                    online.addTerm((RandomVariableAtom)variable, coefficient);
                }
            } else if (term.isConstant()) {
                // Subtracts because online is stored as coeffs^T * x = constant.
                online.setConstant(online.getConstant() - (float)(coefficient * term.getValue()));
            } else {
                throw new IllegalArgumentException("Unexpected summand: " + newFTerm + "[" + i + "] (" + term + ").");
            }
        }

        // add observed terms to term
        for (int i = 0; i < newFTerm.observedSize(); i++) {
            float coefficient = (float)newFTerm.getObservedCoefficient(i);
            FunctionTerm term = newFTerm.getObservedTerm(i);

            if (term instanceof ObservedAtom) {
                ObservedAtom variable = termStore.createLocalObservedVariable((ObservedAtom)term);

                // Check to see if we have seen this variable before in this online.
                // Note that we are checking for existence in a List (O(n)), but there are usually a small number of
                // variables per online.
                int localIndex = online.indexOfObserved((ObservedAtom)variable);
                if (localIndex != -1) {
                    // If the local variable already exists, just add to its coefficient.
                    online.appendObservedCoefficient(localIndex, coefficient);
                } else {
                    online.addObservedTerm((ObservedAtom) variable, coefficient);
                }
            } else {
                throw new IllegalArgumentException("Unexpected summand: " + newFTerm + "[" + i + "] (" + term + ").");
            }
        }
        SGDOnlineObjectiveTerm objTerm = new SGDOnlineObjectiveTerm(termStore, true, true,
                online, 20, LEARNING_RATE_DEFAULT);
        termStore.add(null, objTerm);

        // out of map state, continue optimization
        variableValues = termStore.getVariableValues();
        iteration = 1;
        if (printObj) {
            log.trace("objective:Iterations,Time(ms),Objective");

            if (printInitialObj) {
                objective = computeObjective(termStore, variableValues);
                log.trace("objective:{},{},{}", 0, 0, objective);
            }
        }

        while (iteration <= maxIter
                && (!objectiveBreak || (iteration == 1 || !MathUtils.equals(objective, oldObjective, tolerance)))) {
            long start = System.currentTimeMillis();

            for (SGDOnlineObjectiveTerm term : termStore) {
                term.minimize(iteration, variableValues);
            }

            long end = System.currentTimeMillis();
            oldObjective = objective;
            objective = computeObjective(termStore, variableValues);
            time += end - start;

            if (printObj) {
                log.info("objective:{},{},{}", iteration, time, objective);
            }

            iteration++;
        }

        termStore.syncAtoms();

        /**
         * TEST Online read and write new term complete
         * **/

        log.info("Optimization completed in {} iterations. Objective.: {}", iteration - 1, objective);
        log.debug("Optimized with {} variables and {} terms.", termStore.getNumVariables(), termStore.size());
    }

    public float computeObjective(VariableTermStore<SGDOnlineObjectiveTerm, RandomVariableAtom> termStore, float[] variableValues) {
        float objective = 0.0f;

        // If possible, use a readonly iterator.
        Iterator<SGDOnlineObjectiveTerm> termIterator = null;
        if (termStore.isLoaded()) {
            termIterator = termStore.noWriteIterator();
        } else {
            termIterator = termStore.iterator();
        }

        for (SGDOnlineObjectiveTerm term : IteratorUtils.newIterable(termIterator)) {
            objective += term.evaluate(variableValues);
        }

        return objective / termStore.size();
    }

    @Override
    public void close() {
    }
}
