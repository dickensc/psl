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

import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.util.ArrayUtils;

import java.lang.reflect.Array;

/**
 * Information representing a raw online.
 */
public class Online<E extends ReasonerLocalVariable> {
    private E[] variables;
    private ObservedAtom[] observed;
    private float[] coefficients;
    private float[] observed_coefficients;
    private int VariableIndex;
    private int ObservedIndex;
    private float constant;

    @SuppressWarnings("unchecked")
    public Online(Class<E> localVariableClass, int maxVariableSize, int maxObservedSize, float constant) {
        this((E[])Array.newInstance(localVariableClass, maxVariableSize), new ObservedAtom [maxObservedSize],
                new float[maxVariableSize], new float[maxObservedSize], constant, 0, 0);
    }

    public Online(E[] variables, ObservedAtom[] observed, float[] coefficients, float[] observed_coefficients,
                  float constant, int variableIndex, int observedIndex) {
        this.variables = variables;
        this.observed = observed;
        this.coefficients = coefficients;
        this.observed_coefficients = observed_coefficients;
        this.constant = constant;
        this.VariableIndex = variableIndex;
        this.ObservedIndex = observedIndex;
    }

    public void addTerm(E variable, float coefficient) {
        variables[VariableIndex] = variable;
        coefficients[VariableIndex] = coefficient;
        VariableIndex++;
    }

    public void addObservedTerm(ObservedAtom variable, float coefficient) {
        observed[ObservedIndex] = variable;
        observed_coefficients[ObservedIndex] = coefficient;
        ObservedIndex++;
    }

    public int size() {
        return VariableIndex;
    }

    public int observed_size() {
        return ObservedIndex;
    }

    public E getVariable(int index) {
        if (index >= VariableIndex) {
            throw new IndexOutOfBoundsException("Tried to access variable at index " + index + ", but only " + VariableIndex + " exist.");
        }

        return variables[index];
    }

    public ObservedAtom getObserved(int index) {
        if (index >= ObservedIndex) {
            throw new IndexOutOfBoundsException("Tried to access variable at index " + index + ", but only " + ObservedIndex + " exist.");
        }

        return observed[index];
    }

    public float getCoefficient(int index) {
        if (index >= VariableIndex) {
            throw new IndexOutOfBoundsException("Tried to access coefficient at index " + index + ", but only " + VariableIndex + " exist.");
        }

        return coefficients[index];
    }

    public float getObservedCoefficient(int index) {
        if (index >= ObservedIndex) {
            throw new IndexOutOfBoundsException("Tried to access coefficient at index " + index + ", but only " + ObservedIndex + " exist.");
        }

        return observed_coefficients[index];
    }

    public void appendCoefficient(int index, float value) {
        if (index >= VariableIndex) {
            throw new IndexOutOfBoundsException("Tried to access coefficient at index " + index + ", but only " + VariableIndex + " exist.");
        }

        coefficients[index] += value;
    }

    public void appendObservedCoefficient(int index, float value) {
        if (index >= ObservedIndex) {
            throw new IndexOutOfBoundsException("Tried to access coefficient at index " + index + ", but only " + ObservedIndex + " exist.");
        }

        observed_coefficients[index] += value;
    }

    public float getConstant() {
        return constant;
    }

    public void setConstant(float constant) {
        this.constant = constant;
    }

    public int indexOfVariable(E needle) {
        return ArrayUtils.indexOf(variables, VariableIndex, needle);
    }

    public int indexOfObserved(ObservedAtom needle) {
        return ArrayUtils.indexOf(observed, ObservedIndex, needle);
    }

    public E[] getVariables() {
        return variables;
    }

    public ObservedAtom[] getObserveds() {
        return observed;
    }

    public float[] getCoefficients() {
        return coefficients;
    }

    public float[] getObservedCoefficients() {
        return observed_coefficients;
    }
}
