package org.matsim.prepare.gartenfeld;

import org.matsim.application.MATSimAppCommand;
import org.matsim.application.prepare.population.*;
import org.matsim.prepare.population.CreateFixedPopulation;
import org.matsim.prepare.population.InitLocationChoice;
import org.matsim.prepare.population.RunActivitySampling;
import picocli.CommandLine;

@CommandLine.Command(name = "create-gartenfeld-population", description = "Create the population for the Gartenfeld scenario.")
public class CreateGartenfeldPopulation implements MATSimAppCommand {

//	on the first view this class replicates the structure of a scenario creation makefile.
	private static final String GERMANY = "../../shared-svn/projects/matsim-germany";
	private static final String SRV_BERLIN = "../../shared-svn/projects/matsim-berlin/data/SrV";
	private static final String SRV_YEAR = "2018";

	@CommandLine.Option(names = "--berlin-svn", description = "Path to local matsim-berlin public-svn directory." +
		"Has to point to the input dir.", required = true)
	private String berlinPublicSvn;
	@CommandLine.Option(names = "--shared-svn", description = "Path to local M2G data/scenarioCreation dir." +
		"Dir contains data to create DNG.", required = true)
	private String sharedSvn;
	@CommandLine.Option(names = "--output", description = "Path to output population", defaultValue = "input/gartenfeld/gartenfeld-population-10pct.xml.gz")
	private String output;

	public static void main(String[] args) {
		new CreateGartenfeldPopulation().execute(args);
	}

	@Override
	@SuppressWarnings("MultipleStringLiteralsExtended")
	public Integer call() throws Exception {
//		we might want to have a look at plans of intermediate steps, hence we need to cut the output string
		String outputBaseName = output.split("\\.xml", 1)[0];
		String outputFileType = output.split("\\.xml", 1)[1];
		String outputFixed = outputBaseName + "_fixed" + outputFileType;

//		for explanation of values, see: https://depositonce.tu-berlin.de/items/a724cdc7-d6ef-4b9c-a85c-27930d226828 page 4
//		create gartenfeld population with person attributes and semi-empty plans (plans have first home act).
//		The plans are basically "stay home plans" for now. This will change in the next step (activity sampling).
		new CreateFixedPopulation().execute(
			"--n", "7400",
			"--sample", "0.1",
			"--unemployed", "0.013",
			"--age-dist", "0.149", "0.203",
			"--facilities", sharedSvn + "/DNG_residential.gpkg",
			"--prefix", "dng",
			"--output", outputFixed
		);

//		sample activity-leg chains from persons and activities tables which are based on SRV.
//		the tables are created via python code, see https://github.com/matsim-vsp/matsim-python-tools/blob/master/matsim/scenariogen/data/run_extract_activities.py
		String outputSampled = outputBaseName + "_sampled" + outputFileType;
		new RunActivitySampling().execute(
			"--seed", "1",
			"--persons", SRV_BERLIN + "/" + SRV_YEAR + "/converted/table-persons.csv",
			"--activities", SRV_BERLIN + "/" + SRV_YEAR + "/converted/table-activities.csv",
			"--input", outputFixed,
			"--output", outputSampled
		);

//		create locations for activities based on shp file and work commuter statistics for berlin.
//		why only create 1 plan instead of 5 for each person? There is no reference data to optimize location choice against.
//		k=5 plans would mean to create 5 different location sets. Those would be optimized against counts/reference data.
		String outputLocChoice = outputBaseName + "_locChoice" + outputFileType;
		new InitLocationChoice().execute(
			"--input", outputSampled,
			"--output", outputLocChoice,
			"--k", "1",
//			I hard-coded the versions in the following to matsim-berlin 6.4.
			"--facilities", berlinPublicSvn + "/berlin-v6.4-facilities.xml.gz",
			"--network", berlinPublicSvn + "/berlin-v6.4-network.xml.gz",
			"--shp", GERMANY + "/vg5000/vg5000_ebenen_0101/VG5000_GEM.shp",
			"--commuter", GERMANY + "/regionalstatistik/commuter.csv",
			"--berlin-commuter", SRV_BERLIN + "/" + SRV_YEAR + "/converted/berlin-work-commuter.csv",
			"--commute-prob", "0.1",
			"--sample", "0.1"
		);

//		split activity types to type_duration for the scoring to take into account the typical duration
		String outputSplitActTypes = outputBaseName + "_splitActTypes" + outputFileType;
		new SplitActivityTypesDuration().execute(
			"--input", outputLocChoice,
			"--output", outputSplitActTypes,
			"--exclude", "commercial_start,commercial_end,freight_start,freight_end,service"
		);

//		set car availability for agents with age > 18 to always. set driving lincense to "yes" if not already.
		String outputCarAvail = outputBaseName + "_carAvail" + outputFileType;
		new SetCarAvailabilityByAge().execute(
			"--input", outputSplitActTypes,
			"--output", outputCarAvail
		);

//		this class does not only *check* violations of car availability, but also *corrects* them.
//		Correction means: if agent has no driver's license or carAvail=never search for car legs and replace with substitution mode, which defaults to ride.
//		But only if you provide --output. The class does not seem well-structured or -named to me.
		String outputCarAvailChecked = outputBaseName + "_carAvailChecked" + outputFileType;
		new CheckCarAvailability().execute(
			"--input", outputCarAvail,
			"--output", outputCarAvailChecked
		);

//		change modes in subtours with chain based AND non-chain based by choosing mode for subtour randomly
		String outputFixedSubtours = outputBaseName + "_fixedSubtours" + outputFileType;
		new FixSubtourModes().execute(
			"--input", outputCarAvailChecked,
			"--output", outputFixedSubtours,
			"--coord-dist", "100"
		);

		// Merge with calibrated berlin plans into one single population
		new MergePopulations().execute(
//			the plans file is the *full* -- with 5 plans per agent -- calibrated berlin v6.4 plans file.
			outputFixedSubtours, berlinPublicSvn + "/berlin-v6.4-10pct.plans.xml.gz",
			"--output", output
		);

		return 0;
	}
}
