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
package org.linqs.psl.application.inference.mpe;

import org.linqs.psl.application.inference.InferenceApplication;
import org.linqs.psl.application.inference.InferenceTest;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.rule.Rule;

import java.util.List;

public class DCDStreamingInferenceTest extends InferenceTest {
    @Override
    protected InferenceApplication getInference(List<Rule> rules, Database db) {
        return new DCDStreamingInference(rules, db);
    }

    @Override
    public void testLogicalTautologyTrivial() {
        // Streaming methods cannot use this test since it requires
        // ground rules and terms to be kept in memory.
    }
}
