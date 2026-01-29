package org.matsim.run.policies.wrappers;

import org.matsim.application.MATSimApplication;
import org.matsim.run.policies.OpenBerlinSharingScenario;

/**
 * Run the eScooter sharing scenario for berlin.
 */
public final class OpenBerlinSharingScenarioWrapper {

	private OpenBerlinSharingScenarioWrapper() {}

	public static void main(String[] args) {
		MATSimApplication.run(OpenBerlinSharingScenario.class, args);
	}
}
