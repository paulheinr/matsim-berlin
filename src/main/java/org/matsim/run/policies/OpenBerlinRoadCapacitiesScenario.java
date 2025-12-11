package org.matsim.run.policies;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.controler.Controler;
import org.matsim.run.OpenBerlinScenario;
import picocli.CommandLine;

import javax.annotation.Nullable;

/**
 * Berlin scenario including the possibility to change road capacities and
 * thus the possibility to model increase/decrease of population size.
 * All necessary configs will be made in this class.
 */
public class OpenBerlinRoadCapacitiesScenario extends OpenBerlinScenario {
	Logger log = LogManager.getLogger(OpenBerlinRoadCapacitiesScenario.class);

	@Nullable
	@Override
	public Config prepareConfig(Config config) {
		//		apply all config changes from base scenario class
		super.prepareConfig(config);
	}

	@Override
	public void prepareScenario(Scenario scenario) {
		//		apply all scenario changes from base scenario class
		super.prepareScenario(scenario);

//		TODO: set road capacities.
//		TODO: think about which road types should be affected. all of them?
	}

	@Override
	public void prepareControler(Controler controler) {
		//		apply all controller changes from base scenario class
		super.prepareControler(controler);
	}
}
