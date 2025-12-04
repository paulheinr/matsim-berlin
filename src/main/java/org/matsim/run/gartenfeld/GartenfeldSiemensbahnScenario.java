package org.matsim.run.gartenfeld;


import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.controler.Controler;
import org.matsim.run.prepare.AddSiemensbahnToScenario;

/**
 * Scenario class for Gartenfeld Siemensbahn scenario. Siemensbahn is modelled until Gartenfeld.
 * <p>
 * Like its parent class GartenfeldScenario, this scenario has its own input files, which extend the OpenBerlin scenario files with inhabitants and road infrastructure specific to Gartenfeld.
 * See {@link org.matsim.prepare.gartenfeld.CreateGartenfeldComplete} for the creation of these input files.
 */
public class GartenfeldSiemensbahnScenario extends GartenfeldScenario {

	@Override
	protected Config prepareConfig(Config config) {

//		apply all changes from base run class
//		this also includes changes from berlin v6.4
		super.prepareConfig(config);

		return config;
	}

	@Override
	public void prepareScenario(Scenario scenario) {
//		apply all scenario changes from base scenario class
		super.prepareScenario(scenario);

//		add siemensbahn to scenario. script is based on work of GL and Eduardo Lima
		new AddSiemensbahnToScenario().addSiemensbahn(scenario);
	}

	@Override
	public void prepareControler(Controler controler) {
//		apply all controller changes from base scenario class
		super.prepareControler(controler);
	}

}
