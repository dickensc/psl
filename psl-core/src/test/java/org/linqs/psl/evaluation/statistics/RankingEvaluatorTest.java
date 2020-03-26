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
package org.linqs.psl.evaluation.statistics;

import org.junit.Before;

import static org.junit.Assert.assertEquals;

import org.linqs.psl.application.learning.weight.TrainingMap;
import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.atom.PersistedAtomManager;
import org.linqs.psl.database.loading.Inserter;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.UniqueIntID;
import org.linqs.psl.util.MathUtils;

import org.junit.Test;

public class RankingEvaluatorTest extends EvaluatorTest<RankingEvaluator> {
    @Override
    protected RankingEvaluator getEvaluator() {
        return new RankingEvaluator();
    }

    @Before
    public void setUp() {
        // overload default training map to test ranking evaluations
        dataStore = new RDBMSDataStore(new H2DatabaseDriver(
                H2DatabaseDriver.Type.Memory, this.getClass().getName(), true));

        predicate = StandardPredicate.get(
                "RankingPredictionTest_query_rank"
                , new ConstantType[]{ConstantType.UniqueIntID, ConstantType.UniqueIntID}
        );
        dataStore.registerPredicate(predicate);

        Partition targetPartition = dataStore.getPartition("targets");
        Partition truthPartition = dataStore.getPartition("truth");

        // Create some canned ground inference atoms
        int nCannedTerms = 8;
        Constant[][] cannedTerms = new Constant[nCannedTerms][];
        cannedTerms[0] = new Constant[]{ new UniqueIntID(1), new UniqueIntID(1) };
        cannedTerms[1] = new Constant[]{ new UniqueIntID(1), new UniqueIntID(3) };
        cannedTerms[2] = new Constant[]{ new UniqueIntID(1), new UniqueIntID(4) };
        cannedTerms[3] = new Constant[]{ new UniqueIntID(2), new UniqueIntID(1) };
        cannedTerms[4] = new Constant[]{ new UniqueIntID(2), new UniqueIntID(2) };
        cannedTerms[5] = new Constant[]{ new UniqueIntID(2), new UniqueIntID(3) };
        cannedTerms[6] = new Constant[]{ new UniqueIntID(2), new UniqueIntID(4) };
        cannedTerms[7] = new Constant[]{ new UniqueIntID(1), new UniqueIntID(2) };

        // Insert the predicated values.
        Inserter inserter = dataStore.getInserter(predicate, targetPartition);
        double predValue = 1.0;
        double step = predValue / (double)nCannedTerms;
        for (Constant[] terms : cannedTerms) {
            // Default prediction should be in the order of the cannedTerms
            // instances above as they all have predValue 1
            inserter.insertValue(predValue, terms);
            predValue = predValue - step;
        }

        // create some ground truth atoms
        Constant[][] baselinePositiveTerms = new Constant[6][];
        baselinePositiveTerms[0] = new Constant[]{ new UniqueIntID(1), new UniqueIntID(1) };
        baselinePositiveTerms[1] = new Constant[]{ new UniqueIntID(1), new UniqueIntID(3) };
        baselinePositiveTerms[2] = new Constant[]{ new UniqueIntID(1), new UniqueIntID(4) };
        baselinePositiveTerms[3] = new Constant[]{ new UniqueIntID(2), new UniqueIntID(1) };
        baselinePositiveTerms[4] = new Constant[]{ new UniqueIntID(2), new UniqueIntID(2) };
        baselinePositiveTerms[5] = new Constant[]{ new UniqueIntID(2), new UniqueIntID(3) };

        Constant[][] baselineNegativeTerms = new Constant[1][];
        baselineNegativeTerms[0] = new Constant[]{ new UniqueIntID(1), new UniqueIntID(2) };

        // Insert the truth values.
        inserter = dataStore.getInserter(predicate, truthPartition);
        for (Constant[] terms : baselinePositiveTerms) {
            inserter.insertValue(1.0, terms);
        }
        for (Constant[] terms : baselineNegativeTerms) {
            inserter.insertValue(0.0, terms);
        }

        // Redefine the truth database with no atoms in the write partition.
        Database results = dataStore.getDatabase(targetPartition);
        Database truth = dataStore.getDatabase(truthPartition, dataStore.getRegisteredPredicates());

        PersistedAtomManager atomManager = new PersistedAtomManager(results);
        trainingMap = new TrainingMap(atomManager, truth);

        // Since we only need the map, we can close all the databases.
        results.close();
        truth.close();
    }

    @Test
    public void testAUROC() {
        RankingEvaluator evaluator = new RankingEvaluator();
        evaluator.compute(trainingMap, predicate);
        assertEquals(1, evaluator.auroc(), MathUtils.EPSILON);
    }

    @Test
    public void testPositiveAUPRC() {
        RankingEvaluator evaluator = new RankingEvaluator();
        evaluator.compute(trainingMap, predicate);
        assertEquals(1, evaluator.positiveAUPRC(), MathUtils.EPSILON);
    }

    @Test
    public void testNegativeAUPRC() {
        RankingEvaluator evaluator = new RankingEvaluator();
        evaluator.compute(trainingMap, predicate);
        assertEquals(0.5, evaluator.negativeAUPRC(), MathUtils.EPSILON);
    }

    @Test
    public void testMeanAveragePrecision() {
        // TODO Need more tests
        for (double threshold = 0.1; threshold <= 1.0; threshold += 0.1) {
            RankingEvaluator computer = new RankingEvaluator(threshold);
            computer.compute(trainingMap, predicate);
            double value = computer.meanAveragePrecision();

            if (threshold <= 0.8) {
                assertEquals("Threshold: " + threshold, 1.0, value, MathUtils.EPSILON);
            } else {
                assertEquals("Threshold: " + threshold, 1.0, value, MathUtils.EPSILON);
            }
        }
    }

    @Test
    public void testDiscountedCumulativeGain() {
        // TODO Need more tests
        for (double threshold = 0.1; threshold <= 1.0; threshold += 0.1) {
            RankingEvaluator computer = new RankingEvaluator(threshold);
            computer.compute(trainingMap, predicate);
            double value = computer.discountedCumulativeGain();

            if (threshold <= 0.8) {
            } else {
            }
        }
    }

    @Test
    public void testNormalizedDiscountedCumulativeGain() {
        // TODO Need more tests
        for (double threshold = 0.1; threshold <= 1.0; threshold += 0.1) {
            RankingEvaluator computer = new RankingEvaluator(threshold);
            computer.compute(trainingMap, predicate);
            double value = computer.normalizedDiscountedCumulativeGain();

            if (threshold <= 0.8) {
                assertEquals("Threshold: " + threshold, 1.0, value, MathUtils.EPSILON);
            } else {
                assertEquals("Threshold: " + threshold, 1.0, value, MathUtils.EPSILON);
            }
        }
    }

    @Test
    public void testGetAllStats() {
        RankingEvaluator computer = new RankingEvaluator();
        computer.compute(trainingMap, predicate);
    }
}
