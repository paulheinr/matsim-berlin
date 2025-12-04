package org.matsim.run.gartenfeld;

import org.matsim.application.MATSimApplication;

/**
 * Run class for the Gartenfeld Siemensbahn scenario. Siemensbahn is modelled until Gartenfeld.
 */
public final class RunGartenfeldSiemensbahnScenario {

	private RunGartenfeldSiemensbahnScenario() {
	}

	public static void main(String[] args) {
		MATSimApplication.runWithDefaults(GartenfeldSiemensbahnScenario.class, args);
	}
}
