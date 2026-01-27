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
 * Berlin scenario including the possibility to add a provided percentage of working berlin residents to the "subpopulation" of stay home agents.
 * Stay home agents are assumed to be home office workers.
 * All necessary configs will be made in this class.
 */
public class OpenBerlinHomeOfficeScenario extends OpenBerlinScenario {
	Logger log = LogManager.getLogger(OpenBerlinHomeOfficeScenario.class);

	@CommandLine.Option(names = "--home-office-pct", description = "", defaultValue = "0.1")
	private double additionalHomeOfficePct;

	@Nullable
	@Override
	public Config prepareConfig(Config config) {
		//		apply all config changes from base scenario class
		super.prepareConfig(config);

		return config;
	}

	@Override
	public void prepareScenario(Scenario scenario) {
		//		apply all scenario changes from base scenario class
		super.prepareScenario(scenario);

		MobilityToGridScenariosUtils.addHomeOfficeWorkers(scenario, additionalHomeOfficePct);
	}

	@Override
	public void prepareControler(Controler controler) {
		//		apply all controller changes from base scenario class
		super.prepareControler(controler);
	}
}
