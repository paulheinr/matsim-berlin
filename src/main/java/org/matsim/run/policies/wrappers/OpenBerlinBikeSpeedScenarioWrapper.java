package org.matsim.run.policies.wrappers;

import org.matsim.application.MATSimApplication;
import org.matsim.run.policies.OpenBerlinBikeSpeedScenario;

/**
 * Run the bike speed scenario for berlin.
 */
public final class OpenBerlinBikeSpeedScenarioWrapper {

	private OpenBerlinBikeSpeedScenarioWrapper() {}

	public static void main(String[] args) {
		MATSimApplication.run(OpenBerlinBikeSpeedScenario.class, args);
	}
}
