package org.matsim.run.policies;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.TripStructureUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utils class for plugging together different policies in the same scenario.
 */
public final class MobilityToGridScenariosUtils {
	private static final Logger log = LogManager.getLogger(MobilityToGridScenariosUtils.class);

	private MobilityToGridScenariosUtils() {}

	static void addHomeOfficeWorkers(Scenario scenario, double additionalHomeOfficePct) {
		AtomicInteger stayHomeCount = new AtomicInteger(0);
		AtomicInteger workCount = new AtomicInteger(0);

//		count stay home agents among berlin residents
		scenario.getPopulation().getPersons().values()
			.stream()
			.filter(p -> p.getId().toString().contains("berlin"))
			.filter(p -> p.getSelectedPlan().getPlanElements().size() == 1)
			.filter(p -> p.getSelectedPlan().getPlanElements().getFirst() instanceof Activity)
			.filter(p -> ((Activity) p.getSelectedPlan().getPlanElements().getFirst()).getType().contains("home"))
			.forEach(p -> stayHomeCount.getAndIncrement());

//		count working agents among berlin residents
		List<? extends Person> workingPopulation = scenario.getPopulation().getPersons().values().stream()
			.filter(p -> p.getId().toString().contains("berlin"))
			.filter(p -> PopulationUtils.getActivities(
					p.getSelectedPlan(),
					TripStructureUtils.StageActivityHandling.ExcludeStageActivities
				)
				.stream()
				.anyMatch(act -> act.getType().contains("work"))).toList();

		workingPopulation.forEach(p -> workCount.incrementAndGet());

//		we need a mutable list for shuffling the population randomly
		List<? extends Person> mutableWorkingPopulation = new ArrayList<>(workingPopulation);

		double currentHomeOfficePct = Math.round((double) stayHomeCount.get() / workCount.get() * 100.) / 100.;
		double targetHomeOfficePct = currentHomeOfficePct + additionalHomeOfficePct;
		int targetHomeOfficeCount = (int) (targetHomeOfficePct * workCount.get());

		log.info("Your input population has {} berlin residents with stay home plans.", stayHomeCount.get());
		log.info("Your input population has {} berlin residents with at least one activity of type work.", workCount.get());
		log.info("Stay home agents are assumed to be working in home office. {}% of working berlin agents work in home office", currentHomeOfficePct * 100);
		log.info("Target home office share: {}%. Will start to pick agents randomly from the working berlin residents and transform them to home office workers.", targetHomeOfficePct * 100);

		Collections.shuffle(mutableWorkingPopulation, new Random(12));

		Set<Id<Person>> personsToAdapt = new HashSet<>();

		for (Person p : mutableWorkingPopulation) {
//			break loop as soon as we have the wished number of persons
			if (personsToAdapt.size() >= targetHomeOfficeCount - stayHomeCount.get()) {
				break;
			}
			personsToAdapt.add(p.getId());
		}

		scenario.getPopulation().getPersons().values()
			.stream()
			.filter(p -> personsToAdapt.contains(p.getId()))
			.forEach(p -> {
				Activity homeOffice = PopulationUtils.createActivityFromCoord("home_86400",
					new Coord((double) p.getAttributes().getAttribute("home_x"), (double) p.getAttributes().getAttribute("home_y")));
				Plan homeOfficePlan = PopulationUtils.createPlan();

				homeOfficePlan.addActivity(homeOffice);

				p.getPlans().clear();
				p.addPlan(homeOfficePlan);
				p.setSelectedPlan(homeOfficePlan);
			});

		log.info("Transformed {} working berlin residents to home office workers.", personsToAdapt.size());
	}
}
