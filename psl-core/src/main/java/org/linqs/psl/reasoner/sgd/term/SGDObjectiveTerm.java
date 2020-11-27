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
package org.linqs.psl.reasoner.sgd.term;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.reasoner.term.VariableTermStore;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.ReasonerTerm;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * A term in the objective to be optimized by a SGDReasoner.
 */
public class SGDObjectiveTerm implements ReasonerTerm  {
    private boolean squared;
    private boolean hinge;
    private boolean mutualInformation;

    private float weight;
    private float constant;
    private float learningRate;

    private short size;
    private float[] coefficients;
    private int[] variableIndexes;

    public SGDObjectiveTerm(VariableTermStore<SGDObjectiveTerm, GroundAtom> termStore,
            boolean squared, boolean hinge, boolean mutualInformation,
            Hyperplane<GroundAtom> hyperplane,
            float weight, float learningRate) {
        this.squared = squared;
        this.hinge = hinge;
        this.mutualInformation = mutualInformation;

        this.weight = weight;
        this.learningRate = learningRate;

        size = (short)hyperplane.size();
        coefficients = hyperplane.getCoefficients();
        constant = hyperplane.getConstant();

        variableIndexes = new int[size];
        GroundAtom[] variables = hyperplane.getVariables();
        for (int i = 0; i < size; i++) {
            variableIndexes[i] = termStore.getVariableIndex(variables[i]);
        }
    }

    public int getVariableIndex(int i) {
        return variableIndexes[i];
    }

    @Override
    public int size() {
        return size;
    }

    public float evaluate(float[] variableValues, GroundAtom[] variableAtoms) {
        float dot = dot(variableValues);

        if (mutualInformation) {
            return computeMutualInformation(variableValues, variableAtoms);
        } else if (squared && hinge) {
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
    public float minimize(int iteration, VariableTermStore termStore) {
        float movement = 0.0f;

        GroundAtom[] variableAtoms = termStore.getVariableAtoms();
        float[] variableValues = termStore.getVariableValues();

        if (!mutualInformation) {
            for (int i = 0; i < size; i++) {
                if (variableAtoms[variableIndexes[i]] instanceof ObservedAtom) {
                    continue;
                }

                float dot = dot(variableValues);
                float gradient = computeGradient(i, dot);
                float gradientStep = gradient * (learningRate / iteration);

                float newValue = Math.max(0.0f, Math.min(1.0f, variableValues[variableIndexes[i]] - gradientStep));
                movement += Math.abs(newValue - variableValues[variableIndexes[i]]);
                variableValues[variableIndexes[i]] = newValue;
            }
        } else {
            Map<Constant, List<Constant>> stakeholderAttributeMap = computeStakeholderAttributeMap(variableAtoms, variableValues);
            float targetProbability = computeTargetProbability(variableValues);
            Map<Constant, Float> attributeConditionedTargetProbability = computeAttributeConditionedTargetProbability(variableAtoms, variableValues, stakeholderAttributeMap);

            for (int i = 0; i < size; i++) {
                if (variableAtoms[variableIndexes[i]] instanceof ObservedAtom) {
                    continue;
                }

                float gradient = computeMutualInformationGradient(i, variableAtoms,
                        attributeConditionedTargetProbability, stakeholderAttributeMap, targetProbability);
                float step = gradient * (learningRate / iteration);

                float newValue = Math.max(0.0f, Math.min(1.0f, variableValues[variableIndexes[i]] - step));
                movement += Math.abs(newValue - variableValues[variableIndexes[i]]);
                variableValues[variableIndexes[i]] = newValue;
            }
        }

        return movement;
    }

    private Map<Constant, List<Constant>> computeStakeholderAttributeMap(GroundAtom[] variableAtoms, float[] variableValues) {
        Map<Constant, List<Constant>> stakeholderAttributeMap = new HashMap<Constant, List<Constant>>();

        for (int i = 0; i < size; i++) {
            if (coefficients[i] == 1) {
                // LHS Atom
                continue;
            } else {
                // RHS Atom
                // TODO(Charles): Assumes 0 or 1 valued attribute atoms.
                //  This does not work if attribute is soft or being inferred.
                if (variableValues[variableIndexes[i]] == 1.0f) {
                    if (stakeholderAttributeMap.containsKey(variableAtoms[variableIndexes[i]].getArguments()[0])) {
                        stakeholderAttributeMap.get(variableAtoms[variableIndexes[i]].getArguments()[0]).add(variableAtoms[variableIndexes[i]].getArguments()[1]);
                    } else {
                        stakeholderAttributeMap.put(variableAtoms[variableIndexes[i]].getArguments()[0],
                                new LinkedList<Constant>(Arrays.asList(variableAtoms[variableIndexes[i]].getArguments()[1])));
                    }
                }
            }
        }

        return stakeholderAttributeMap;
    }

    private Map<Constant, Float> computeAttributeConditionedTargetProbability(GroundAtom[] variableAtoms, float[] variableValues,
                                                                              Map<Constant, List<Constant>> stakeholderAttributeMap) {
        Map<Constant, Integer> attributeCount = new HashMap<Constant, Integer>();
        Map<Constant, Float> attributeConditionedTargetProbability = new HashMap<Constant, Float>();
        Map<Constant, Float> stakeholderConditionedTargetProbability = new HashMap<Constant, Float>();

        for (int i = 0; i < size; i++) {
            if (coefficients[i] == 1) {
                //LHS Atom
                // TODO(Charles): Assuming only one value per stakeholder conditioned target.
                stakeholderConditionedTargetProbability.put(variableAtoms[variableIndexes[i]].getArguments()[0],
                        variableValues[variableIndexes[i]]);
            } else {
                // RHS Atom
                // Need to populate stakeholderConditionedTargetProbability before counting attributes.
            }
        }

        // TODO(Charles): There are cases when a stakeholder does not ground for both RHS and LHS.
        for (Constant stakeholder : stakeholderConditionedTargetProbability.keySet()) {
            for (Constant attribute : stakeholderAttributeMap.get(stakeholder)){
                if (attributeCount.containsKey(attribute)) {
                    attributeCount.put(attribute, attributeCount.get(attribute) + 1);
                } else {
                    attributeCount.put(attribute, 1);
                }
            }
        }

        for (Constant stakeholder : stakeholderConditionedTargetProbability.keySet()) {
            for (Constant attribute : stakeholderAttributeMap.get(stakeholder)){
                if (attributeConditionedTargetProbability.containsKey(attribute)) {
                    attributeConditionedTargetProbability.put(attribute,
                            attributeConditionedTargetProbability.get(attribute) + stakeholderConditionedTargetProbability.get(stakeholder) / attributeCount.get(attribute));
                } else {
                    attributeConditionedTargetProbability.put(attribute, stakeholderConditionedTargetProbability.get(stakeholder) / attributeCount.get(attribute));
                }
            }
        }

        return attributeConditionedTargetProbability;
    }

    private Map<Constant, Float> computeAttributeProbability(float[] variableValues, GroundAtom[] variableAtoms,
                                                             Map<Constant, List<Constant>> stakeholderAttributeMap) {
        Map<Constant, Float> attributeProbability = new HashMap<Constant, Float>();
        Map<Constant, Integer> attributeCount = new HashMap<Constant, Integer>();

        for (int i = 0; i < size; i++) {
            if (coefficients[i] == 1) {
                //LHS Atom
            } else {
                // RHS Atom
                // TODO(Charles): Assumes 0'th entry is stakeholder and 1'st entry is attribute.
                //  Assumes stakeholder is common for LHS and RHS atoms.
                if (variableValues[variableIndexes[i]] == 1.0f) {
                    if (attributeCount.containsKey(variableAtoms[variableIndexes[i]].getArguments()[1])) {
                        attributeCount.put(variableAtoms[variableIndexes[i]].getArguments()[1],
                                attributeCount.get(variableAtoms[variableIndexes[i]].getArguments()[1]) + 1);
                    } else {
                        attributeCount.put(variableAtoms[variableIndexes[i]].getArguments()[1], 1);
                    }
                }
            }
        }

        for (Constant attribute : attributeCount.keySet()) {
            attributeProbability.put(attribute, (float) attributeCount.get(attribute) / stakeholderAttributeMap.keySet().size());
        }

        return attributeProbability;
    }

    private float computeTargetProbability(float[] variableValues) {
        float targetProbability = 0.0f;
        int stakeholderCount = 0;

        for (int i = 0; i < size; i++) {
            if (coefficients[i] == 1) {
                // LHS Atom
                targetProbability += variableValues[variableIndexes[i]];

                // TODO(Charles): Assuming stakeholder set for open predicate contains stakeholder set for attributes.
                stakeholderCount += 1;
            }
        }

        return targetProbability / (float) stakeholderCount;
    }

    private float computeMutualInformationGradient(int varId, GroundAtom[] variableAtoms,
                                                   Map<Constant, Float> attributeConditionedTargetProbability,
                                                   Map<Constant, List<Constant>> stakeholderAttributeMap,
                                                   float targetProbability) {
        float gradient = 0.0f;
        float stakeholderCount = stakeholderAttributeMap.keySet().size();
        // TODO(Charles): Assumes stakeholder is always the 0'th argument.
        Constant stakeholder = variableAtoms[variableIndexes[varId]].getArguments()[0];

        // TODO(Charles): Assuming attributes are mutually exclusive and every stakeholder has exactly one attribute.
        for (Constant attribute : attributeConditionedTargetProbability.keySet()) {
            if (!stakeholderAttributeMap.get(stakeholder).contains(attribute)){
                // Partial of P(y | s) w.r.t tar(u, t) is 0 for this case.
                continue;
            }

            // target = 1 term
            if ((targetProbability != 0.0f) && (attributeConditionedTargetProbability.get(attribute) != 0.0f)) {
                gradient += Math.log(attributeConditionedTargetProbability.get(attribute) / targetProbability) / (float) stakeholderCount;
            } else if ((targetProbability == 0.0f) && (attributeConditionedTargetProbability.get(attribute) == 0.0f)) {
                // Define log(0/0) = log(1) = 0
                gradient += 0.0f;
            } else if (targetProbability == 0.0f) {
                // Should never happen. if P(Y = 1) = 0 then P(Y = 1| S = s) cannot be anything besides 0.
                throw new IllegalStateException(String.format("Attribute conditional probability cannot be greater than 0 if probability is 0. " +
                        "Conditional probability: %f " +
                        "\nTarget Probability: %f", attributeConditionedTargetProbability.get(attribute), targetProbability));
            } else if (attributeConditionedTargetProbability.get(attribute) == 0.0f) {
                // Happens if attribute completely determines target. Should be -infinity.
                gradient += -1000.0f / (float) stakeholderCount;
            }

            // target = 0 term
            if ((1.0f - targetProbability != 0.0f) && (1.0f - attributeConditionedTargetProbability.get(attribute) != 0.0f)) {
                gradient -=  Math.log((1.0f - attributeConditionedTargetProbability.get(attribute)) / (1.0f - targetProbability)) / (float) stakeholderCount;
            } else if ((1 - targetProbability == 0.0f) && (1 - attributeConditionedTargetProbability.get(attribute) == 0.0f)) {
                // Define log(0/0) = log(1) = 0
                gradient += 0.0f;
            } else if (1 - targetProbability == 0.0f) {
                // Should never happen. if P(Y = 1) = 0 then P(Y = 1| S = s) cannot be anything besides 0.
                throw new IllegalStateException(String.format("Attribute conditional probability cannot be greater than 0 if probability is 0. " +
                        "Conditional probability: %f " +
                        "Target Probability: %f", 1 - attributeConditionedTargetProbability.get(attribute), 1 - targetProbability));
            } else if (1 - attributeConditionedTargetProbability.get(attribute) == 0.0f) {
                gradient -= -1000.0f / (float) stakeholderCount;
            }
        }

        return gradient;
    }

    private float computeMutualInformation(float[] variableValues, GroundAtom[] variableAtoms) {
        float mutualInformation = 0.0f;

        Map<Constant, List<Constant>> stakeholderAttributeMap = computeStakeholderAttributeMap(variableAtoms, variableValues);
        float targetProbability = computeTargetProbability(variableValues);
        Map<Constant, Float> attributeConditionedTargetProbability = computeAttributeConditionedTargetProbability(variableAtoms, variableValues, stakeholderAttributeMap);
        Map<Constant, Float> attributeProbability = computeAttributeProbability(variableValues, variableAtoms, stakeholderAttributeMap);

        // TODO(Charles): Assuming attributes are mutually exclusive and every stakeholder has exactly one attribute.
        for (Constant attribute : attributeConditionedTargetProbability.keySet()) {
            // target = 1 term
            if ((targetProbability != 0.0f) && (attributeConditionedTargetProbability.get(attribute) != 0.0f)) {
                mutualInformation += (attributeProbability.get(attribute) * attributeConditionedTargetProbability.get(attribute)
                        * Math.log(attributeConditionedTargetProbability.get(attribute) / targetProbability));
            } else if ((targetProbability == 0.0f) && (attributeConditionedTargetProbability.get(attribute) == 0.0f)) {
                // Define log(0/0) = log(1) = 0
                mutualInformation += 0.0f;
            } else if (targetProbability == 0.0f) {
                // Should never happen. if P(Y = 1) = 0 then P(Y = 1| S = s) cannot be anything besides 0.
                throw new IllegalStateException(String.format("Attribute conditional probability cannot be greater than 0 if probability is 0. " +
                        "Conditional probability: %f " +
                        "\nTarget Probability: %f", attributeConditionedTargetProbability.get(attribute), targetProbability));
            } else if (attributeConditionedTargetProbability.get(attribute) == 0.0f) {
                // 0 log(0) = 0
                mutualInformation += 0.0f;
            }

            // target = 0 term
            if ((1.0f - targetProbability != 0.0f) && (1.0f - attributeConditionedTargetProbability.get(attribute) != 0.0f)) {
                mutualInformation += (attributeProbability.get(attribute) * (1 - attributeConditionedTargetProbability.get(attribute))
                        * Math.log((1 - attributeConditionedTargetProbability.get(attribute)) / (1 - targetProbability)));
            } else if ((1 - targetProbability == 0.0f) && (1 - attributeConditionedTargetProbability.get(attribute) == 0.0f)) {
                mutualInformation += 0.0f;
            } else if (1 - targetProbability == 0.0f) {
                // Should never happen. if P(Y = 0) = 0 then P(Y = 0| S = s) cannot be anything besides 0.
                throw new IllegalStateException(String.format("Attribute conditional probability cannot be greater than 0 if probability is 0. " +
                        "Conditional probability: %f " +
                        "Target Probability: %f", 1 - attributeConditionedTargetProbability.get(attribute), 1 - targetProbability));
            } else if (1 - attributeConditionedTargetProbability.get(attribute) == 0.0f) {
                // 0 log(0) = 0
                mutualInformation += 0.0f;
            }
        }

        return mutualInformation;
    }

    private float computeGradient(int varId, float dot) {
        if (hinge && dot <= 0.0f) {
            return 0.0f;
        }

        if (squared) {
            return weight * 2.0f * dot * coefficients[varId];
        }

        return weight * coefficients[varId];
    }

    private float dot(float[] variableValues) {
        float value = 0.0f;

        for (int i = 0; i < size; i++) {
            value += coefficients[i] * variableValues[variableIndexes[i]];
        }

        return value - constant;
    }

    /**
     * The number of bytes that writeFixedValues() will need to represent this term.
     * This is just all the member datum.
     */
    public int fixedByteSize() {
        int bitSize =
            Byte.SIZE  // squared
            + Byte.SIZE  // hinge
            + Float.SIZE  // weight
            + Float.SIZE  // constant
            + Float.SIZE  // learningRate
            + Short.SIZE  // size
            + size * (Float.SIZE + Integer.SIZE);  // coefficients + variableIndexes

        return bitSize / 8;
    }

    /**
     * Write a binary representation of the fixed values of this term to a buffer.
     * Note that the variableIndexes are written using the term store indexing.
     */
    public void writeFixedValues(ByteBuffer fixedBuffer) {
        fixedBuffer.put((byte)(squared ? 1 : 0));
        fixedBuffer.put((byte)(hinge ? 1 : 0));
        fixedBuffer.putFloat(weight);
        fixedBuffer.putFloat(constant);
        fixedBuffer.putFloat(learningRate);
        fixedBuffer.putShort(size);

        for (int i = 0; i < size; i++) {
            fixedBuffer.putFloat(coefficients[i]);
            fixedBuffer.putInt(variableIndexes[i]);
        }
    }

    /**
     * Assume the term that will be next read from the buffers.
     */
    public void read(ByteBuffer fixedBuffer, ByteBuffer volatileBuffer) {
        squared = (fixedBuffer.get() == 1);
        hinge = (fixedBuffer.get() == 1);
        weight = fixedBuffer.getFloat();
        constant = fixedBuffer.getFloat();
        learningRate = fixedBuffer.getFloat();
        size = fixedBuffer.getShort();

        // Make sure that there is enough room for all these variables.
        if (coefficients.length < size) {
            coefficients = new float[size];
            variableIndexes = new int[size];
        }

        for (int i = 0; i < size; i++) {
            coefficients[i] = fixedBuffer.getFloat();
            variableIndexes[i] = fixedBuffer.getInt();
        }
    }

    @Override
    public String toString() {
        return toString(null);
    }

    public String toString(VariableTermStore<SGDObjectiveTerm, GroundAtom> termStore) {
        // weight * [max(coeffs^T * x - constant, 0.0)]^2

        StringBuilder builder = new StringBuilder();

        builder.append(weight);
        builder.append(" * ");

        if (hinge) {
            builder.append("max(0.0, ");
        } else {
            builder.append("(");
        }

        for (int i = 0; i < size; i++) {
            builder.append("(");
            builder.append(coefficients[i]);

            if (termStore == null) {
                builder.append(" * <index:");
                builder.append(variableIndexes[i]);
                builder.append(">)");
            } else {
                builder.append(" * ");
                builder.append(termStore.getVariableValue(variableIndexes[i]));
                builder.append(")");
            }

            if (i != size - 1) {
                builder.append(" + ");
            }
        }

        builder.append(" - ");
        builder.append(constant);

        builder.append(")");

        if (squared) {
            builder.append(" ^2");
        }

        return builder.toString();
    }
}
