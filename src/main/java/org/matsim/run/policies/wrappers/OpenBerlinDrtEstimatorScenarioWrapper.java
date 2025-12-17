package org.matsim.run.policies.wrappers;

import org.matsim.application.MATSimApplication;
import org.matsim.run.policies.OpenBerlinDrtEstimatorScenario;

/**
 * Run the drt (estimator) scenario for berlin.
 */
public final class OpenBerlinDrtEstimatorScenarioWrapper {

	private OpenBerlinDrtEstimatorScenarioWrapper() {

	}

	public static void main(String[] args) {
		MATSimApplication.run(OpenBerlinDrtEstimatorScenario.class, args);
	}
}
