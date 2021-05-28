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
package org.linqs.psl.reasoner.sgd.term;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.rule.AbstractRule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.sgd.SGDReasoner;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.VariableTermStore;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Arrays;

/**
 * A term in the objective to be optimized by a SGDReasoner.
 */
public class SGDObjectiveTerm implements ReasonerTerm  {
    public static final float EPSILON = 1e-8f;

    private boolean squared;
    private boolean hinge;
    private boolean max;
    private boolean min;

    private WeightedRule rule;
    private float[] constant;

    private short[] size;
    private float[][] coefficients;
    private int[][] variableIndexes;

    public SGDObjectiveTerm(VariableTermStore<SGDObjectiveTerm, GroundAtom> termStore,
            WeightedRule rule,
            boolean squared, boolean hinge, boolean max, boolean min,
            List<Hyperplane<GroundAtom>> hyperplanes) {
        this.squared = squared;
        this.hinge = hinge;
        this.max = max;
        this.min = min;

        this.rule = rule;

        size = new short[hyperplanes.size()];
        constant = new float[hyperplanes.size()];
        coefficients = new float[hyperplanes.size()][];
        variableIndexes = new int[hyperplanes.size()][];

        for (int i = 0; i < hyperplanes.size(); i++) {
            Hyperplane<GroundAtom> hyperplane = hyperplanes.get(i);
            size[i] = (short)hyperplane.size();
            coefficients[i] = hyperplane.getCoefficients();
            constant[i] = hyperplane.getConstant();

            variableIndexes[i] = new int[size[i]];
            GroundAtom[] variables = hyperplane.getVariables();
            for (int j = 0; j < size[i]; j++) {
                variableIndexes[i][j] = termStore.getVariableIndex(variables[j]);
            }
        }
    }

    @Override
    public void adjustConstant(float oldValue, float newValue) {
        constant[0] = constant[0] - oldValue + newValue;
    }

    public float evaluate(float[] variableValues) {
        float[] dots = dot(variableValues);
        float weight = rule.getWeight();
        float dot = max? Float.NEGATIVE_INFINITY: Float.POSITIVE_INFINITY;

        if (max) {
            for (int i = 0; i < dots.length; i ++) {
                if (dots[i] > dot) {
                    dot = dots[i];
                }
            }
        } else if (min) {
            for (int i = 0; i < dots.length; i ++) {
                if (dots[i] < dot) {
                    dot = dots[i];
                }
            }
        } else {
            dot = dots[0];
        }

        if (squared && hinge) {
            // weight * [max(0.0, coeffs^T * x - constant)]^2
            return weight * (float)Math.pow(Math.max(0.0f, dot), 2);
        } else if (squared && !hinge) {
            // weight * [coeffs^T * x - constant]^2
            return weight * (float)Math.pow(dot, 2);
        } else if (!squared && hinge) {
            // weight * max(0.0, coeffs^T * x - constant)
            return weight * Math.max(0.0f, dot);
        } else {
            // weight * (coeffs^T * x - constant)
            return weight * dot;
        }
    }

    /**
     * Minimize the term by changing the random variables and return how much the random variables were moved by.
     */
    public float minimize(int iteration, VariableTermStore termStore, float learningRate,
                          SGDReasoner sgdReasoner, boolean coordinateStep) {
        float movement = 0.0f;
        float variableStep = 0.0f;
        float newValue = 0.0f;
        float partial = 0.0f;
        int term = -1;
        float dot = max? Float.NEGATIVE_INFINITY: Float.POSITIVE_INFINITY;

        GroundAtom[] variableAtoms = termStore.getVariableAtoms();
        float[] variableValues = termStore.getVariableValues();
        float[] dots = dot(variableValues);

        if (max) {
            for (int i = 0; i < dots.length; i ++) {
                if (dots[i] > dot) {
                    dot = dots[i];
                    term = i;
                }
            }
        } else if (min) {
            for (int i = 0; i < dots.length; i ++) {
                if (dots[i] < dot) {
                    dot = dots[i];
                    term = i;
                }
            }
        } else {
            term = 0;
        }

        for (int i = 0 ; i < size[term]; i++) {
            if (variableAtoms[variableIndexes[term][i]] instanceof ObservedAtom) {
                continue;
            }

            partial = computePartial(term, i, dots[term], rule.getWeight());
            variableStep = computeVariableStep(variableIndexes[term][i], iteration, learningRate, partial, sgdReasoner);

            newValue = Math.max(0.0f, Math.min(1.0f, variableValues[variableIndexes[term][i]] - variableStep));
            movement += Math.abs(newValue - variableValues[variableIndexes[term][i]]);
            variableValues[variableIndexes[term][i]] = newValue;

            if (coordinateStep) {
                dot = max? Float.NEGATIVE_INFINITY: Float.POSITIVE_INFINITY;
                if (max) {
                    for (int j = 0; j < dots.length; j ++) {
                        if (dots[j] > dot) {
                            dot = dots[j];
                            term = j;
                        }
                    }
                } else if (min) {
                    for (int j = 0; j < dots.length; j ++) {
                        if (dots[j] < dot) {
                            dot = dots[j];
                            term = j;
                        }
                    }
                } else {
                    term = 0;
                }
            }
        }

        return movement;
    }

    /**
     * Compute the step for a single variable according SGD or one of it's extensions.
     * For details on the math behind the SGD extensions see the corresponding papers listed below:
     *  - AdaGrad: https://jmlr.org/papers/volume12/duchi11a/duchi11a.pdf
     *  - Adam: https://arxiv.org/pdf/1412.6980.pdf
     */
    private float computeVariableStep(
            int variableIndex, int iteration, float learningRate, float partial, SGDReasoner sgdReasoner) {
        float step = 0.0f;
        float adaptedLearningRate = 0.0f;

        switch (sgdReasoner.getSgdExtension()) {
            case NONE:
                step = partial * learningRate;
                break;
            case ADAGRAD:
                float[] accumulatedGradientSquares = sgdReasoner.getAccumulatedGradientSquares();

                if (accumulatedGradientSquares.length <= variableIndex) {
                    accumulatedGradientSquares = Arrays.copyOf(accumulatedGradientSquares, (variableIndex + 1) * 2);
                    sgdReasoner.setAccumulatedGradientSquares(accumulatedGradientSquares);
                }
                accumulatedGradientSquares[variableIndex] = accumulatedGradientSquares[variableIndex] + partial * partial;

                adaptedLearningRate = learningRate / (float)Math.sqrt(accumulatedGradientSquares[variableIndex] + EPSILON);
                step = partial * adaptedLearningRate;
                break;
            case ADAM:
                float[] accumulatedGradientMean = sgdReasoner.getAccumulatedGradientMean();
                float[] accumulatedGradientVariance = sgdReasoner.getAccumulatedGradientVariance();
                float biasedGradientMean = 0.0f;
                float biasedGradientVariance = 0.0f;

                if (accumulatedGradientMean.length  <= variableIndex) {
                    accumulatedGradientMean = Arrays.copyOf(accumulatedGradientMean, (variableIndex + 1) * 2);
                    sgdReasoner.setAccumulatedGradientMean(accumulatedGradientMean);
                }
                accumulatedGradientMean[variableIndex] = sgdReasoner.getAdamBeta1() * accumulatedGradientMean[variableIndex] + (1.0f - sgdReasoner.getAdamBeta1()) * partial;

                if (accumulatedGradientVariance.length <= variableIndex) {
                    accumulatedGradientVariance = Arrays.copyOf(accumulatedGradientVariance, (variableIndex + 1) * 2);
                    sgdReasoner.setAccumulatedGradientVariance(accumulatedGradientVariance);
                }
                accumulatedGradientVariance[variableIndex] = sgdReasoner.getAdamBeta2() * accumulatedGradientVariance[variableIndex]
                            + (1.0f - sgdReasoner.getAdamBeta2()) * partial * partial;

                biasedGradientMean = accumulatedGradientMean[variableIndex] / (1.0f - (float)Math.pow(sgdReasoner.getAdamBeta1(), iteration));
                biasedGradientVariance = accumulatedGradientVariance[variableIndex] / (1.0f - (float)Math.pow(sgdReasoner.getAdamBeta2(), iteration));
                adaptedLearningRate = learningRate / ((float)Math.sqrt(biasedGradientVariance) + EPSILON);
                step = biasedGradientMean * adaptedLearningRate;
                break;
            default:
                throw new IllegalArgumentException(String.format("Unsupported SGD Extensions: '%s'", sgdReasoner.getSgdExtension()));
        }

        return step;
    }

    private float computePartial(int hyperplane, int localVarId, float dot, float weight) {
        if (hinge && dot <= 0.0f) {
            return 0.0f;
        }

        if (squared) {
            return weight * 2.0f * dot * coefficients[hyperplane][localVarId];
        }

        return weight * coefficients[hyperplane][localVarId];
    }

    private float[] dot(float[] variableValues) {
        float[] values = new float[size.length];

        for (int i = 0; i < size.length; i++) {
            for (int j = 0; j < size[i]; j++) {
                values[i] += coefficients[i][j] * variableValues[variableIndexes[i][j]];
            }
            values[i] -= constant[i];
        }

        return values;
    }

    /**
     * The number of bytes that writeFixedValues() will need to represent this term.
     * This is just all the member datum.
     */
    public int fixedByteSize() {
        int bitSize = 0;

        bitSize += Byte.SIZE  // squared
                + Byte.SIZE  // hinge
                + Integer.SIZE; // rule hash

        for (int i = 0; i < size.length; i++) {
            bitSize += Float.SIZE  // constant
                    + Short.SIZE  // size
                    + size[i] * (Float.SIZE + Integer.SIZE);  // coefficients + variableIndexes
        }

        return bitSize / 8;
    }

    /**
     * Write a binary representation of the fixed values of this term to a buffer.
     * Note that the variableIndexes are written using the term store indexing.
     */
    public void writeFixedValues(ByteBuffer fixedBuffer) {
        fixedBuffer.put((byte) (squared ? 1 : 0));
        fixedBuffer.put((byte) (hinge ? 1 : 0));
        fixedBuffer.putInt(System.identityHashCode(rule));

        for (int i = 0; i < size.length; i++) {
            fixedBuffer.putFloat(constant[i]);
            fixedBuffer.putShort(size[i]);

            for (int j = 0; j < size[i]; j++) {
                fixedBuffer.putFloat(coefficients[i][j]);
                fixedBuffer.putInt(variableIndexes[i][j]);
            }
        }
    }

    /**
     * Assume the term that will be next read from the buffers.
     */
    public void read(ByteBuffer fixedBuffer, ByteBuffer volatileBuffer) {
        squared = (fixedBuffer.get() == 1);
        hinge = (fixedBuffer.get() == 1);
        rule = (WeightedRule)AbstractRule.getRule(fixedBuffer.getInt());

        for (int i = 0; i < size.length; i++) {
            constant[i] = fixedBuffer.getFloat();
            size[i] = fixedBuffer.getShort();

            // Make sure that there is enough room for all these variables.
            if (coefficients[i].length < size[i]) {
                coefficients[i] = new float[size[i]];
                variableIndexes[i] = new int[size[i]];
            }

            for (int j = 0; j < size[i]; j++) {
                coefficients[i][j] = fixedBuffer.getFloat();
                variableIndexes[i][j] = fixedBuffer.getInt();
            }
        }
    }

    @Override
    public String toString() {
        return toString(null);
    }

    public String toString(VariableTermStore<SGDObjectiveTerm, GroundAtom> termStore) {
        // weight * [max(coeffs^T * x - constant, 0.0)]^2

        StringBuilder builder = new StringBuilder();

        if (size.length > 1) {
            builder.append("max{");
        }

        for (int i=0; i < size.length; i++) {
            builder.append(rule.getWeight());
            builder.append(" * ");

            if (hinge) {
                builder.append("max(0.0, ");
            } else {
                builder.append("(");
            }

            for (int j = 0; j < size[i]; j++) {
                builder.append("(");
                builder.append(coefficients[i][j]);

                if (termStore == null) {
                    builder.append(" * <index:");
                    builder.append(variableIndexes[i][j]);
                    builder.append(">)");
                } else {
                    builder.append(" * ");
                    builder.append(termStore.getVariableValue(variableIndexes[i][j]));
                    builder.append(")");
                }

                if (j != size[i] - 1) {
                    builder.append(" + ");
                }
            }

            builder.append(" - ");
            builder.append(constant[i]);

            builder.append(")");

            if (squared) {
                builder.append(" ^2");
            }
            builder.append(", ");
        }

        if (size.length > 1) {
            builder.append("}");
        }

        return builder.toString();
    }
}
