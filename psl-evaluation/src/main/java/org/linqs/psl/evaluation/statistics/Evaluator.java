/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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
import org.linqs.psl.database.Database;
import org.linqs.psl.database.atom.PersistedAtomManager;
import org.linqs.psl.model.predicate.StandardPredicate;

/**
 * Compute some metric (or set of matrics) for some predicted and labeled data.
 * Every computer will have some "representative metric" that is usually set through config options.
 * This is so that more automated methods like search-based weight learning and CLI evaluation
 * can easily access all types of metrics.
 * The computer will also have to provide some indication as to if higher values for the
 * representative metric is better than lower values.
 * In addition to the representative metric, subclasses classes will usually
 * provide their own methods to access specific metrics.
 */
public abstract class Evaluator {
	/**
	 * The main computation method.
	 * This must be called before any of the metric retrival methods.
	 * Only values in the TrainingMap are computed over.
	 */
	public abstract void compute(TrainingMap data, StandardPredicate predicate);

	public abstract double getRepresentativeMetric();

	public abstract boolean isHigherRepresentativeBetter();

	/**
	 * A convenience call for those who don't want to create a training map directly.
	 */
	public void compute(Database rvDB, Database truthDB, StandardPredicate predicate) {
		PersistedAtomManager atomManager = new PersistedAtomManager(rvDB);
		TrainingMap map = new TrainingMap(atomManager, truthDB);
		compute(map, predicate);
	}
}