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
	private static final String SHARED_SVN = "../../shared-svn/projects/Mobility2Grid/data/scenarioCreation";

//	private access only
	private CreateGartenfeldComplete() {
	}

	public static void main(String[] args) {
//		define output paths for berlin + gartenfeld "full" simulation input files
		String outputBerlinWithDNGFullPopulationPath = "input/gartenfeld/v6.4/berlin-with-DNG-full-v6.4.population-10pct.xml.gz";
		String outputBerlinWithDNGFullNetworkPath = "input/v6.4/gartenfeld/gartenfeld-v6.4.network.xml.gz";
		String berlinNetworkPath = BERLIN_PUBLIC_SVN + "/berlin-v6.4-network-with-pt.xml.gz";

//		create gartenfeld population as a merge of berlin population + newly created gartenfeld population.
//		outputPopulation = the resulting output population
//		outputPopulation would be ready to go for simulating berlin 6.4 + gartenfeld, but simulation times
//		would be rather long. Hence, in the next step, we create a cutout with class CreateScenarioCutOut.
		if (!Files.exists(Path.of(outputBerlinWithDNGFullPopulationPath))) {
			new CreateGartenfeldPopulation().execute(
					"--output", outputBerlinWithDNGFullPopulationPath,
				"--berlin-svn", BERLIN_PUBLIC_SVN,
				"--shared-svn", SHARED_SVN
			);
		}

//		define output paths for cutout simulation input files
		String outputBerlinWithDNGCutoutPopulationPath = "input/gartenfeld/v6.4/berlin-with-DNG-cutout-v6.4.population-10pct.xml.gz";
		String outputBerlinWithDNGCutoutNetworkPath = "input/gartenfeld/v6.4/berlin-with-DNG-cutout-v6.4.network.xml.gz";
		String outputBerlinWithDNGCutoutFacilitiesPath = "input/gartenfeld/v6.4/berlin-with-DNG-cutout-v6.4.facilities.xml.gz";
		String outputBerlinWithDNGCutoutNetworkChangeEventsPath = "input/gartenfeld/v6.4/berlin-with-DNG-cutout-v6.4.network-change-events.xml.gz";


//		cuts out all links out of the berlin network which are either in the defined shp or used by a route touching the shp.
//		cuts out all persons which have activities in the shp or cross the shp with their route. If it is not a network route
//		a network route is estimated by a shortest path algo.
//		TODO: the alogrithm does not take into account all plans of agents, only the selected ones. FIX THAT
//		TODO: the algorithm deletes non-selected plans. FIX THAT
//		TODO: sort out Aleksanders cutout branch in matsim-libs together with him
//		cuts out all act facilities which are part of the above cutout population.
		new CreateScenarioCutOut().execute(
				"--network", berlinNetworkPath,
				"--population", outputBerlinWithDNGFullPopulationPath,
				"--facilities", BERLIN_PUBLIC_SVN + "/berlin-v6.4-facilities.xml.gz",
//				TODO: test if this path works. if not: make BERLIN_PUBLIC_SVN point to one dir level up compared to now.
				"--events", BERLIN_PUBLIC_SVN + "/../output/berlin-v6.4.output_events.xml.gz",
				"--shp", SHARED_SVN + "/DNG_model_area.gpkg",
				"--input-crs", OpenBerlinScenario.CRS,
				"--network-modes", "car,bike",
				"--clean-modes", "truck,freight,ride",
//				after testing around in QGIS with different buffers 5k seems ok.
				"--buffer", "5000",
				"--keep-capacities",
				"--output-network", outputBerlinWithDNGCutoutNetworkPath,
				"--output-population", outputBerlinWithDNGCutoutPopulationPath,
				"--output-facilities", outputBerlinWithDNGCutoutFacilitiesPath,
				"--output-network-change-events", outputBerlinWithDNGCutoutNetworkChangeEventsPath
		);

//		call ModifyNetwork class. The class removes links from the network based on a provided csv/txt file.
//		It also adds links to the network based on a provided shp file with line geometries.
//		we use the cutout network from CreateScenarioCutout as input and output.
		new ModifyNetwork().execute(
			"--network", outputBerlinWithDNGCutoutNetworkPath,
			"--remove-links", "input/gartenfeld/DNG_LinksToDelete.txt",
			"--shp", "input/gartenfeld/DNG_network.gpkg",
			"--output", outputBerlinWithDNGCutoutNetworkPath
		);

		new ModifyNetwork().execute(
			"--network", berlinNetworkPath,
			"--remove-links", "input/gartenfeld/DNG_LinksToDelete.txt",
			"--shp", "input/gartenfeld/DNG_network.gpkg",
			"--output", outputBerlinWithDNGFullNetworkPath
		);

//		check the plans of agents for non-existing linkIds. Invalid linkIds will be removed.
//		this means removed from activities + if a non-existing link is part of a leg's route, the route is deleted.
		new PersonNetworkLinkCheck().execute(
				"--input", outputBerlinWithDNGCutoutPopulationPath,
				"--network", outputBerlinWithDNGCutoutNetworkPath,
				"--output", outputBerlinWithDNGCutoutPopulationPath
		);

		new PersonNetworkLinkCheck().execute(
				"--input", outputBerlinWithDNGFullPopulationPath,
				"--network", outputBerlinWithDNGFullNetworkPath,
				"--output", outputBerlinWithDNGFullPopulationPath
		);

//		sample input 10% sample to 1%.
		new DownSamplePopulation().execute(
				outputBerlinWithDNGCutoutPopulationPath,
				"--sample-size", "0.1",
				"--samples", "0.01"
		);

		new DownSamplePopulation().execute(
				outputBerlinWithDNGFullPopulationPath,
				"--sample-size", "0.1",
				"--samples", "0.01"
		);

	}
}
