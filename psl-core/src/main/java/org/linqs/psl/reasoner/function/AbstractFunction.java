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
 * A general function that can handle various cases.
 * All constant values are merged together into a single constant.
 * Note that the design of this function is to reduce the number of
 * allocations at grounding time.
 */
public abstract class AbstractFunction implements GeneralFunction {
    protected final float[] coefficients;
    protected final GeneralFunction[] terms;
    // Whether to merge fixed values (like observed atoms) into the single constant.
    protected final boolean mergeConstants;

    protected short size;

    // All constants will get merged into this.
    protected float constant;

    protected boolean constantTerms;
    protected boolean squared;

    public AbstractFunction(int maxSize, boolean squared, boolean mergeConstants) {
        coefficients = new float[maxSize];
        terms = new GeneralFunction[maxSize];
        size = 0;
        constant = 0.0f;

        this.mergeConstants = mergeConstants;
        this.squared = squared;
        constantTerms = true;
    }

    public float getConstant() {
        return constant;
    }

    public boolean isSquared() {
        return squared;
    }

    public boolean isConstant() {
        return constantTerms;
    }

    public void setSquared(boolean squared) {
        this.squared = squared;
    }

    public int size() {
        return size;
    }

    public float getCoefficient(int index) {
        return coefficients[index];
    }

    public GeneralFunction getTerm(int index) {
        return terms[index];
    }
}
