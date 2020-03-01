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
package org.linqs.psl.evaluation.statistics;

import org.linqs.psl.application.learning.weight.TrainingMap;
import org.linqs.psl.config.Config;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.StandardPredicate;

import java.util.*;


/**
 * Compute various ranking statistics.
 */
public class RankingEvaluator extends Evaluator {
    public enum RepresentativeMetric {
        AUROC,
        POSITIVE_AUPRC,
        NEGATIVE_AUPRC,
        MEAN_AP,
        DCG,
        NDCG
    }

    /**
     * Prefix of property keys used by this class.
     */
    public static final String CONFIG_PREFIX = "rankingevaluator";

    /**
     * The truth threshold.
     */
    public static final String THRESHOLD_KEY = CONFIG_PREFIX + ".threshold";
    public static final double DEFAULT_THRESHOLD = 0.5;

    /**
     * The representative metric.
     * Default to F1.
     * Must match a string from the RepresentativeMetric enum.
     */
    public static final String REPRESENTATIVE_KEY = CONFIG_PREFIX + ".representative";
    public static final String DEFAULT_REPRESENTATIVE = "AUROC";

    private double threshold;
    private RepresentativeMetric representative;

    // Both sorted DESC by truth value.
    private List<GroundAtom> truth;
    private List<GroundAtom> predicted;

    public RankingEvaluator() {
        this(
                Config.getDouble(THRESHOLD_KEY, DEFAULT_THRESHOLD),
                Config.getString(REPRESENTATIVE_KEY, DEFAULT_REPRESENTATIVE));
    }

    public RankingEvaluator(double threshold) {
        this(threshold, DEFAULT_REPRESENTATIVE);
    }

    public RankingEvaluator(double threshold, String representative) {
        this(threshold, RepresentativeMetric.valueOf(representative.toUpperCase()));
    }

    public RankingEvaluator(double threshold, RepresentativeMetric representative) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("Threhsold must be in (0, 1). Found: " + threshold);
        }

        this.threshold = threshold;
        this.representative = representative;

        truth = new ArrayList<GroundAtom>();
        predicted = new ArrayList<GroundAtom>();
    }

    @Override
    public void compute(TrainingMap trainingMap) {
        compute(trainingMap, null);
    }

    @Override
    public void compute(TrainingMap trainingMap, StandardPredicate predicate) {
        truth = new ArrayList<GroundAtom>(trainingMap.getTrainingMap().size());
        predicted = new ArrayList<GroundAtom>(trainingMap.getTrainingMap().size());

        for (Map.Entry<GroundAtom, GroundAtom> entry : trainingMap.getFullMap()) {
            if (predicate != null && entry.getKey().getPredicate() != predicate) {
                continue;
            }

            truth.add(entry.getValue());
            predicted.add(entry.getKey());
        }

        Collections.sort(truth);
        Collections.sort(predicted);
    }

    @Override
    public double getRepresentativeMetric() {
        switch (representative) {
            case AUROC:
                return auroc();
            case POSITIVE_AUPRC:
                return positiveAUPRC();
            case NEGATIVE_AUPRC:
                return negativeAUPRC();
            case MEAN_AP:
                return meanAveragePrecision();
            case DCG:
                return discountedCumulativeGain();
            case NDCG:
                return normalizedDiscountedCumulativeGain();
            default:
                throw new IllegalStateException("Unknown representative metric: " + representative);
        }
    }

    @Override
    public boolean isHigherRepresentativeBetter() {
        return true;
    }

    public double getThreshold() {
        return threshold;
    }

    /**
     * Returns area under the precision recall curve.
     * This is a simple implementation that assumes all the ground truth is 0/1
     * and does not make any effort to approximate the first point.
     */
    public double positiveAUPRC() {
        // both lists are sorted
        int totalPositives = 0;
        for (GroundAtom atom : truth) {
            if (atom.getValue() > threshold) {
                totalPositives++;
            }
        }

        if (totalPositives == 0) {
            return 0.0;
        }

        double area = 0.0;
        int tp = 0;
        int fp = 0;

        // Precision is along the Y-axis.
        double prevY = 1.0;
        // Recall is along the X-axis.
        double prevX = 0.0;

        // Go through the atoms from highest truth value to lowest.
        for (GroundAtom atom : predicted) {
            Boolean label = getTruthLabel(atom);
            if (label == null) {
                continue;
            }

            // Assume we predicted everything positive.
            if (label != null && label) {
                tp++;
            } else {
                fp++;
            }

            double newY = tp / (double)(tp + fp);
            double newX = tp / (double)totalPositives;

            area += 0.5 * (newX - prevX) * Math.abs(newY - prevY) + (newX - prevX) * newY;
            prevY = newY;
            prevX = newX;
        }

        // Add the final piece.
        area += 0.5 * (1.0 - prevX) * Math.abs(0.0 - prevY) + (1.0 - prevX) * 0;

        return area;
    }

    /**
     * Returns area under the precision recall curve for the negative class.
     * The same stipulations for AUPRC hold here.
     */
    public double negativeAUPRC() {
        // both lists are sorted
        int totalPositives = 0;
        for (GroundAtom atom : truth) {
            if (atom.getValue() > threshold) {
                totalPositives++;
            }
        }

        int totalNegatives = predicted.size() - totalPositives;
        if (totalNegatives == 0) {
            return 0.0;
        }

        double area = 0.0;
        // Assume we have already predicted everything false, and correct as we go.
        int fn = totalPositives;
        int tn = totalNegatives;

        // Precision is along the Y-axis.
        double prevY = tn / (double)(tn + fn);
        // Recall is along the X-axis.
        double prevX = 1.0;

        // Go through the atoms from highest truth value to lowest.
        for (GroundAtom atom : predicted) {
            Boolean label = getTruthLabel(atom);
            if (label == null) {
                continue;
            }

            if (label != null && label) {
                fn--;
            } else {
                tn--;
            }

            double newY = 0.0;
            if (tn + fn > 0) {
                newY = tn / (double)(tn + fn);
            }

            double newX = tn / (double)totalNegatives;

            area += 0.5 * (prevX - newX) * Math.abs(newY - prevY) + (prevX - newX) * newY;
            prevY = newY;
            prevX = newX;
        }

        return area;
    }

    /**
     * Returns area under ROC curve.
     * Assumes predicted GroundAtoms are hard truth values.
     */
    public double auroc() {
        int totalPositives = 0;
        for (GroundAtom atom : truth) {
            if (atom.getValue() > threshold) {
                totalPositives++;
            }
        }

        int totalNegatives = predicted.size() - totalPositives;

        double area = 0.0;
        int tp = 0;
        int fp = 0;

        // True positrive rate (TPR) is along the Y-axis.
        double prevY = 0.0;
        // False positive rate (FPR) is along the X-axis.
        double prevX = 0.0;

        // Go through the atoms from highest truth value to lowest.
        for (GroundAtom atom : predicted) {
            Boolean label = getTruthLabel(atom);
            if (label == null) {
                continue;
            }

            // Assume we predicted everything positive.
            if (label) {
                tp++;
            } else {
                fp++;
            }

            double newY = (double)tp / (double)totalPositives;
            double newX = (double)fp / (double)totalNegatives;

            area += 0.5 * (newX - prevX) * Math.abs(newY - prevY) + (newX - prevX) * newY;
            prevY = newY;
            prevX = newX;
        }

        // Add the final piece.
        area += 0.5 * (1.0 - prevX) * Math.abs(1.0 - prevY) + (1.0 - prevX) * 1.0;

        return area;
    }

    /**
     * Returns area mean Average Precision.
     * Assumes predicted GroundAtoms are hard truth values.
     * Assumes predicted GroundAtoms are formatted as (query, rank)
     */
    public double meanAveragePrecision() {
        // hashtable to hold the number of ground truth positives for each query
        Map<String, Integer> totalPositives = new Hashtable<>();

        for (GroundAtom atom : truth) {
            if (atom.getValue() >= threshold) {
                String queryId = atom.getArguments()[0].toString();
                if (totalPositives.containsKey(queryId)) {
                    totalPositives.put(queryId, (totalPositives.get(queryId) + 1));
                } else {
                    totalPositives.put(queryId, 1);
                }
            }
        }

        // find the average precision for each query. Note that the predictions are already sorted.
        // hashtables to hold the true positives seen so far, the position, the running sum of precisions, and
        // the average precision for each query
        Map<String, Integer> TPSeen = new Hashtable<>();
        Map<String, Integer> Position = new Hashtable<>();
        Map<String, Double> SumP = new Hashtable<>();
        Map<String, Double> AveP = new Hashtable<>();

        for (GroundAtom atom : predicted) {
            Boolean label = getTruthLabel(atom);
            String queryId = atom.getArguments()[0].toString();

            if (label == null) {
                continue;
            }

            // always update query position
            if (Position.containsKey(queryId)) {
                Position.put(queryId, (Position.get(queryId) + 1));
            } else {
                Position.put(queryId, 1);
            }

            // only update the TPSeen count and the running precision sum if the document is relevant
            if (label) {
                if (TPSeen.containsKey(queryId)) {
                    TPSeen.put(queryId, (TPSeen.get(queryId) + 1));
                } else {
                    TPSeen.put(queryId, 1);
                }

                if (SumP.containsKey(queryId)) {
                    SumP.put(queryId, (SumP.get(queryId) + ((double) TPSeen.get(queryId) / Position.get(queryId))));
                } else {
                    SumP.put(queryId, 1.0);
                }
            }
        }

        // Calculate the average precision for each query
        for (Map.Entry<String, Double> entry : SumP.entrySet()) {
            AveP.put(entry.getKey(), (entry.getValue() / totalPositives.get(entry.getKey())));
        }

        // Calculate the mean average precision over all the queries
        double MAP = 0.0;
        int NQ = 0;

        for (Map.Entry<String, Double> entry : AveP.entrySet()) {
            MAP = MAP + entry.getValue();
            NQ = NQ + 1;
        }
        MAP = MAP / (double)NQ;

        return MAP;
    }

    public double discountedCumulativeGain() {
        Map<String, Double> DCG = getDCGTable();

        // Calculate the mean dcg over each query
        double meanDCG = 0.0;
        int NQ = 0;

        for (Map.Entry<String, Double> entry : DCG.entrySet()) {
            meanDCG = meanDCG + entry.getValue();
            NQ = NQ + 1;
        }
        meanDCG = meanDCG / (double) NQ;

        return meanDCG;
    }

    public double normalizedDiscountedCumulativeGain() {
        Map<String, Double> DCG = getDCGTable();
        Map<String, Double> IDCG = getIdealDCGTable();
        Map<String, Double> NDCG = new Hashtable<>();

        for (Map.Entry<String, Double> entry : DCG.entrySet()) {
            NDCG.put(entry.getKey(), DCG.get(entry.getKey()) / IDCG.get(entry.getKey()));
        }

        // Calculate the mean NDCG over each query
        double meanNDCG = 0.0;
        int NQ = 0;

        for (Map.Entry<String, Double> entry : NDCG.entrySet()) {
            meanNDCG = meanNDCG + entry.getValue();
            NQ = NQ + 1;
        }
        meanNDCG = meanNDCG / (double) NQ;

        return meanNDCG;
    }

    @Override
    public String getAllStats() {
        return String.format(
                "AUROC: %f, Positive Class AUPRC: %f, Negative Class AUPRC: %f, Mean Average PrecisionMAP: %f, " +
                        "Discounted Cumulative Gain DCG: %f, Normalized DCG: %f",
                auroc(), positiveAUPRC(), negativeAUPRC(), meanAveragePrecision(),
                discountedCumulativeGain(), normalizedDiscountedCumulativeGain());
    }

    private Map<String, Double> getIdealDCGTable() {
        // hashtable to hold the running DCG sums and current rank positions for each query
        Map<String, Double> IDCG = new Hashtable<>();
        Map<String, Integer> Position = new Hashtable<>();

        for (GroundAtom atom : truth) {
            Double truthValue = getTruthValue(atom);
            String queryId = atom.getArguments()[0].toString();

            if (truthValue == null) {
                continue;
            }

            // always update query position
            if (Position.containsKey(queryId)) {
                Position.put(queryId, (Position.get(queryId) + 1));
            } else {
                Position.put(queryId, 1);
            }

            // use position to calculate current DCG term and update DCG sum
            double DCGTerm = ((Math.pow(2.0, truthValue) - 1)
                    / (Math.log(Position.get(queryId) + 1) / Math.log(2)));
            if (IDCG.containsKey(queryId)) {
                IDCG.put(queryId, (IDCG.get(queryId) + DCGTerm));
            } else {
                IDCG.put(queryId, DCGTerm);
            }
        }

        return IDCG;
    }

    private Map<String, Double> getDCGTable() {
        // hashtable to hold the running DCG sums and current rank positions for each query
        Map<String, Double> DCG = new Hashtable<>();
        Map<String, Integer> Position = new Hashtable<>();

        for (GroundAtom atom : predicted) {
            Double truthValue = getTruthValue(atom);
            String queryId = atom.getArguments()[0].toString();

            if (truthValue == null) {
                continue;
            }

            // always update query position
            if (Position.containsKey(queryId)) {
                Position.put(queryId, (Position.get(queryId) + 1));
            } else {
                Position.put(queryId, 1);
            }

            // use position to calculate current DCG term and update DCG sum
            double DCGTerm = ((Math.pow(2.0, truthValue) - 1)
                    / (Math.log(Position.get(queryId) + 1) / Math.log(2)));
            if (DCG.containsKey(queryId)) {
                DCG.put(queryId, (DCG.get(queryId) + DCGTerm));
            } else {
                DCG.put(queryId, DCGTerm);
            }
        }

        return DCG;
    }

    /**
     * If the atom exists in the truth, return it's boolean value.
     * Otherwise return null.
     */
    private Boolean getTruthLabel(GroundAtom atom) {
        int index = truth.indexOf(atom);
        if (index == -1) {
            return null;
        }

        return truth.get(index).getValue() > threshold;
    }
    
    /**
     * If the atom exists in the truth, return it's relevance value.
     * Otherwise return null.
     */
    private Double getTruthValue(GroundAtom atom) {
        int index = truth.indexOf(atom);
        if (index == -1) {
            return null;
        }

        return (double)truth.get(index).getValue();
    }
}
