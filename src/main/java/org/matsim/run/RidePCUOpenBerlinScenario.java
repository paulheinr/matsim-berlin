package org.matsim.run;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.application.MATSimApplication;
import org.matsim.core.config.Config;
import org.matsim.vehicles.VehicleType;

import java.util.HashSet;

public class RidePCUOpenBerlinScenario extends OpenBerlinScenario {

	public static void main(String[] args) {
		MATSimApplication.execute(RidePCUOpenBerlinScenario.class, args);
	}

	@Override
	protected Config prepareConfig(Config config) {
		super.prepareConfig(config);

		HashSet<String> modes = new HashSet<>();
		for (String mainMode : config.qsim().getMainModes()) {
			modes.add(mainMode);
		}
		modes.add(TransportMode.ride);
		config.qsim().setMainModes(modes);

		return config;
	}

	@Override
	protected void prepareScenario(Scenario scenario) {
		super.prepareScenario(scenario);

		VehicleType ride = scenario.getVehicles().getVehicleTypes().get(Id.create("ride", VehicleType.class));
		ride.setPcuEquivalents(0.0);
	}
}
