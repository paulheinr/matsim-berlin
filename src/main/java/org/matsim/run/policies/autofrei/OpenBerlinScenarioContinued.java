package org.matsim.run.policies.autofrei;

import org.matsim.application.MATSimApplication;
import org.matsim.core.config.Config;
import org.matsim.run.OpenBerlinScenario;

public class OpenBerlinScenarioContinued extends OpenBerlinScenario {
	public static void main(String[] args) {
		MATSimApplication.run(OpenBerlinScenarioContinued.class, args);
	}

	@Override
	protected Config prepareConfig(Config config) {
		Config newConfig = super.prepareConfig(config);

		// disable subtourModeChoice based on coordinate distance. This is in particular important to enable mass conservation at S-Bahn-Ring boundary.
		newConfig.subtourModeChoice().setCoordDistance(0);

		return config;
	}
}
