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
package org.linqs.psl.reasoner.function;

import org.linqs.psl.model.atom.GroundAtom;

/**
 * A linear combination of terms.
 */
public class LinearFunction extends AbstractFunction {
    private boolean constantTerms;

    public LinearFunction(int maxSize, boolean squared, boolean mergeConstants) {
        super(maxSize, squared, mergeConstants);
        constantTerms = true;
    }

    @Override
    public boolean isConstant() {
        return constantTerms;
    }

    /**
     * Add a constant to the sum.
     */
    @Override
    public void add(float value) {
        constant += value;
    }

    /**
     * Add a general term to the sum.
     */
    @Override
    public void add(float coefficient, GeneralFunction term) {
        // Merge constants.
        if (mergeConstants && term.isConstant()) {
            constant += (coefficient * term.getValue());
            return;
        }

        if (size == terms.length) {
            throw new IllegalStateException(
                    "More than the max terms added to the function. Max: " + terms.length);
        }

        terms[size] = term;
        coefficients[size] = coefficient;
        size++;

        constantTerms = constantTerms && term.isConstant();
    }

    protected void setConstant(float val) {
        constant = val;
    }

    @Override
    public float getValue() {
        float val = getSum();

        return squared ? (val * val) : val;
    }

    protected float getSum() {
        float val = constant;

        for (int i = 0; i < size; i++) {
            val += terms[i].getValue() * coefficients[i];
        }

        return val;
    }

    @Override
    public float getValue(float[] values) {
        float val = getSum(values);

        return squared ? (val * val) : val;
    }

    public float getSum(float[] values) {
        float val = constant;

        for (int i = 0; i < size; i++) {
            val += coefficients[i] * values[i];
        }

        return val;
    }

    @Override
    public float getValue(GroundAtom replacementAtom, float replacementValue) {
        float val = getSum(replacementAtom, replacementValue);

        return squared ? (val * val) : val;
    }

    public float getSum(GroundAtom replacementAtom, float replacementValue) {
        float val = constant;

        // Use numeric for loops instead of iterators in high traffic code.
        for (int i = 0; i < size; i++) {
            GeneralFunction term = terms[i];
            float coefficient = coefficients[i];

            // Only one instance of each atom exists and we are trying to match it directly.
            if (term == replacementAtom) {
                val += coefficient * replacementValue;
            } else {
                val += coefficient * term.getValue();
            }
        }

        return val;
    }

    @Override
    public String toString() {
        StringBuilder string = new StringBuilder();

        string.append("(");
        string.append(constant);

        for (int i = 0; i < size; i++) {
            GeneralFunction term = terms[i];
            float coefficient = coefficients[i];

            string.append(" + ");
            string.append("" + coefficient + " * " + term.toString());
        }

        string.append(")");

        if (squared) {
            string.append("^2");
        }

        return string.toString();
    }
}
