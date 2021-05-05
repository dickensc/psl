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
 * A numeric function.
 */
public interface GeneralFunction {
    public int size();

    /**
     * Returns whether the term is constant.
     */
    public boolean isConstant();

    public float getConstant();

    public float getCoefficient(int index);

    public boolean isSquared();

    public void setSquared(boolean squared);

    /**
     * Add a constant to the function.
     */
    public void add(float value);

    /**
     * Add a general term to the function.
     */
    public void add(float coefficient, GeneralFunction term);

    /**
     * Returns the functions's value
     */
    public float getValue();

    /**
     * Get the value of the function, but replace the value of a single RVA with the given value.
     * This should only be called by people who really know what they are doing.
     * Note that the value of the RVA is NOT used, it is only used to find the matching function term.
     * The general version of would be to just have a map,
     * However, the common use case is just having one variable change value and this is typically
     * very high traffic making the map (and autoboxing float) overhead noticable.
     */
    public float getValue(GroundAtom replacementAtom, float replacementValue);

    /**
     * Get the value of this function, but using the values passed in place of non-constants for the term.
     * Note that the constants still apply.
     * This is a fragile function that should only be called by the code that constructed
     * this function in the first place,
     * The passed in values must only contains entries for non-constant atoms (all constants get merged).
     * The passed in values may be larger than the number of values actually used.
     */
    public float getValue(float[] values);

    public GeneralFunction getTerm(int index);
}
