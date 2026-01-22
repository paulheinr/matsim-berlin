package org.matsim.run;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.*;
import org.matsim.application.MATSimApplication;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.listener.IterationStartsListener;
import org.matsim.core.population.algorithms.PersonAlgorithm;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.TripRouter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.core.utils.timing.TimeInterpretation;
import org.matsim.core.utils.timing.TimeTracker;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class UpdateRideTravelTimesOpenBerlinScenario extends OpenBerlinScenario {
	public static void main(String[] args) {
		MATSimApplication.execute(UpdateRideTravelTimesOpenBerlinScenario.class, args);
	}

	@Override
	protected void prepareControler(Controler controler) {
		super.prepareControler(controler);
		controler.addControlerListener(new RideTravelTimeUpdaterListener());
	}

	private static class RideTravelTimeUpdaterListener implements IterationStartsListener {
		private static final Logger logger = LoggerFactory.getLogger(UpdateRideTravelTimesOpenBerlinScenario.class);

		@Inject
		Scenario scenario;

		@Inject
		@Named("car")
		TravelTimeCalculator travelTimeCalculator;

		@Inject
		TimeInterpretation timeInterpretation;

		@Override
		public void notifyIterationStarts(IterationStartsEvent event) {
			logger.info("Starting to fix teleported trips.");
			RideTravelTimeUpdater planRouter = new RideTravelTimeUpdater();
			scenario.getPopulation().getPersons().values().forEach(planRouter::run);
			logger.info("Finished teleported trips.");
		}

		private class RideTravelTimeUpdater implements PersonAlgorithm {
			@Override
			public void run(Person person) {
				Plan plan = person.getSelectedPlan();

				TimeTracker timeTracker = new TimeTracker(timeInterpretation);

				for (TripStructureUtils.Trip t : TripStructureUtils.getTrips(plan)) {
					String mode = TripStructureUtils.identifyMainMode(t.getTripElements());
					timeTracker.addActivity(t.getOriginActivity());

					if (!mode.equals(TransportMode.ride)) {
						timeTracker.addElements(t.getTripElements());
						continue;
					}

					List<PlanElement> newTrip = new ArrayList<>(t.getTripElements().size());
					for (PlanElement tripElement : t.getTripElements()) {
						if (tripElement instanceof Activity activity) {
							// Keep stage activities as they are
							newTrip.add(activity);
							timeTracker.addActivity(activity);
						} else if (tripElement instanceof Leg leg) {
							if (leg.getMode().equals(TransportMode.ride)) {
								// Recalculate travel time for ride leg
								double totalTravelTime = 0.0;
								NetworkRoute route = (NetworkRoute) leg.getRoute();
								// we omit the first link on purpose
								List<Id<Link>> linkIds = new LinkedList<>(route.getLinkIds());
								// add last link as this is not part of getLinkIds()
								linkIds.add(leg.getRoute().getEndLinkId());
								for (Id<Link> linkId : linkIds) {
									VehicleType rideVehicleType = scenario.getVehicles().getVehicleTypes().get(Id.create("ride", VehicleType.class));
									Vehicle rideVehicle = scenario.getVehicles().getFactory().createVehicle(Id.createVehicleId("ride"), rideVehicleType);
									totalTravelTime += travelTimeCalculator.getLinkTravelTimes().getLinkTravelTime(scenario.getNetwork().getLinks().get(linkId), timeTracker.getTime().seconds(), person, rideVehicle);
								}
								leg.setTravelTime(totalTravelTime);
							}
							timeTracker.addLeg(leg);
							newTrip.add(leg);
						}
					}

					TripRouter.insertTrip(plan, t.getOriginActivity(), newTrip, t.getDestinationActivity());
				}
			}
		}
	}
}
