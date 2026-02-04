package org.matsim.run.gartenfeld;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.contrib.emissions.HbefaVehicleCategory;
import org.matsim.run.Activities;
import org.matsim.vehicles.EngineInformation;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * class with useful methods, which are applied in multiple classes. Thus centralized here.
 */
public final class GartenfeldUtils {
	private static final String AVERAGE = "average";
	private static final Logger log = LogManager.getLogger(GartenfeldUtils.class);

	private GartenfeldUtils() {

	}

	/**
	 * Prepare vehicle types with necessary HBEFA information for emission analysis.
	 */
	static void prepareVehicleTypesForEmissionAnalysis(Scenario scenario) {
		for (VehicleType type : scenario.getVehicles().getVehicleTypes().values()) {
			EngineInformation engineInformation = type.getEngineInformation();

//				only set engine information if none are present
			if (engineInformation.getAttributes().isEmpty()) {
				switch (type.getId().toString()) {
					case TransportMode.car -> {
						VehicleUtils.setHbefaVehicleCategory(engineInformation, HbefaVehicleCategory.PASSENGER_CAR.toString());
//						based on car registrations in germany 2023: 30% petrol, 17% diesel, 30% Hybrid, 18% battery. Thus, average is the choice here.
						VehicleUtils.setHbefaTechnology(engineInformation, AVERAGE);
						VehicleUtils.setHbefaSizeClass(engineInformation, AVERAGE);
						VehicleUtils.setHbefaEmissionsConcept(engineInformation, AVERAGE);
					}
					case TransportMode.ride -> {
//							ignore ride, the mode is routed on network, but then teleported
						VehicleUtils.setHbefaVehicleCategory(engineInformation, HbefaVehicleCategory.NON_HBEFA_VEHICLE.toString());
						VehicleUtils.setHbefaTechnology(engineInformation, AVERAGE);
						VehicleUtils.setHbefaSizeClass(engineInformation, AVERAGE);
						VehicleUtils.setHbefaEmissionsConcept(engineInformation, AVERAGE);
					}
					case TransportMode.bike -> {
//							ignore bikes
						VehicleUtils.setHbefaVehicleCategory(engineInformation, HbefaVehicleCategory.NON_HBEFA_VEHICLE.toString());
						VehicleUtils.setHbefaTechnology(engineInformation, AVERAGE);
						VehicleUtils.setHbefaSizeClass(engineInformation, AVERAGE);
						VehicleUtils.setHbefaEmissionsConcept(engineInformation, AVERAGE);
					}
					case TransportMode.truck, "freight" -> {
						VehicleUtils.setHbefaVehicleCategory(engineInformation, HbefaVehicleCategory.HEAVY_GOODS_VEHICLE.toString());
						VehicleUtils.setHbefaTechnology(engineInformation, AVERAGE);
						VehicleUtils.setHbefaSizeClass(engineInformation, AVERAGE);
						VehicleUtils.setHbefaEmissionsConcept(engineInformation, AVERAGE);
					}
					default -> throw new IllegalArgumentException("does not know how to handle vehicleType " + type.getId().toString());
				}
			}
		}
//			ignore all pt veh types
		scenario.getTransitVehicles()
			.getVehicleTypes()
			.values().forEach(type -> VehicleUtils.setHbefaVehicleCategory(type.getEngineInformation(), HbefaVehicleCategory.NON_HBEFA_VEHICLE.toString()));
	}

	/**
	 * Disable wrap-around scoring of first and last act of the day by setting them to different subtypes "_morning" and "_evening".
	 */
	static void changeWrapAroundActsIntoMorningAndEveningActs(Scenario scenario) {
		Set<String> firstActTypes = new HashSet<>();
		Set<String> lastActTypes = new HashSet<>();

		for (Person p : scenario.getPopulation().getPersons().values()) {
//			ignore freight / commercial traffic agents and stay home agents
			if (!p.getAttributes().getAttribute("subpopulation").equals("person") ||
			p.getSelectedPlan().getPlanElements().size() == 1) {
				continue;
			}

			for (Plan plan : p.getPlans()) {
				Activity first = (Activity) plan.getPlanElements().getFirst();
				Activity last = (Activity) plan.getPlanElements().getLast();

				String[] splitFirst = first.getType().split("_");
				String typeFirst = String.join("_", Arrays.copyOfRange(splitFirst, 0, splitFirst.length - 1));
				int orginalTimeBinFirst = Integer.parseInt(splitFirst[splitFirst.length - 1]);
				firstActTypes.add(typeFirst);

				String[] splitLast = last.getType().split("_");
				String typeLast = String.join("_", Arrays.copyOfRange(splitLast, 0, splitLast.length - 1));
				int orginalTimeBinLast = Integer.parseInt(splitLast[splitLast.length - 1]);
				lastActTypes.add(typeLast);

				if (!typeFirst.equals(typeLast)) {
//					if first and last act do not have the same type, we will not change anything.
//					this is the pragmatic version. There are last acts with without startTime, endTime or maxDuration.
//					this needs to be repaired upstream (in the makefile process). -sm0226
					continue;
				}

				Double durationFirst = null;
				if (first.getEndTime().isDefined()) {
//					use act end time if defined
					durationFirst = first.getEndTime().seconds();
				}

				if (durationFirst == null && first.getMaximumDuration().isDefined()) {
					durationFirst = first.getMaximumDuration().seconds();
				}

				if (durationFirst == null) {
					log.fatal("Neither duration nor end time is defined for activity {} of agent {}. This should not happen, aborting!", first, p.getId());
					throw new IllegalStateException("");
				}

				int durationBinFirst = getDurationBin(durationFirst);

				first.setType(String.format("%s_%d", Activities.createMorningActivityType(typeFirst), durationBinFirst));

	//			act types of first and last act the same
				if (orginalTimeBinFirst != orginalTimeBinLast) {
					log.fatal("typical duration of first and last activity of person {} with the same act type {} are not the same. This should not happen, aborting!", p.getId(), typeLast);
					throw new IllegalStateException("");
				}
				double durationLast = orginalTimeBinLast - durationFirst;

				last.setType(String.format("%s_%d", Activities.createEveningActivityType(typeLast), getDurationBin(durationLast)));
				last.setMaximumDuration(durationLast);
				last.setEndTimeUndefined();
				last.setStartTimeUndefined();
			}
		}
		log.info("Activity types of first activity in plans: {}", firstActTypes);
		log.info("Activity types of last activity in plans: {}", lastActTypes);
	}

	private static int getDurationBin(Double duration) {
		final int maxCategories = 86400 / 600;

		int durationCategoryNr = (int) Math.round(duration / 600);

		if (durationCategoryNr <= 0) {
			durationCategoryNr = 1;
		}

		if (durationCategoryNr >= maxCategories) {
			durationCategoryNr = maxCategories;
		}
		return durationCategoryNr * 600;
	}

	/**
	 * Helper enum to enable/disable functionalities.
	 */
	enum FunctionalityHandling {ENABLED, DISABLED}
}
