/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2017 The Regents of the University of California
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
package org.linqs.psl.model.rule.logical;

import org.linqs.psl.application.groundrulestore.GroundRuleStore;
import org.linqs.psl.database.DatabaseQuery;
import org.linqs.psl.database.ResultList;
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.model.NumericUtilities;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.atom.VariableAssignment;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.formula.FormulaAnalysis;
import org.linqs.psl.model.formula.Negation;
import org.linqs.psl.model.formula.FormulaAnalysis.DNFClause;
import org.linqs.psl.model.rule.AbstractRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.reasoner.function.FunctionTerm;
import org.linqs.psl.reasoner.function.FunctionVariable;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base class for all (first order, i.e., not ground) logical rules.
 */
abstract public class AbstractLogicalRule extends AbstractRule {
	private static final Logger log = LoggerFactory.getLogger(AbstractLogicalRule.class);

	protected Formula formula;
	protected final DNFClause clause;

	public AbstractLogicalRule(Formula f) {
		super();
		formula = f;
		FormulaAnalysis analysis = new FormulaAnalysis(new Negation(formula));

		if (analysis.getNumDNFClauses() > 1) {
			throw new IllegalArgumentException("Formula must be a disjunction of literals (or a negative literal).");
		} else {
			clause = analysis.getDNFClause(0);
		}

		Set<Variable> unboundVariables = clause.getUnboundVariables();
		if (unboundVariables.size() > 0) {
			Variable[] sortedVariables = unboundVariables.toArray(new Variable[unboundVariables.size()]);
			Arrays.sort(sortedVariables);

			throw new IllegalArgumentException(
					"Any variable used in a negated (non-functional) predicate must also participate" +
					" in a positive (non-functional) predicate." +
					" The following variables do not meet this requirement: [" + StringUtils.join(sortedVariables, ", ") + "]."
			);
		}

		if (clause.isGround()) {
			throw new IllegalArgumentException("Formula has no Variables.");
		}

		if (!clause.isQueriable()) {
			throw new IllegalArgumentException("Formula is not a valid rule for unknown reason.");
		}
	}

	public Formula getFormula() {
		return formula;
	}

	public DNFClause getDNF() {
		return clause;
	}

	@Override
	public void groundAll(AtomManager atomManager, GroundRuleStore grs) {
		ResultList res = atomManager.executeQuery(new DatabaseQuery(clause.getQueryFormula(), false));
		groundAll(res, atomManager, grs);
	}

	// TODO(eriq): Does this need to be part of the superclass?
	public void groundAll(ResultList groundVariables, AtomManager atomManager, GroundRuleStore grs) {
		int numGrounded = groundFormula(atomManager, grs, groundVariables);
		log.debug("Grounded {} instances of rule {}", numGrounded, this);
	}

	protected int groundFormula(AtomManager atomManager, GroundRuleStore grs, ResultList res) {
		int numGroundingsAdded = 0;
		List<GroundAtom> posLiterals = new ArrayList<GroundAtom>(4);
		List<GroundAtom> negLiterals = new ArrayList<GroundAtom>(4);

		// Uses these to check worst-case truth value.
		Map<FunctionVariable, Double> worstCaseValues = new HashMap<FunctionVariable, Double>(8);
		double worstCaseValue;

		GroundAtom atom;
		for (int i = 0; i < res.size(); i++) {
			for (int j = 0; j < clause.getPosLiterals().size(); j++) {
				atom = ((QueryAtom)clause.getPosLiterals().get(j)).ground(atomManager, res, i);
				if (atom instanceof RandomVariableAtom) {
					worstCaseValues.put(atom.getVariable(), 1.0);
				} else {
					worstCaseValues.put(atom.getVariable(), atom.getValue());
				}

				posLiterals.add(atom);
			}

			for (int j = 0; j < clause.getNegLiterals().size(); j++) {
				atom = ((QueryAtom)clause.getNegLiterals().get(j)).ground(atomManager, res, i);
				if (atom instanceof RandomVariableAtom) {
					worstCaseValues.put(atom.getVariable(), 0.0);
				} else {
					worstCaseValues.put(atom.getVariable(), atom.getValue());
				}

				negLiterals.add(atom);
			}

			AbstractGroundLogicalRule groundRule = groundFormulaInstance(posLiterals, negLiterals);
			FunctionTerm function = groundRule.getFunction();
			worstCaseValue = function.getValue(worstCaseValues, false);
			if (worstCaseValue > NumericUtilities.strictEpsilon
					&& (!function.isConstant() || !(groundRule instanceof WeightedGroundRule))
					&& !grs.containsGroundRule(groundRule)) {
				grs.addGroundRule(groundRule);
				numGroundingsAdded++;
			// If the ground rule is not actually added, unregisters it from atoms.
			} else {
				for (GroundAtom incidentAtom : groundRule.getAtoms()) {
					incidentAtom.unregisterGroundRule(groundRule);
				}
			}

			posLiterals.clear();
			negLiterals.clear();
			worstCaseValues.clear();
		}

		return numGroundingsAdded;
	}

	abstract protected AbstractGroundLogicalRule groundFormulaInstance(List<GroundAtom> posLiterals, List<GroundAtom> negLiterals);

	@Override
	public Rule clone() throws CloneNotSupportedException {
		throw new CloneNotSupportedException();
	}
}
