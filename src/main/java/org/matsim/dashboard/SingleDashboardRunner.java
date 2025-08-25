package org.matsim.dashboard;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.MATSimAppCommand;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.simwrapper.SimWrapper;
import org.matsim.simwrapper.dashboard.TrafficCountsDashboard;
import picocli.CommandLine;

import java.nio.file.Path;


@CommandLine.Command(
	name = "simwrapper",
	description = "Run additional analysis and create SimWrapper dashboard for existing run output."
)
public class SingleDashboardRunner implements MATSimAppCommand {


	public static void main(String[] args) {
		new SingleDashboardRunner().execute(args);
	}


	@Override
	public Integer call() throws Exception {

		Config config = ConfigUtils.createConfig();

		ScenarioUtils.createScenario(config);
		//Network defaultNetwork = NetworkUtils.readNetwork("/Users/gregorr/Documents/work/stuff/CRBAM/withContrib/bikeWithContribMATSim.output_network.xml.gz");


		//Network networkWithPerceviedSafetyScores = NetworkUtils.readNetwork("/Users/gregorr/Documents/work/stuff/CRBAM/withContrib/bikeWithContribMATSim.output_network_withPerceivedSafetyScores.xml.gz");

		/*
		for (Link link : defaultNetwork.getLinks().values()) {
			link.getAttributes().removeAttribute("bus:lanes");
			link.getAttributes().removeAttribute("bus:lanes:backward");
			link.getAttributes().removeAttribute("bus:lanes:forward");
			link.getAttributes().removeAttribute("cycleway:left");
			link.getAttributes().removeAttribute("cycleway:right");
		}

		//NetworkUtils.writeNetwork(defaultNetwork, "/Users/gregorr/Documents/work/stuff/CRBAM/withContrib/bikeWithContribMATSim.output_network.xml.gz");

*/


		SimWrapper simWrapper = SimWrapper.create(config);
		simWrapper.addDashboard(new BikeDashboard());

		simWrapper.generate(Path.of("/Users/gregorr/Documents/work/stuff/CRBAM/withContrib"));
		simWrapper.run(Path.of("/Users/gregorr/Documents/work/stuff/CRBAM/withContrib"));



		return 0;
	}
}
