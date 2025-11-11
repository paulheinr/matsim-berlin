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
	private static final String BERLIN_PUBLIC_SVN = "../../public-svn/matsim/scenarios/countries/de/berlin/berlin-v6.4/input";

//	private access only
	private CreateGartenfeldComplete() {
	}

	public static void main(String[] args) {

//		TODO: re-strucutre this class:
//		1) remove hard-coded usage of paths. mb change to MATSimAppCommand and define necessary paths as run params.
//			after thinking about 1): this is a makefile like script. In make, we also add the necessary files as "hard-code". so here it is also fine.
//		2) I am pretty sure that we should not have all the .xml / .gpkg files on github. That's what we have a svn structure for.

//		create gartenfeld population as a merge of berlin population + newly created gartenfeld population.
//		outputPopulation = the resulting output population
//		outputPopulation would be ready to go for simulating berlin 6.4 + gartenfeld, but simulation times
//		would be rather long. Hence, in the next step, we create a cutout with class CreateScenarioCutOut.
		String outputPopulationPath = "input/gartenfeld/v6.4/gartenfeld-v6.4.population-full-10pct.xml.gz";
		if (!Files.exists(Path.of(outputPopulationPath))) {
			new CreateGartenfeldPopulation().execute(
					"--output", outputPopulationPath,
				"--berlin-svn", BERLIN_PUBLIC_SVN
			);
		}

//		(they are currently defined there)
		String outputCutoutPopulationPath = "input/gartenfeld/v6.4/gartenfeld-v6.4.population-cutout-10pct.xml.gz";
		String outputCutoutNetworkPath = "input/gartenfeld/v6.4/gartenfeld-v6.4.network-cutout.xml.gz";
//		TODO: what is this network file??
//		fullNetwork for me would be sth like berlin + gartenfeld?!
//		und woher kommt dieses eigentlich? Bisher wird doch nur die population erstellt???
//		TODO: think about better name when above question is answered
		String fullNetworkPath = "input/v6.4/gartenfeld/gartenfeld-v6.4.network.xml.gz";
		String berlinNetworkPath = BERLIN_PUBLIC_SVN + "/berlin-v6.4-network-with-pt.xml.gz";

//		create "the" cutout for gartenfeld. I am currently (2025-10-08) not sure what that means exactly. TODO
//		cuts out all links out of the berlin network which are either in the defined shp or used by a route touching the shp.
//		cuts out all persons which have activities in the shp or cross the shp with their route. If it is not a network route
//		a network route is estimated by a shortest path algo.
//		TODO: the alogrithm does not take into account all plans of agents, only the selected ones. FIX THAT
//		TODO: the algorithm deletes non-selected plans. FIX THAT
//		cuts out all act facilities which are part of the above cutout population.
		new CreateScenarioCutOut().execute(
				"--network", berlinNetworkPath,
				"--population", outputPopulationPath,
//				TODO: ref SVN
				"--facilities", "input/v%s/berlin-v%s-facilities.xml.gz".formatted(OpenBerlinScenario.VERSION, OpenBerlinScenario.VERSION),
//				TODO: ref SVN locally
			"--events", "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v%s/output/berlin-v%s-10pct/berlin-v%s.output_events.xml.gz".formatted(OpenBerlinScenario.VERSION, OpenBerlinScenario.VERSION, OpenBerlinScenario.VERSION),
//				TODO: file is on shared-svn now
			"--shp", "input/gartenfeld/DNG_dilution_area.gpkg",
				"--input-crs", OpenBerlinScenario.CRS,
				"--network-modes", "car,bike",
				"--clean-modes", "truck,freight,ride",
//				TODO: is 1000 enough?
			"--buffer", "1000",
				"--keep-capacities",
				"--output-network", outputCutoutNetworkPath,
				"--output-population", outputCutoutPopulationPath,
//				TODO: rather define below paths above as vars like for network and population
				"--output-facilities", "input/gartenfeld/gartenfeld-v6.4.facilities-cutout.xml.gz",
				"--output-network-change-events", "input/gartenfeld/gartenfeld-v6.4.network-change-events.xml.gz"
		);

//		call ModifyNetwork class. The class removes links from the network based on a provided csv/txt file.
//		It also adds links to the network based on a provided shp file with line geometries.
		createNetwork(outputCutoutNetworkPath, outputCutoutNetworkPath, "input/gartenfeld/DNG_network.gpkg");
		createNetwork(berlinNetworkPath, fullNetworkPath, "input/gartenfeld/DNG_network.gpkg");

//		check the plans of agents for non-existing linkIds. Invalid linkIds will be removed.
		new PersonNetworkLinkCheck().execute(
				"--input", outputCutoutPopulationPath,
				"--network", outputCutoutNetworkPath,
				"--output", outputCutoutPopulationPath
		);

		new PersonNetworkLinkCheck().execute(
				"--input", outputPopulationPath,
				"--network", fullNetworkPath,
				"--output", outputPopulationPath
		);

//		sample input 10% sample to 1%.
		new DownSamplePopulation().execute(
				outputCutoutPopulationPath,
				"--sample-size", "0.1",
				"--samples", "0.01"
		);

		new DownSamplePopulation().execute(
				outputPopulationPath,
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
