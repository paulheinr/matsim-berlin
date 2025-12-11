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
 * Berlin scenario including the possibility to change the speed of bicycles.
 * All necessary configs will be made in this class.
 */
public class OpenBerlinBikeSpeedScenario extends OpenBerlinScenario {
	Logger log = LogManager.getLogger(OpenBerlinBikeSpeedScenario.class);

	@Nullable
	@Override
	public Config prepareConfig(Config config) {
		//		apply all config changes from base scenario class
		super.prepareConfig(config);

//		TODO: is it enough to change teleportedModeSpeed of bike?

		return config;
	}

	@Override
	public void prepareScenario(Scenario scenario) {
		//		apply all scenario changes from base scenario class
		super.prepareScenario(scenario);
//		TODO: do we need to change the speed of veh type bike? Or is this value not used when teleported?!
	}

	@Override
	public void prepareControler(Controler controler) {
		//		apply all controller changes from base scenario class
		super.prepareControler(controler);
	}
}
