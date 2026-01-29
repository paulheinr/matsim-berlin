package org.matsim.run.policies;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.shared_mobility.run.SharingConfigGroup;
import org.matsim.contrib.shared_mobility.run.SharingModule;
import org.matsim.contrib.shared_mobility.run.SharingServiceConfigGroup;
import org.matsim.contrib.shared_mobility.service.SharingUtils;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.RoutingConfigGroup;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.population.PersonUtils;
import org.matsim.run.OpenBerlinDrtScenario;
import org.matsim.run.OpenBerlinScenario;
import picocli.CommandLine;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Berlin scenario including the possibility to change beta money and thus the perception of monetary prices.
 * E.g. for pt or car.
 * All necessary configs will be made in this class.
 */
public class OpenBerlinSharingScenario extends OpenBerlinScenario {
	Logger log = LogManager.getLogger(OpenBerlinSharingScenario.class);

	@CommandLine.Option(names = "--sharing-stations", description = "File with coordinates of sharing stations.", required = true)
	private String sharingStationsCsv;
	@CommandLine.Option(names = "--x-coord-column", description = "Name of column with x coords in sharingStationsCsv.", defaultValue = "xcoord")
	private String xCoordColumnName;
	@CommandLine.Option(names = "--y-coord-column", description = "Name of column with y coords in sharingStationsCsv.", defaultValue = "ycoord")
	private String yCoordColumnName;
	@CommandLine.Option(names = "--intermodal-e-scooter", defaultValue = "E_SCOOTER_REGULAR_AND_INTERMODAL", description = "INTERMODAL_E_SCOOTER_ONLY: eScooter can only be used for access/egress to PT. E_SCOOTER_REGULAR_AND_INTERMODAL: eScooter used for intermodal access/egress to PT and as separate mode.")
	private EScooterIntermodalityHandling intermodal;

	private final String eScooter = "eScooter";
	private final String stopFilter = "eScooterStopFilter";
	private final String stopFilterValue = "station_S/U/RE/RB_eScooter";
	private final String berlinShpString = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v6.4/input/shp/Berlin_25832.shp";

	@Nullable
	@Override
	public Config prepareConfig(Config config) {
		//		apply all config changes from base scenario class
		super.prepareConfig(config);

//		TODO: transform csv into shraibng stations xml. look at example xml file. Can this be done in code?
//		TODO: how many scooters at each station?

//		this should do the same as the follwoing
//		SharingConfigGroup sharingConfig = new SharingConfigGroup();
//		config.addModule(sharingConfig);
//		TODO: test and compare
		SharingConfigGroup sharingConfigGroup = ConfigUtils.addOrGetModule(config, SharingConfigGroup.class);

		SharingServiceConfigGroup serviceConfig = new SharingServiceConfigGroup();
		serviceConfig.setId(eScooter);
		serviceConfig.setServiceScheme(SharingServiceConfigGroup.ServiceScheme.StationBased);
		serviceConfig.setMaximumAccessEgressDistance(2000);
//		TODO: figure out whether to pre-process the csv file or to transfrom csv to xml in this class and save stations.xml
		serviceConfig.setServiceInputFile("");
		serviceConfig.setServiceAreaShapeFile(berlinShpString);
		serviceConfig.setMode(eScooter);
//		TODO: figure out pricing. Rather do it via daily mon. constatn like pt or here?
		serviceConfig.setBaseFare(0.75);
		serviceConfig.setTimeFare(0.24);
		serviceConfig.setDistanceFare(0.0);

		sharingConfigGroup.addService(serviceConfig);

		// Register the shared mode as a teleportation mode
		RoutingConfigGroup.TeleportedModeParams eScooterParams = new RoutingConfigGroup.TeleportedModeParams(eScooter);
//		2.98 is the speed of veh type bike in the veh type file
		eScooterParams.setTeleportedModeSpeed(2.98);
		eScooterParams.setBeelineDistanceFactor(1.3);
		config.routing().addTeleportedModeParams(eScooterParams);


		if (intermodal == EScooterIntermodalityHandling.E_SCOOTER_REGULAR_AND_INTERMODAL) {
			// Add the shared mode to mode choice
			List<String> modes = new ArrayList<>(Arrays.asList(config.subtourModeChoice().getModes()));
			modes.add(eScooter);
			config.subtourModeChoice().setModes(modes.toArray(new String[0]));
		}

		// Add activity types used in shared mobility
		for (String act : List.of(SharingUtils.PICKUP_ACTIVITY, SharingUtils.DROPOFF_ACTIVITY, SharingUtils.BOOKING_ACTIVITY)) {
			ScoringConfigGroup.ActivityParams params = new ScoringConfigGroup.ActivityParams(act);
			params.setScoringThisActivityAtAll(false);
			config.scoring().addActivityParams(params);
		}

		// Scoring params of eScooter mode based on bike scoring params
		ScoringConfigGroup.ModeParams bikeParams = config.scoring().getModes().get(TransportMode.bike);

		ScoringConfigGroup.ModeParams modeParams = new ScoringConfigGroup.ModeParams(eScooter);
		modeParams.setConstant(bikeParams.getConstant());
		modeParams.setMarginalUtilityOfDistance(bikeParams.getMarginalUtilityOfDistance());
		modeParams.setMarginalUtilityOfTraveling(bikeParams.getMarginalUtilityOfTraveling());
		modeParams.setDailyUtilityConstant(bikeParams.getDailyUtilityConstant());

//		TODO: decide on how and which fare to implement for eScooter
		modeParams.setMonetaryDistanceRate(bikeParams.getMonetaryDistanceRate());
		modeParams.setDailyMonetaryConstant(bikeParams.getDailyMonetaryConstant());
		config.scoring().addModeParams(modeParams);

//		configure intermodal access/egress to pt
		SwissRailRaptorConfigGroup raptorConfigGroup = ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class);
		SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet intermodalParams = new SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet();
		intermodalParams.setMode(eScooter);
		intermodalParams.setSearchExtensionRadius(1000.);
//		in this DLR report (p. 7), multiple sources about avg. eScooter trip length are cited https://elib.dlr.de/141837/1/ArbeitsberichteVF_Nr4_2021.pdf
//		avg. eScooter trip length seems to ~2km
//		thus, we set initialSearchRadius to 2km and maxRadius to 2 * initialRadius. -sm0126
		intermodalParams.setInitialSearchRadius(2000.);
		intermodalParams.setMaxRadius(4000.);
		intermodalParams.setStopFilterAttribute(stopFilter);
//		we assume that -- similar to DRT -- access/egress to PT is not done to bus/tram
		intermodalParams.setStopFilterValue(stopFilterValue);

		raptorConfigGroup.addIntermodalAccessEgress(intermodalParams);

		return config;
	}

	@Override
	public void prepareScenario(Scenario scenario) {
		//		apply all scenario changes from base scenario class
		super.prepareScenario(scenario);

		for (Person person : scenario.getPopulation().getPersons().values()) {
			if (PersonUtils.getModeConstants(person) != null && PersonUtils.getModeConstants(person).containsKey(TransportMode.bike)) {
//				assume that preference for bike is similar for eScooter

//				TODO: this will probably not work. TEST THIS! if not: we need to get the map, add it to a mutable map and then setModeConstants..
				PersonUtils.getModeConstants(person).put(eScooter, PersonUtils.getModeConstants(person).get(TransportMode.bike));
			}

		}

//		tag intermodal eScooter-pt-stations
		OpenBerlinDrtScenario.tagTransitStopsInServiceArea(scenario.getTransitSchedule(),
			stopFilter, stopFilterValue,
			berlinShpString,
			"stopFilter", "station_S/U/RE/RB",
			// some S+U stations are located slightly outside the shp File, e.g. U7 Neukoelln, U8
			// Hermannstr., so allow buffer around the shape.
			// This does not mean that a drt vehicle can pick the passenger up outside the service area,
			// rather the passenger has to walk the last few meters from the drt drop off to the station.
//			we now use whole of berlin instead of Hundekopf, but will keep using the buffer, assuming that the same issues might occur for other stations -sm0126
			200.0);
	}

	@Override
	public void prepareControler(Controler controler) {
		//		apply all controller changes from base scenario class
		super.prepareControler(controler);

		controler.addOverridingModule(new SharingModule());
		controler.configureQSimComponents(SharingUtils.configureQSim(ConfigUtils.addOrGetModule(controler.getConfig(), SharingConfigGroup.class)));

//		TODO: test if the sim works without adding a TeleportationRoutingModule for eScooter. If not, add it here.
	}

	/**
	 * Helper enum to enable/disable functionalities.
	 */
	private enum EScooterIntermodalityHandling {INTERMODAL_E_SCOOTER_ONLY, E_SCOOTER_REGULAR_AND_INTERMODAL}
}
