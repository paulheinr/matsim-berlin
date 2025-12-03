package org.matsim.run.gartenfeld;

import org.matsim.application.MATSimApplication;

/**
 * Run class for the Gartenfeld drt scenario.
 */
public final class RunGartenfeldDrtScenario {

	private RunGartenfeldDrtScenario() {
	}

	public static void main(String[] args) {
		MATSimApplication.runWithDefaults(GartenfeldDrtScenario.class, args);
	}
}
