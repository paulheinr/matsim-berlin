package org.matsim.run.policies;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.application.MATSimApplication;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.population.algorithms.XY2Links;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.run.OpenBerlinScenario;

// Run with command line args: run --1pct --output output/1pct-no-pt --iterations 0
public class OpenBerlinNoPt extends OpenBerlinScenario {
	public static void main(String[] args) {
		MATSimApplication.execute(OpenBerlinNoPt.class, args);
	}

	@Override
	protected Config prepareConfig(Config config) {
		config.scoring().setWriteExperiencedPlans(true);
		config.qsim().setLinkDynamics(QSimConfigGroup.LinkDynamics.FIFO);
		config.qsim().setTrafficDynamics(QSimConfigGroup.TrafficDynamics.queue);
		config.qsim().setStuckTime(30);
		return config;
	}

	@Override
	protected void prepareScenario(Scenario scenario) {
		super.prepareScenario(scenario);

		int count = 0;
		var it = scenario.getPopulation().getPersons().entrySet().iterator();
		XY2Links xy2Links = new XY2Links(scenario);

		while (it.hasNext()) {
			var e = it.next();
			if (TripStructureUtils.getLegs(e.getValue().getSelectedPlan()).stream().anyMatch(l -> l.getMode().equals(TransportMode.pt))) {
				it.remove();
				count++;
			}

			xy2Links.run(e.getValue().getSelectedPlan());
		}

		System.out.println("Removed " + count + " persons that had pt in their legs. Remaining persons: " + scenario.getPopulation().getPersons().size());

	}
}
