package org.matsim.prepare.choices;

import org.jetbrains.annotations.Nullable;
import org.matsim.modechoice.CandidateGenerator;
import org.matsim.modechoice.PlanCandidate;
import org.matsim.modechoice.PlanModel;
import org.matsim.modechoice.search.TopKChoicesGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Generates random candidates.
 */
public class RandomPlanGenerator implements CandidateGenerator {

	private final int topK;
	private final TopKChoicesGenerator gen;
	private final SplittableRandom rnd = new SplittableRandom(0);

	public RandomPlanGenerator(int topK, TopKChoicesGenerator generator) {
		this.topK = topK;
		this.gen = generator;
	}

	@Override
	public List<PlanCandidate> generate(PlanModel planModel, @Nullable Set<String> consideredModes, @Nullable boolean[] mask) {

		List<String[]> chosen = new ArrayList<>();
		chosen.add(planModel.getCurrentModes());

		// Chosen candidate from data
		PlanCandidate existing = gen.generatePredefined(planModel, chosen).get(0);

		throw new UnsupportedOperationException("Code removed due to API changes.");
	}

}
