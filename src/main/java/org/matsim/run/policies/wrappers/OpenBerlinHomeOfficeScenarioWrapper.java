package org.matsim.run.policies.wrappers;

import org.matsim.application.MATSimApplication;
import org.matsim.run.policies.OpenBerlinHomeOfficeScenario;

/**
 * Run the home office scenario for berlin.
 */
public final class OpenBerlinHomeOfficeScenarioWrapper {

	private OpenBerlinHomeOfficeScenarioWrapper() {}

	public static void main(String[] args) {
		MATSimApplication.run(OpenBerlinHomeOfficeScenario.class, args);
	}
}
