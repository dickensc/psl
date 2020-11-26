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
package org.linqs.psl.application.inference.mpe;

import org.junit.Test;
import org.linqs.psl.TestModel;
import org.linqs.psl.application.inference.InferenceApplication;
import org.linqs.psl.application.inference.InferenceTest;
import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;

import org.junit.After;
import org.linqs.psl.model.rule.arithmetic.WeightedArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.expression.*;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Coefficient;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.ConstantNumber;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.reasoner.function.FunctionComparator;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class SGDInferenceTest extends InferenceTest {
    @After
    public void cleanup() {
        Options.SGD_LEARNING_RATE.clear();
    }

    @Override
    protected InferenceApplication getInference(List<Rule> rules, Database db) {
        return new SGDInference(rules, db);
    }

    @Override
    public void initialValueTest() {
        Options.SGD_LEARNING_RATE.set(10.0);
        super.initialValueTest();
    }

    /**
     * Test mutual information rule
     */
    @Test
    public void testArithmeticMI() {
        TestModel.ModelInformation info = TestModel.getModel();

        Rule rule;
        List<Coefficient> coefficients;
        List<SummationAtomOrAtom> atoms;

        coefficients = Arrays.asList(
                (Coefficient)(new ConstantNumber(1.0f)),
                (Coefficient)(new ConstantNumber(-1.0f))
        );

        atoms = Arrays.asList(
                (SummationAtomOrAtom)(new SummationAtom(info.predicates.get("Buys"),
                        new SummationVariableOrTerm[]{new SummationVariable("A"), new Variable("B")}
                )), (SummationAtomOrAtom)(new SummationAtom(info.predicates.get("Likes"),
                        new SummationVariableOrTerm[]{new SummationVariable("C"), new SummationVariable("D")}
                ))
        );

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.MI, new ConstantNumber(0.0f)),
                1.0f,
                false
        );

        // Friends(+A, B) MI Likes(+C, +D)
        info.model.clear();
        info.model.addRule(rule);

        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
        InferenceApplication inference = getInference(info.model.getRules(), inferDB);

        double objective = inference.inference();
        inference.close();
        inferDB.close();

        assertEquals(0.0, objective, 0.01);
    }
}
