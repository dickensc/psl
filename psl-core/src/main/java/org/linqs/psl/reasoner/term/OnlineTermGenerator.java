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
package org.linqs.psl.reasoner.term;

import org.linqs.psl.config.Config;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.function.ConstraintTerm;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.function.FunctionTerm;
import org.linqs.psl.reasoner.function.GeneralFunction;
import org.linqs.psl.reasoner.term.online.OnlineTermStore;
import org.linqs.psl.util.MathUtils;
import org.linqs.psl.util.Parallel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A base term generator for terms that come from onlines.
 */
public abstract class OnlineTermGenerator<T extends ReasonerTerm, V extends ReasonerLocalVariable> implements TermGenerator<T, V> {
    private static final Logger log = LoggerFactory.getLogger(OnlineTermGenerator.class);

    public static final String CONFIG_PREFIX = "onlinetermgenerator";

    /**
     * If true, then invert negative weight rules into their positive weight counterparts
     * (negate the weight and expression).
     */
    public static final String INVERT_NEGATIVE_WEIGHTS_KEY = CONFIG_PREFIX + ".invertnegativeweights";
    public static final boolean INVERT_NEGATIVE_WEIGHTS_DEFAULT = false;

    private boolean invertNegativeWeight;

    public OnlineTermGenerator() {
        invertNegativeWeight = Config.getBoolean(INVERT_NEGATIVE_WEIGHTS_KEY, INVERT_NEGATIVE_WEIGHTS_DEFAULT);
    }

    @Override
    public int generateTerms(GroundRuleStore ruleStore, final TermStore<T, V> termStore) {
        int initialSize = termStore.size();
        termStore.ensureCapacity(initialSize + ruleStore.size());

        Set<WeightedRule> rules = new HashSet<WeightedRule>();
        for (GroundRule rule : ruleStore.getGroundRules()) {
            if (rule instanceof WeightedGroundRule) {
                rules.add((WeightedRule)rule.getRule());
            }
        }

        for (WeightedRule rule : rules) {
            if (rule.getWeight() < 0.0) {
                log.warn("Found a rule with a negative weight, but config says not to invert it... skipping: " + rule);
            }
        }

        Parallel.foreach(ruleStore.getGroundRules(), new Parallel.Worker<GroundRule>() {
            @Override
            public void work(int index, GroundRule rule) {
                boolean negativeWeight =
                        rule instanceof WeightedGroundRule
                                && ((WeightedGroundRule)rule).getWeight() < 0.0;

                if (negativeWeight) {
                    // Skip
                    if (!invertNegativeWeight) {
                        return;
                    }

                    // Negate (weight and expression) rules that have a negative weight.
                    for (GroundRule negatedRule : rule.negate()) {
                        T term = createTerm(negatedRule, termStore);
                        if (term != null && term.size() > 0) {
                            termStore.add(rule, term);
                        }
                    }
                } else {
                    T term = createTerm(rule, termStore);
                    if (term != null && term.size() > 0) {
                        termStore.add(rule, term);
                    }
                }
            }
        });

        return termStore.size() - initialSize;
    }

    /**
     * Create a ReasonerTerm from the ground rule.
     * Note that the term will NOT be added to the term store.
     * The store is just needed for creating variables.
     */
    public T createTerm(GroundRule groundRule, TermStore<T, V> termStore) {
        if (groundRule instanceof WeightedGroundRule) {
            GeneralFunction function = ((WeightedGroundRule)groundRule).getFunctionDefinition();
            Online<V> online = processOnline(function, (OnlineTermStore)termStore);
            if (online == null) {
                return null;
            }

            // Non-negative functions have a hinge.
            return createLossTerm(termStore, function.isNonNegative(), function.isSquared(), groundRule, online);
        } else if (groundRule instanceof UnweightedGroundRule) {
            ConstraintTerm constraint = ((UnweightedGroundRule)groundRule).getConstraintDefinition();
            GeneralFunction function = constraint.getFunction();
            Online<V> online = processOnline(function, (OnlineTermStore)termStore);
            if (online == null) {
                return null;
            }

            online.setConstant((float)(constraint.getValue() + online.getConstant()));
            return createLinearConstraintTerm(termStore, groundRule, online, constraint.getComparator());
        } else {
            throw new IllegalArgumentException("Unsupported ground rule: " + groundRule);
        }
    }

    /**
     * Construct a online from a general function.
     * Will return null if the term is trivial and should be abandoned.
     */
    public Online<V> processOnline(GeneralFunction sum, OnlineTermStore<T> termStore) {

        Online<V> online = new Online<V>(getLocalVariableType(), sum.size(), sum.observedSize(), -1.0f * (float)sum.getConstant());

        for (int i = 0; i < sum.size(); i++) {
            float coefficient = (float)sum.getCoefficient(i);
            FunctionTerm term = sum.getTerm(i);
            if (term instanceof RandomVariableAtom) {
                RandomVariableAtom variable = termStore.createLocalVariable((RandomVariableAtom)term);

                // Check to see if we have seen this variable before in this online.
                // Note that we are checking for existence in a List (O(n)), but there are usually a small number of
                // variables per online.
                int localIndex = online.indexOfVariable((V)variable);
                if (localIndex != -1) {
                    // If this function came from a logical rule
                    // and the sign of the current coefficient and the coefficient of this variable do not match,
                    // then this term is trivial.
                    // Recall that all logical rules are disjunctions with only +1 and -1 as coefficients.
                    // A mismatch in signs for the same variable means that a ground atom appeared twice,
                    // once as a positive atom and once as a negative atom: Foo('a') || !Foo('a').
                    if (sum.isNonNegative() && !MathUtils.signsMatch(online.getCoefficient(localIndex), coefficient)) {
                        return null;
                    }

                    // If the local variable already exists, just add to its coefficient.
                    online.appendCoefficient(localIndex, coefficient);
                } else {
                    online.addTerm((V)variable, coefficient);
                }
            } else if (term.isConstant()) {
                // Subtracts because online is stored as coeffs^T * x = constant.
                online.setConstant(online.getConstant() - (float)(coefficient * term.getValue()));
            } else {
                throw new IllegalArgumentException("Unexpected summand: " + sum + "[" + i + "] (" + term + ").");
            }
        }

        // add observed terms to term
        for (int i = 0; i < sum.observedSize(); i++) {
            float coefficient = (float)sum.getObservedCoefficient(i);
            FunctionTerm term = sum.getObservedTerm(i);
            if (term instanceof ObservedAtom) {
                ObservedAtom variable = termStore.createLocalObservedVariable((ObservedAtom)term);

                // Check to see if we have seen this variable before in this online.
                // Note that we are checking for existence in a List (O(n)), but there are usually a small number of
                // variables per online.
                int localIndex = online.indexOfObserved(variable);
                if (localIndex != -1) {
                    // If this function came from a logical rule
                    // and the sign of the current coefficient and the coefficient of this variable do not match,
                    // then this term is trivial.
                    // Recall that all logical rules are disjunctions with only +1 and -1 as coefficients.
                    // A mismatch in signs for the same variable means that a ground atom appeared twice,
                    // once as a positive atom and once as a negative atom: Foo('a') || !Foo('a').
                    if (sum.isNonNegative() && !MathUtils.signsMatch(online.getCoefficient(localIndex), coefficient)) {
                        return null;
                    }

                    // If the local variable already exists, just add to its coefficient.
                    online.appendObservedCoefficient(localIndex, coefficient);
                } else {
                    online.addObservedTerm((ObservedAtom) variable, coefficient);
                }
            } else {
                throw new IllegalArgumentException("Unexpected summand: " + sum + "[" + i + "] (" + term + ").");
            }
        }

        return online;
    }

    /**
     * Get the class object for the local vairable type.
     * This is for type safety when creating onlines.
     */
    public abstract Class<V> getLocalVariableType();

    /**
     * Create a term from a ground rule and online.
     * Non-hinge terms are linear combinations (ala arithmetic rules).
     * Non-squared terms are linear.
     */
    public abstract T createLossTerm(TermStore<T, V> termStore, boolean isHinge, boolean isSquared, GroundRule groundRule, Online<V> online);

    /**
     * Create a hard constraint term,
     */
    public abstract T createLinearConstraintTerm(TermStore<T, V> termStore, GroundRule groundRule, Online<V> online, FunctionComparator comparator);
}
