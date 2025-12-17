package org.matsim.run.policies.wrappers;

import org.matsim.application.MATSimApplication;
import org.matsim.run.policies.OpenBerlinBetaMoneyScenario;

/**
 * Run the beta money scenario for berlin.
 */
public final class OpenBerlinBetaMoneyScenarioWrapper {

	private OpenBerlinBetaMoneyScenarioWrapper() {}

	public static void main(String[] args) {
		MATSimApplication.run(OpenBerlinBetaMoneyScenario.class, args);
	}
}
