package org.linqs.psl.application.inference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.linqs.psl.PSLTest;
import org.linqs.psl.TestModelFactory;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.Queries;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.formula.Implication;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.arithmetic.WeightedArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtom;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtomOrAtom;
import org.linqs.psl.model.rule.arithmetic.expression.SummationVariable;
import org.linqs.psl.model.rule.arithmetic.expression.SummationVariableOrTerm;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Cardinality;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Coefficient;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.ConstantNumber;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.reasoner.function.FunctionComparator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LazyMPEInferenceTest {
	// TODO(eriq): Tests:
	//  - base: not nice
	//  - below threshold (will require different rules that don't hit all targets on initial grounding)
	//  - multiple predicates
	//  - partially observed
	//  - rules such that no instantiation will happen
	//  - arithmetic rules

	private Database inferDB;
	private Partition targetPartition;
	private Set<StandardPredicate> allPredicates;
	private Set<StandardPredicate> closedPredicates;
	private TestModelFactory.ModelInformation info;

	@Before
	public void setup() {
		initModel(true);
	}

	@After
	public void cleanup() {
		PSLTest.disableLogger();

		inferDB.close();
		inferDB = null;

		info.dataStore.close();
		info = null;
	}

	private void initModel(boolean useNice) {
		if (inferDB != null) {
			inferDB.close();
			inferDB = null;
		}

		if (info != null) {
			info.dataStore.close();
			info = null;
		}

		info = TestModelFactory.getModel(useNice);

		// Get an empty partition so that no targets will exist in it and we will have to lazily instantiate them all.
		targetPartition = info.dataStore.getPartition(TestModelFactory.PARTITION_UNUSED);

		allPredicates = new HashSet<StandardPredicate>(info.predicates.values());
		closedPredicates = new HashSet<StandardPredicate>(info.predicates.values());
		closedPredicates.remove(info.predicates.get("Friends"));

		inferDB = info.dataStore.getDatabase(targetPartition, closedPredicates, info.observationPartition);
	}

	/**
	 * A quick test that only checks to see if LazyMPEInference is running.
	 * This is not a targeted or exhaustive test, just a starting point.
	 */
	@Test
	public void testBase() {
		LazyMPEInference mpe = new LazyMPEInference(info.model, inferDB, info.config);

		// The Friends predicate should be empty.
		assertEquals(0, Queries.countAllGroundRandomVariableAtoms(inferDB, info.predicates.get("Friends")));

		mpe.mpeInference();

		// Now the Friends predicate should have the crossproduct (5x5) minus self pairs (5) in it.
		assertEquals(20, Queries.countAllGroundRandomVariableAtoms(inferDB, info.predicates.get("Friends")));

		mpe.close();
	}

	/**
	 * Ensure that simple arithmetic groundings (no summation atoms) works.
	 */
	@Test
	public void testSimpleArithmeticBase() {
		// 1.0: Friends(A, B) >= 0.5 ^2
		List<Coefficient> coefficients = Arrays.asList(
			(Coefficient)(new ConstantNumber(1))
		);

		List<SummationAtomOrAtom> atoms = Arrays.asList(
			(SummationAtomOrAtom)(new QueryAtom(info.predicates.get("Friends"), new Variable("A"), new Variable("B")))
		);

		Rule rule = new WeightedArithmeticRule(
				new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.LargerThan, new ConstantNumber(0.5)),
				1.0,
				true
		);
		info.model.addRule(rule);

		LazyMPEInference mpe = new LazyMPEInference(info.model, inferDB, info.config);

		// The Friends predicate should be empty.
		assertEquals(0, Queries.countAllGroundRandomVariableAtoms(inferDB, info.predicates.get("Friends")));

		mpe.mpeInference();

		// Now the Friends predicate should have the crossproduct (5x5) minus self pairs (5) in it.
		assertEquals(20, Queries.countAllGroundRandomVariableAtoms(inferDB, info.predicates.get("Friends")));

		mpe.close();
	}

	/**
	 * Ensure that complex arithmetic groundings (has summation atoms) works.
	 */
	@Test
	public void testComplexArithmeticBase() {
		// TEST
		PSLTest.initLogger("TRACE");

		// |B| * Friends(A, +B) >= 1 {B: Nice(B)}

		List<Coefficient> coefficients = Arrays.asList(
			(Coefficient)(new Cardinality(new SummationVariable("B")))
		);

		List<SummationAtomOrAtom> atoms = Arrays.asList(
			(SummationAtomOrAtom)(new SummationAtom(
				info.predicates.get("Friends"),
				new SummationVariableOrTerm[]{new Variable("A"), new SummationVariable("B")}
			))
		);

		Map<SummationVariable, Formula> filters = new HashMap<SummationVariable, Formula>();
		filters.put(new SummationVariable("B"), new QueryAtom(info.predicates.get("Nice"), new Variable("B")));

		Rule rule = new WeightedArithmeticRule(
				new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.LargerThan, new ConstantNumber(1.0)),
				filters,
				1.0,
				true
		);
		info.model.addRule(rule);

		LazyMPEInference mpe = new LazyMPEInference(info.model, inferDB, info.config);

		// The Friends predicate should be empty.
		assertEquals(0, Queries.countAllGroundRandomVariableAtoms(inferDB, info.predicates.get("Friends")));

		mpe.mpeInference();

		// Now the Friends predicate should have the crossproduct (5x5) minus self pairs (5) in it.
		assertEquals(20, Queries.countAllGroundRandomVariableAtoms(inferDB, info.predicates.get("Friends")));

		mpe.close();
	}

	/**
	 * Make sure lazy inference works even when everything is fully specified.
	 */
	@Test
	public void testFullySpecified() {
		Database fullTargetDB = info.dataStore.getDatabase(info.targetPartition, closedPredicates, info.observationPartition);
		LazyMPEInference mpe = new LazyMPEInference(info.model, fullTargetDB, info.config);

		// The Friends predicate should be fully defined.
		assertEquals(20, Queries.countAllGroundRandomVariableAtoms(fullTargetDB, info.predicates.get("Friends")));

		mpe.mpeInference();

		assertEquals(20, Queries.countAllGroundRandomVariableAtoms(fullTargetDB, info.predicates.get("Friends")));

		mpe.close();
		fullTargetDB.close();
	}
}
