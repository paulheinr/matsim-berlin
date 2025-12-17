package org.matsim.run.policies.wrappers;

import org.matsim.application.MATSimApplication;
import org.matsim.run.policies.OpenBerlinRoadCapacitiesScenario;

/**
 * Run the road capacities scenario for berlin.
 */
public final class OpenBerlinRoadCapacitiesScenarioWrapper {

	private OpenBerlinRoadCapacitiesScenarioWrapper() {}

	public static void main(String[] args) {
		MATSimApplication.run(OpenBerlinRoadCapacitiesScenario.class, args);
	}
}
