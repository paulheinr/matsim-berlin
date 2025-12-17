package org.matsim.run.policies.wrappers;

import org.matsim.application.MATSimApplication;
import org.matsim.run.policies.OpenBerlinRoadSpeedScenario;

/**
 * Run the road speed scenario for berlin.
 */
public final class OpenBerlinRoadSpeedScenarioWrapper {

	private OpenBerlinRoadSpeedScenarioWrapper() {}

	public static void main(String[] args) {
		MATSimApplication.run(OpenBerlinRoadSpeedScenario.class, args);
	}
}
