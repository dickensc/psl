/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2021 The Regents of the University of California
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

import org.linqs.psl.config.Options;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.reasoner.function.ConstraintTerm;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.function.GeneralFunction;
import org.linqs.psl.reasoner.function.HingeFunction;
import org.linqs.psl.reasoner.function.LinearFunction;
import org.linqs.psl.reasoner.function.MaxFunction;
import org.linqs.psl.util.MathUtils;
import org.linqs.psl.util.Parallel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A base term generator for terms that come from hyperplanes.
 */
public abstract class HyperplaneTermGenerator<T extends ReasonerTerm, V extends ReasonerLocalVariable> implements TermGenerator<T, V> {
    private static final Logger log = LoggerFactory.getLogger(HyperplaneTermGenerator.class);

    private boolean mergeConstants;
    private boolean invertNegativeWeight;
    private boolean godelNegation;

    public HyperplaneTermGenerator(boolean mergeConstants) {
        this.mergeConstants = mergeConstants;
        invertNegativeWeight = Options.HYPERPLANE_TG_INVERT_NEGATIVE_WEIGHTS.getBoolean();
        godelNegation = Options.GodelNegation.getBoolean();
    }

    @Override
    public long generateTerms(GroundRuleStore ruleStore, final TermStore<T, V> termStore) {
        long initialSize = termStore.size();
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

        Parallel.foreach(ruleStore.getGroundRules(), new GeneratorWorker(termStore));

        // Invert negative Godel term weights once all Godel terms are grounded.
        for (WeightedRule rule : rules) {
            if ((rule.getWeight() < 0) && (rule instanceof WeightedLogicalRule) && godelNegation) {
                rule.setWeight(-1.0f * rule.getWeight());
            }
        }

        return termStore.size() - initialSize;
    }

    private class GeneratorWorker extends Parallel.Worker<GroundRule> {
        private final TermStore<T, V> termStore;
        private final List<T> newTerms;
        private final List<Hyperplane> newHyperplane;

        public GeneratorWorker(final TermStore<T, V> termStore) {
            super();

            this.termStore = termStore;
            newTerms = new ArrayList<T>(2);
            newHyperplane = new ArrayList<Hyperplane>(1);
        }

        @Override
        public Object clone() {
            return new GeneratorWorker(termStore);
        }

        @Override
        public void work(long index, GroundRule rule) {
            newTerms.clear();
            newHyperplane.clear();

            boolean negativeWeight =
                    rule instanceof WeightedGroundRule
                    && ((WeightedGroundRule)rule).getWeight() < 0.0;

            if (negativeWeight && invertNegativeWeight) {
                // Negate (weight and expression) rules that have a negative weight.
                for (GroundRule negatedRule : rule.negate()) {
                    createTerm(negatedRule, termStore, newTerms, newHyperplane);

                    for (T term : newTerms) {
                        termStore.add(rule, term, newHyperplane.get(0));
                    }

                    newTerms.clear();
                    newHyperplane.clear();
                }
            } else {
                createTerm(rule, termStore, newTerms, newHyperplane);

                for (T term : newTerms) {
                    termStore.add(rule, term, newHyperplane.get(0));
                }

                newTerms.clear();
                newHyperplane.clear();
            }
        }
    }

    /**
     * Create a terms from the ground rule and add it to supplied collection.
     * This does not directly add terms to the TermStore.
     *
     * The supplied collection will not be cleared before use.
     * In most cases only one term will be added,
     * but it is possible for zero or more terms to be added.
     *
     * If |newHyperplane| is not null, the Hyperplane used to create the resultant terms will be added to it.
     * If no terms are added, not Hyperplane will be added.
     *
     * @return the number of terms added to the supplied collection.
     */
    public int createTerm(GroundRule groundRule, TermStore<T, V> termStore,
            Collection<T> newTerms, Collection<Hyperplane> newHyperplane) {
        int count = 0;
        List<Hyperplane<V>> hyperplanes = null;

        if (groundRule instanceof WeightedGroundRule) {
            GeneralFunction function = ((WeightedGroundRule)groundRule).getFunctionDefinition(mergeConstants);
            hyperplanes = processHyperplanes(function, termStore);
            if (hyperplanes == null) {
                return 0;
            }

            // Non-negative functions have a hinge.
            count = createLossTerm(newTerms, termStore, function instanceof HingeFunction, function.isSquared(), groundRule, hyperplanes);
        } else if (groundRule instanceof UnweightedGroundRule) {
            ConstraintTerm constraint = ((UnweightedGroundRule)groundRule).getConstraintDefinition(mergeConstants);
            GeneralFunction function = constraint.getFunction();
            hyperplanes = processHyperplanes(function, termStore);
            if (hyperplanes == null) {
                return 0;
            }

            for (Hyperplane<V> hyperplane: hyperplanes) {
                hyperplane.setConstant((float)(constraint.getValue() + hyperplane.getConstant()));
            }

            count = createLinearConstraintTerm(newTerms, termStore, groundRule, hyperplanes, constraint.getComparator());
        } else {
            throw new IllegalArgumentException("Unsupported ground rule: " + groundRule);
        }

        if (newHyperplane != null && count > 0) {
            newHyperplane.addAll(hyperplanes);
        }

        return count;
    }

    /**
     * Construct a hyperplane from a general function.
     * Will return null if the term is trivial and should be abandoned.
     */
    private List<Hyperplane<V>> processHyperplanes(GeneralFunction generalFunction, TermStore<T, V> termStore) {
        List<Hyperplane<V>> hyperplanes = null;
        GeneralFunction[] sums = null;
        int n_hyperplanes = 0;

        if (generalFunction instanceof LinearFunction) {
            n_hyperplanes = 1;
            hyperplanes = new ArrayList<Hyperplane<V>>(n_hyperplanes);
            sums = new GeneralFunction[1];
            sums[0] = generalFunction;
        } else if (generalFunction instanceof MaxFunction) {
            n_hyperplanes = generalFunction.size();
            hyperplanes = new ArrayList<Hyperplane<V>>(generalFunction.size());
            sums = ((MaxFunction)generalFunction).getTerms();
        } else {
            throw new IllegalArgumentException();
        }

        for (int j = 0; j < n_hyperplanes; j++) {
            GeneralFunction sum = sums[j];
            Hyperplane<V> hyperplane = new Hyperplane<V>(getLocalVariableType(), sum.size(), -1.0f * (float) sum.getConstant());

            for (int i = 0; i < sum.size(); i++) {
                float coefficient = sum.getCoefficient(i);
                GeneralFunction term = sum.getTerm(i);

                if ((term instanceof RandomVariableAtom) && (((RandomVariableAtom) term).getPredicate().isFixedMirror())) {
                    // These types of RVAs get treated as observations and integrated into the constant.

                    // Subtract because hyperplane is stored as coeffs^T * x = constant.
                    hyperplane.setConstant(hyperplane.getConstant() - (float) (coefficient * term.getValue()));

                    // Negate the coefficient so that "incorporating" this term would mean adding it,
                    // and "removing" this term would be subtracting.
                    hyperplane.addIntegratedRVA((RandomVariableAtom) term, -coefficient);
                } else if ((term instanceof RandomVariableAtom) || (!mergeConstants && term instanceof ObservedAtom)) {
                    V variable = termStore.createLocalVariable((GroundAtom) term);

                    // Check to see if we have seen this variable before in this hyperplane.
                    // Note that we are checking for existence in a List (O(n)), but there are usually a small number of
                    // variables per hyperplane.
                    int localIndex = hyperplane.indexOfVariable(variable);
                    if (localIndex != -1) {
                        // If this function came from a logical rule
                        // and the sign of the current coefficient and the coefficient of this variable do not match,
                        // then this term is trivial.
                        // Recall that all logical rules are disjunctions with only +1 and -1 as coefficients.
                        // A mismatch in signs for the same variable means that a ground atom appeared twice,
                        // once as a positive atom and once as a negative atom: Foo('a') || !Foo('a').
                        if (sum instanceof HingeFunction && !MathUtils.signsMatch(hyperplane.getCoefficient(localIndex), coefficient)) {
                            hyperplane = null;
                            break;
                        }

                        // If the local variable already exists, just add to its coefficient.
                        hyperplane.appendCoefficient(localIndex, coefficient);
                    } else {
                        hyperplane.addTerm(variable, coefficient);
                    }
                } else if (term.isConstant()) {
                    // Subtract because hyperplane is stored as coeffs^T * x = constant.
                    hyperplane.setConstant(hyperplane.getConstant() - (float)(coefficient * term.getValue()));
                } else {
                    throw new IllegalArgumentException("Unexpected summand: " + sum + "[" + i + "] (" + term + ").");
                }
            }

            if (hyperplane != null) {
                hyperplanes.add(hyperplane);
            }
        }

        // This should be caught further up the chain, but we will check for full observed terms.
        if (hyperplanes.size() == 0) {
            return null;
        }

        return hyperplanes;
    }

    /**
     * Get the class object for the local variable type.
     * This is for type safety when creating hyperplanes.
     */
    public abstract Class<V> getLocalVariableType();

    /**
     * Create a term from a ground rule and hyperplane, and add it to the collection of new terms.
     * Non-hinge terms are linear combinations (ala arithmetic rules).
     * Non-squared terms are linear.
     *
     * @return the number of terms added to the supplied collection.
     */
    public abstract int createLossTerm(Collection<T> newTerms, TermStore<T, V> termStore,
            boolean isHinge, boolean isSquared, GroundRule groundRule, List<Hyperplane<V>> hyperplanes);

    /**
     * Create a hard constraint term, and add it to the collection of new terms.
     *
     * @return the number of terms added to the supplied collection.
     */
    public abstract int createLinearConstraintTerm(Collection<T> newTerms, TermStore<T, V> termStore,
            GroundRule groundRule, List<Hyperplane<V>> hyperplanes, FunctionComparator comparator);
}
