package org.matsim.prepare.gartenfeld;


import org.matsim.application.prepare.population.DownSamplePopulation;
import org.matsim.application.prepare.population.PersonNetworkLinkCheck;
import org.matsim.application.prepare.scenario.CreateScenarioCutOut;
import org.matsim.prepare.network.ModifyNetwork;
import org.matsim.run.OpenBerlinScenario;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility class to create all necessary input files for the Gartenfeld scenario.
 */
public final class CreateGartenfeldComplete {

//	private access only
	private CreateGartenfeldComplete() {
	}

	public static void main(String[] args) {

//		TODO: re-strucutre this class:
//		1) remove hard-coded usage of paths. mb change to MATSimAppCommand and define necessary paths as run params.
//		2) I am pretty sure that we should not have all the .xml / .gpkg files on github. That's what we have a svn structure for.

//		create gartenfeld population based on provided full population.
//		I have not yet fully understood how that is done. TODO
		String fullPopulation = "input/gartenfeld/gartenfeld-v6.4.population-full-10pct.xml.gz";
		if (!Files.exists(Path.of(fullPopulation))) {
			new CreateGartenfeldPopulation().execute(
					"--output", fullPopulation
			);
		}

		String population = "input/gartenfeld/gartenfeld-v6.4.population-cutout-10pct.xml.gz";
		String network = "input/gartenfeld/gartenfeld-v6.4.network-cutout.xml.gz";
		String fullNetwork = "input/gartenfeld/gartenfeld-v6.4.network.xml.gz";
		String berlinNetwork = "input/v%s/berlin-v%s-network-with-pt.xml.gz".formatted(OpenBerlinScenario.VERSION, OpenBerlinScenario.VERSION);

//		create "the" cutout for gartenfeld. I am currently (2025-10-08) not sure what that means exactly. TODO
		new CreateScenarioCutOut().execute(
				"--network", berlinNetwork,
				"--population", fullPopulation,
				"--facilities", "input/v%s/berlin-v%s-facilities.xml.gz".formatted(OpenBerlinScenario.VERSION, OpenBerlinScenario.VERSION),
				"--events", "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v%s/output/berlin-v%s-10pct/berlin-v%s.output_events.xml.gz".formatted(OpenBerlinScenario.VERSION, OpenBerlinScenario.VERSION, OpenBerlinScenario.VERSION),
				"--shp", "input/gartenfeld/DNG_dilution_area.gpkg",
				"--input-crs", OpenBerlinScenario.CRS,
				"--network-modes", "car,bike",
				"--clean-modes", "truck,freight,ride",
				"--buffer", "1000",
				"--keep-capacities",
				"--output-network", network,
				"--output-population", population,
				"--output-facilities", "input/gartenfeld/gartenfeld-v6.4.facilities-cutout.xml.gz",
				"--output-network-change-events", "input/gartenfeld/gartenfeld-v6.4.network-change-events.xml.gz"
		);

//		call ModifyNetwork class. The class removes links from the network based on a provided csv/txt file.
//		It also adds links to the network based on a provided shp file with line geometries.
		createNetwork(network, network, "input/gartenfeld/DNG_network.gpkg");
		createNetwork(berlinNetwork, fullNetwork, "input/gartenfeld/DNG_network.gpkg");

//		check the plans of agents for non-existing linkIds. Invalid linkIds will be removed.
		new PersonNetworkLinkCheck().execute(
				"--input", population,
				"--network", network,
				"--output", population
		);

		new PersonNetworkLinkCheck().execute(
				"--input", fullPopulation,
				"--network", fullNetwork,
				"--output", fullPopulation
		);

//		sample input 10% sample to 1%.
		new DownSamplePopulation().execute(
				population,
				"--sample-size", "0.1",
				"--samples", "0.01"
		);

		new DownSamplePopulation().execute(
				fullPopulation,
				"--sample-size", "0.1",
				"--samples", "0.01"
		);

	}

	private static void createNetwork(String network, String outputNetwork, String shp) {

		new ModifyNetwork().execute(
				"--network", network,
				"--remove-links", "input/gartenfeld/DNG_LinksToDelete.txt",
				"--shp", shp,
				"--output", outputNetwork
		);

	}
}
