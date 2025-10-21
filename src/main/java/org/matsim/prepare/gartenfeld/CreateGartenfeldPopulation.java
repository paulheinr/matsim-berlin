package org.matsim.prepare.gartenfeld;

import org.matsim.application.MATSimAppCommand;
import org.matsim.application.prepare.population.*;
import org.matsim.prepare.population.CreateFixedPopulation;
import org.matsim.prepare.population.InitLocationChoice;
import org.matsim.prepare.population.RunActivitySampling;
import org.matsim.run.OpenBerlinScenario;
import picocli.CommandLine;

@CommandLine.Command(name = "create-gartenfeld-population", description = "Create the population for the Gartenfeld scenario.")
public class CreateGartenfeldPopulation implements MATSimAppCommand {

//	on the first view this class replicates the structure of a scenario creation makefile.

	private static final String GERMANY = "../shared-svn/projects/matsim-germany";
	private static final String SRV = "../shared-svn/projects/matsim-berlin/data/SrV";
	private static final String SRV_YEAR = "2018";

	@CommandLine.Option(names = "--output", description = "Path to output population", defaultValue = "input/gartenfeld/gartenfeld-population-10pct.xml.gz")
	private String output;

	public static void main(String[] args) {
		new CreateGartenfeldPopulation().execute(args);
	}

	@Override
	@SuppressWarnings("MultipleStringLiteralsExtended")
	public Integer call() throws Exception {

//		for values, see: https://depositonce.tu-berlin.de/items/a724cdc7-d6ef-4b9c-a85c-27930d226828 page 4
//		create gartenfeld population with person attributes and empty plans (except first home act).
//		The plans are basically "stay home plans" for now. This will change in the next step (activity sampling).
		new CreateFixedPopulation().execute(
			"--n", "7400",
			"--sample", "0.1",
			"--unemployed", "0.013",
			"--age-dist", "0.149", "0.203",
			"--facilities", "input/gartenfeld/DNG_residential.gpkg",
			"--prefix", "dng",
			"--output", output
		);

//		sample activity-leg chains from persons and activities tables which are based on SRV.
//		the tables are created via python code, see https://github.com/matsim-vsp/matsim-python-tools/blob/master/matsim/scenariogen/data/run_extract_activities.py
		new RunActivitySampling().execute(
			"--seed", "1",
			"--persons", SRV + "/" + SRV_YEAR + "/converted/table-persons.csv",
			"--activities", SRV + "/" + SRV_YEAR + "/converted/table-activities.csv",
			"--input", output,
			"--output", output
		);

//		create locations for activities based on shp file and work commuter statistics for berlin.
//		why only create 1 plan instead of 5 for each person? There is no reference data to optimize location choice against.
//		k=5 plans would mean to create 5 different location sets. Those would be optimized against counts/reference data.
		new InitLocationChoice().execute(
			"--input", output,
			"--output", output,
			"--k", "1",
			"--facilities", "input/v%s/berlin-v%s-facilities.xml.gz".formatted(OpenBerlinScenario.VERSION, OpenBerlinScenario.VERSION),
			"--network", "input/v%s/berlin-v%s-network.xml.gz".formatted(OpenBerlinScenario.VERSION, OpenBerlinScenario.VERSION),
			"--shp", GERMANY + "/vg5000/vg5000_ebenen_0101/VG5000_GEM.shp",
			"--commuter", GERMANY + "/regionalstatistik/commuter.csv",
			"--berlin-commuter", SRV + "/" + SRV_YEAR + "/converted/berlin-work-commuter.csv",
			"--commute-prob", "0.1",
			"--sample", "0.1"
		);

//		split activity types to type_duration for the scoring to take into account the typical duration
		new SplitActivityTypesDuration().execute(
			"--input", output,
			"--output", output,
			"--exclude", "commercial_start,commercial_end,freight_start,freight_end,service"
		);

//		set car availability for agents with age > 18 to always. set driving lincense to "yes" if not already.
		new SetCarAvailabilityByAge().execute(
			"--input", output,
			"--output", output
		);

//		this class does not only *check* violations of car availability, but also *corrects* them.
//		Correction means: if agent has no drivers license or carAvail=never search for car legs and replace with substitution mode, which defaults to ride.
//		But only if you provide --output. The class does not seem well-structured or -named to me.
		new CheckCarAvailability().execute(
			"--input", output,
			"--output", output
		);

//		change modes in subtours with chain based AND non-chain based by choosing mode for subtour randomly
		new FixSubtourModes().execute(
			"--input", output,
			"--output", output,
			"--coord-dist", "100"
		);

		// Merge with calibrated berlin plans into one single population
		new MergePopulations().execute(
			output, "input/v%s/berlin-v%s-10pct.plans.xml.gz".formatted(OpenBerlinScenario.VERSION, OpenBerlinScenario.VERSION),
			"--output", output
		);


		return 0;
	}
}
