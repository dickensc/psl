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
package org.linqs.psl.application.inference;

import org.linqs.psl.application.ModelApplication;
import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.atom.PersistedAtomManager;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.grounding.Grounding;
import org.linqs.psl.grounding.MemoryGroundRuleStore;
import org.linqs.psl.reasoner.InitialValue;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.admm.ADMMReasoner;
import org.linqs.psl.reasoner.admm.term.ADMMTermStore;
import org.linqs.psl.reasoner.admm.term.ADMMTermGenerator;
import org.linqs.psl.reasoner.term.TermGenerator;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.Reflection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * All the tools necessary to perform infernce.
 * An inference application owns the ground atoms (Database/AtomManager), ground rules (GroundRuleStore), the terms (TermStore),
 * how terms are generated (TermGenerator), and how inference is actually performed (Reasoner).
 * As such, the inference application is the top level authority for these items and methods.
 * For example, inference may set the value of the random variables on construction.
 */
public abstract class InferenceApplication implements ModelApplication {
    private static final Logger log = LoggerFactory.getLogger(InferenceApplication.class);

    protected List<Rule> rules;
    protected Database db;
    protected Reasoner reasoner;
    protected InitialValue initialValue;

    protected GroundRuleStore groundRuleStore;
    protected TermStore termStore;
    protected TermGenerator termGenerator;
    protected PersistedAtomManager atomManager;

    private boolean atomsCommitted;

    public InferenceApplication(List<Rule> rules, Database db) {
        this.rules = new ArrayList<Rule>(rules);
        this.db = db;
        this.atomsCommitted = false;

        this.initialValue = InitialValue.valueOf(Options.INFERENCE_INITIAL_VARIABLE_VALUE.getString());

        initialize();
    }

    /**
     * Get objects ready for inference.
     * This will call into the abstract method completeInitialize().
     */
    protected void initialize() {
        log.debug("Creating persisted atom mannager.");
        atomManager = createAtomManager(db);
        log.debug("Atom manager initialization complete.");

        initializeAtoms();

        reasoner = createReasoner();
        termStore = createTermStore();
        groundRuleStore = createGroundRuleStore();
        termGenerator = createTermGenerator();

        termStore.ensureVariableCapacity(atomManager.getCachedRVACount());

        completeInitialize();
    }

    protected PersistedAtomManager createAtomManager(Database db) {
        return new PersistedAtomManager(db, false, initialValue);
    }

    protected GroundRuleStore createGroundRuleStore() {
        return (GroundRuleStore)Options.INFERENCE_GRS.getNewObject();
    }

    protected Reasoner createReasoner() {
        return (Reasoner)Options.INFERENCE_REASONER.getNewObject();
    }

    protected TermGenerator createTermGenerator() {
        return (TermGenerator)Options.INFERENCE_TG.getNewObject();
    }

    protected TermStore createTermStore() {
        return (TermStore)Options.INFERENCE_TS.getNewObject();
    }

    /**
     * Complete the initialization process.
     * Most of the infrastructure will have been constructued.
     * The child is responsible for constructing the AtomManager
     * and populating the ground rule store.
     */
    protected void completeInitialize() {
        log.info("Grounding out model.");
        int groundCount = Grounding.groundAll(rules, atomManager, groundRuleStore);
        log.info("Grounding complete.");

        log.debug("Initializing objective terms for {} ground rules.", groundCount);
        @SuppressWarnings("unchecked")
        int termCount = termGenerator.generateTerms(groundRuleStore, termStore);
        log.debug("Generated {} objective terms from {} ground rules.", termCount, groundCount);
    }

    /**
     * Alias for inference() with committing atoms.
     */
    public void inference() {
        inference(true, true, true);
    }

    /**
     * Minimize the total weighted incompatibility of the atoms according to the rules,
     * and optionally commit the updated atoms back to the database.
     *
     * All RandomVariableAtoms which the model might access must be persisted in the Database.
     */
    public void inference(boolean commitAtoms, boolean initializeAtoms, boolean resetTerms) {
        if (initializeAtoms) {
            initializeAtoms();
        }

        if (resetTerms && termStore != null) {
            termStore.reset(initialValue);
        }

        log.info("Beginning inference.");
        internalInference();
        log.info("Inference complete.");
        atomsCommitted = false;

        // Commits the RandomVariableAtoms back to the Database.
        if (commitAtoms) {
            commit();
        }
    }

    /**
     * The implementation of the full inference by each class.
     */
    protected void internalInference() {
        reasoner.optimize(termStore);
    }

    public Reasoner getReasoner() {
        return reasoner;
    }

    public GroundRuleStore getGroundRuleStore() {
        return groundRuleStore;
    }

    public TermStore getTermStore() {
        return termStore;
    }

    public PersistedAtomManager getAtomManager() {
        return atomManager;
    }

    /**
     * Set a budget (given as a proportion of the max budget).
     */
    public void setBudget(double budget) {
        reasoner.setBudget(budget);
    }

    /**
     * Set all the random variable atoms to the initial value for this inference application.
     */
    public void initializeAtoms() {
        for (RandomVariableAtom atom : atomManager.getDatabase().getAllCachedRandomVariableAtoms()) {
            atom.setValue(initialValue.getVariableValue(atom));
        }
    }

    /**
     * Commit the results of inference to the database.
     */
    public void commit() {
        if (atomsCommitted) {
            return;
        }

        log.info("Writing results to Database.");
        atomManager.commitPersistedAtoms();
        log.info("Results committed to database.");

        atomsCommitted = true;
    }

    @Override
    public void close() {
        if (termStore != null) {
            termStore.close();
            termStore = null;
        }

        if (groundRuleStore != null) {
            groundRuleStore.close();
            groundRuleStore = null;
        }

        if (reasoner != null) {
            reasoner.close();
            reasoner = null;
        }

        rules = null;
        db = null;
    }

    /**
     * Construct an inference application given the data.
     * Look for a constructor like: (List<Rule>, Database).
     */
    public static InferenceApplication getInferenceApplication(String className, List<Rule> rules, Database db) {
        className = Reflection.resolveClassName(className);

        Class<? extends InferenceApplication> classObject = null;
        try {
            @SuppressWarnings("unchecked")
            Class<? extends InferenceApplication> uncheckedClassObject = (Class<? extends InferenceApplication>)Class.forName(className);
            classObject = uncheckedClassObject;
        } catch (ClassNotFoundException ex) {
            throw new IllegalArgumentException("Could not find class: " + className, ex);
        }

        Constructor<? extends InferenceApplication> constructor = null;
        try {
            constructor = classObject.getConstructor(List.class, Database.class);
        } catch (NoSuchMethodException ex) {
            throw new IllegalArgumentException("No sutible constructor (List<Rules>, Database) found for inference application: " + className + ".", ex);
        }

        InferenceApplication inferenceApplication = null;
        try {
            inferenceApplication = constructor.newInstance(rules, db);
        } catch (InstantiationException ex) {
            throw new RuntimeException("Unable to instantiate inference application (" + className + ")", ex);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("Insufficient access to constructor for " + className, ex);
        } catch (InvocationTargetException ex) {
            throw new RuntimeException("Error thrown while constructing " + className, ex);
        }

        return inferenceApplication;
    }
}
