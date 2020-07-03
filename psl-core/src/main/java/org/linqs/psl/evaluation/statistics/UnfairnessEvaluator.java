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

import org.linqs.psl.application.learning.weight.TrainingMap;
import org.linqs.psl.config.Options;
import org.linqs.psl.model.predicate.StandardPredicate;

public class UnfairnessEvaluator extends Evaluator {
    public enum RepresentativeMetric {
        NON_PARITY,
        VALUE
    }

    private double predictedAverage;
    private double absoluteError;
    private double squaredError;

    private UnfairnessEvaluator.RepresentativeMetric representative;

    public UnfairnessEvaluator() {
        this(Options.EVAL_UNF_REPRESENTATIVE.getString());
    }

    public UnfairnessEvaluator(String representative) {
        this(UnfairnessEvaluator.RepresentativeMetric.valueOf(representative.toUpperCase()));
    }

    public UnfairnessEvaluator(RepresentativeMetric representative) {
        this.representative = representative;
    }

    @Override
    public void compute(TrainingMap trainingMap) {
        compute(trainingMap, null);
    }

    @Override
    public void compute(TrainingMap trainingMap, StandardPredicate predicate) {
//        absoluteError = 0.0;
//        squaredError = 0.0;
//
//        for (Map.Entry<GroundAtom, GroundAtom> entry : getMap(trainingMap)) {
//            if (predicate != null && entry.getKey().getPredicate() != predicate) {
//                continue;
//            }
//
//            count++;
//            absoluteError += Math.abs(entry.getValue().getValue() - entry.getKey().getValue());
//            squaredError += Math.pow(entry.getValue().getValue() - entry.getKey().getValue(), 2);
//        }
    }

    @Override
    public double getRepMetric() {
        switch (representative) {
            case NON_PARITY:
                return nonParity();
            case VALUE:
                return value();
            default:
                throw new IllegalStateException("Unknown representative metric: " + representative);
        }
    }

    private double value() {
        return 0.0;
    }

    private double nonParity() {
        return 0.0;
    }

    @Override
    public boolean isHigherRepBetter() {
        return false;
    }

    @Override
    public String getAllStats() {
        return null;
    }
}
