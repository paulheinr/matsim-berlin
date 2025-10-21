package org.matsim.run.gartenfeld;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.MATSimApplication;
import org.matsim.application.options.ShpOptions;
import org.matsim.application.prepare.population.PersonNetworkLinkCheck;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.algorithms.ParallelPersonAlgorithmUtils;
import org.matsim.core.router.MultimodalLinkChooser;
import org.matsim.core.router.MultimodalLinkChooserDefaultImpl;
import org.matsim.run.OpenBerlinScenario;
import picocli.CommandLine;

import java.util.HashSet;
import java.util.Set;

/**
 * Scenario class for Gartenfeld.
 * <p>
 * This scenario has its own input files, which extend the OpenBerlin scenario files with inhabitants and road infrastructure specific to Gartenfeld.
 * See {@link org.matsim.prepare.gartenfeld.CreateGartenfeldComplete} for the creation of these input files.
 */
public class GartenfeldScenario extends OpenBerlinScenario {

	@CommandLine.Option(names = "--gartenfeld-config", description = "Path to configuration for Gartenfeld.", defaultValue = "input/gartenfeld/gartenfeld-cutout.config.xml")
	private String gartenFeldConfig;

	@CommandLine.Option(names = "--gartenfeld-shp", description = "Path to configuration for Gartenfeld.", defaultValue = "input/gartenfeld/DNG_area.gpkg")
	private String gartenFeldArea;

	@CommandLine.Option(names = "--parking-garages", description = "Enable parking garages. Possible values CAR_PARKING_ALLOWED_ON_ALL_LINKS or CAR_PARKING_IN_CENTRAL_GARAGE",
		defaultValue = "CAR_PARKING_ALLOWED_ON_ALL_LINKS")
	private GarageType garageType = GarageType.CAR_PARKING_ALLOWED_ON_ALL_LINKS;

		public static void main(String[] args) {
		MATSimApplication.run(GartenfeldScenario.class, args);
	}

	@Override
	protected Config prepareConfig(Config config) {

		// Load the Gartenfeld specific part into the standard Berlin config
		ConfigUtils.loadConfig(config, gartenFeldConfig);

		// needs to be called after load.config
//		apply all changes of super class/method: add simwrapper cfg, add scaling factor where applicable,
//		set correct ride mode params (dependent on car), add replanning strategies for each subpop,
//		add cfg group for bicycle module, configure emissions
		super.prepareConfig(config);

		return config;
	}

	@Override
	protected void prepareScenario(Scenario scenario) {

//		apply all changes of super class/method:
//		add hbefa link attributes
		super.prepareScenario(scenario);

		Network network = scenario.getNetwork();
		Set<String> removeModes = Set.of(TransportMode.car, TransportMode.truck, "freight", TransportMode.ride);

//		if we want to run the scenario with a central DNG parking garage, we have to remove car etc. from all DNG links
		if (garageType != GarageType.CAR_PARKING_ALLOWED_ON_ALL_LINKS) {

			for (Link link : network.getLinks().values()) {
				// Make all links car free
				String linkId = link.getId().toString();

//				if gartenfeld link, do removal of allowed modes
				if (linkId.startsWith("network-DNG")) {

					// First garage, a garage in any cases
//					The following 2 links are the central parking garage
					if (linkId.equals(GartenfeldLinkChooser.accessLink.toString()) || linkId.equals(GartenfeldLinkChooser.egressLink.toString()))
						continue;

					Set<String> allowedModes = new HashSet<>(link.getAllowedModes());
					allowedModes.removeAll(removeModes);
					link.setAllowedModes(allowedModes);
				}
			}
			NetworkUtils.cleanNetwork(network, removeModes);

			// Clean link ids that are not valid anymore
			ParallelPersonAlgorithmUtils.run(
					scenario.getPopulation(),
					Runtime.getRuntime().availableProcessors(),
					PersonNetworkLinkCheck.createPersonAlgorithm(network)
			);
		}
	}

	@Override
	protected void prepareControler(Controler controler) {
//		apply all changes of super class/method:
//		add modules for simwrapper, ttbinding, qsimtiming, matsim-berlin-specific advanced scoring
		super.prepareControler(controler);

		// Only with the car free area, the multimodal link chooser is needed
		if (garageType == GarageType.CAR_PARKING_IN_CENTRAL_GARAGE)
			controler.addOverridingModule(new AbstractModule() {
				@Override
				public void install() {
					bind( MultimodalLinkChooserDefaultImpl.class );
					bind(MultimodalLinkChooser.class).toInstance(new GartenfeldLinkChooser(ShpOptions.ofLayer(gartenFeldArea, null)));
				}
			});
	}

	/**
	 * Enum for the different garage types and implicitly the car free areas.
	 */
	public enum GarageType {
		/**
		 * No garage, cars are allowed on all links.
		 */
		CAR_PARKING_ALLOWED_ON_ALL_LINKS,
		/**
		 * One garage, cars are only allowed on the garage link at the entrance of the area.
		 */
		CAR_PARKING_IN_CENTRAL_GARAGE
	}
}
