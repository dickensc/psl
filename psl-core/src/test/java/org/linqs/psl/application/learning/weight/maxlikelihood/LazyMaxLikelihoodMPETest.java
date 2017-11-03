package org.linqs.psl.application.learning.weight.maxlikelihood;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.linqs.psl.PSLTest;
import org.linqs.psl.TestModelFactory;
import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.application.learning.weight.maxlikelihood.LazyMaxLikelihoodMPE;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.Partition;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Implication;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.model.term.UniqueStringID;
import org.linqs.psl.model.term.Variable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class LazyMaxLikelihoodMPETest {
	private Database weightLearningTrainDB;
	private Database weightLearningTruthDB;
	private Partition targetPartition;
	private TestModelFactory.ModelInformation info;

	@Before
	public void setup() {
		initModel(true);
	}

	@After
	public void cleanup() {
		PSLTest.disableLogger();

		weightLearningTrainDB.close();
		weightLearningTrainDB = null;

		weightLearningTruthDB.close();
		weightLearningTruthDB = null;

		info.dataStore.close();
		info = null;
	}

	private void initModel(boolean useNice) {
		if (weightLearningTrainDB != null) {
			weightLearningTrainDB.close();
			weightLearningTrainDB = null;
		}

		if (weightLearningTruthDB != null) {
			weightLearningTruthDB.close();
			weightLearningTruthDB = null;
		}

		if (info != null) {
			info.dataStore.close();
			info = null;
		}

		info = TestModelFactory.getModel(useNice);

		// Get an empty partition so that no targets will exist in it and we will have to lazily instantiate them all.
		targetPartition = info.dataStore.getPartition(TestModelFactory.PARTITION_UNUSED);

		Set<StandardPredicate> allPredicates = new HashSet<StandardPredicate>(info.predicates.values());
		Set<StandardPredicate> closedPredicates = new HashSet<StandardPredicate>(info.predicates.values());
		closedPredicates.remove(info.predicates.get("Friends"));

		weightLearningTrainDB = info.dataStore.getDatabase(targetPartition, closedPredicates, info.observationPartition);
		weightLearningTruthDB = info.dataStore.getDatabase(info.truthPartition, allPredicates, info.observationPartition);
	}

	/**
	 * A quick test that only checks to see if MPEInference is running.
	 * This is not a targeted or exhaustive test, just a starting point.
	 */
	@Test
	public void baseTest() {
		WeightLearningApplication weightLearner = null;
		try {
			weightLearner = new LazyMaxLikelihoodMPE(info.model, weightLearningTrainDB, weightLearningTruthDB, info.config);
		} catch (Exception ex) {
			System.out.println(ex);
			ex.printStackTrace();
			fail("Exception thrown during MPE constructor.");
		}

		try {
			weightLearner.learn();
		} catch (Exception ex) {
			System.out.println(ex);
			ex.printStackTrace();
			fail("Exception thrown during weight learning.");
		}

		weightLearner.close();
	}

	/**
	 * Ensure that a rule with no groundings does not break.
	 */
	@Test
	public void ruleWithNoGroundingsTest() {
		// Add in a rule that will have zero groundings.
		// People are not friends with themselves.
		Rule newRule = new WeightedLogicalRule(
			new Implication(
				new Conjunction(
					new QueryAtom(info.predicates.get("Nice"), new UniqueStringID("ZzZ__FAKE_PERSON_A__ZzZ")),
					new QueryAtom(info.predicates.get("Nice"), new Variable("B"))
				),
				new QueryAtom(info.predicates.get("Friends"), new UniqueStringID("ZzZ__FAKE_PERSON_A__ZzZ"), new Variable("B"))
			),
			5.0,
			true
		);
		info.model.addRule(newRule);

		WeightLearningApplication weightLearner = new LazyMaxLikelihoodMPE(info.model, weightLearningTrainDB, weightLearningTruthDB, info.config);

		try {
			weightLearner.learn();
		} catch (Exception ex) {
			System.out.println(ex);
			ex.printStackTrace();
			fail("Exception thrown during weight learning.");
		}

		weightLearner.close();
	}
}
