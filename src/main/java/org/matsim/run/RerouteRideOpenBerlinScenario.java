package org.matsim.run;

import com.google.inject.Inject;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.application.MATSimApplication;
import org.matsim.core.config.Config;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.listener.IterationStartsListener;
import org.matsim.core.population.algorithms.PersonAlgorithm;
import org.matsim.core.router.TripRouter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.utils.timing.TimeInterpretation;
import org.matsim.core.utils.timing.TimeTracker;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.FacilitiesUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class RerouteRideOpenBerlinScenario extends OpenBerlinScenario {

	public static void main(String[] args) {
		MATSimApplication.execute(RerouteRideOpenBerlinScenario.class, args);
	}

	@Override
	protected void prepareControler(Controler controler) {
		super.prepareControler(controler);
		controler.addControlerListener(new TeleportedTripFixListener());
	}

	private class TeleportedTripFixListener implements IterationStartsListener {
		private static final Logger logger = LoggerFactory.getLogger(TeleportedTripFixListener.class);

		@Inject
		Population population;

		@Inject
		TripRouter tripRouter;

		@Inject
		TimeInterpretation timeInterpretation;

		@Inject
		ActivityFacilities activityFacilities;

		@Inject
		Config config;

		@Override
		public void notifyIterationStarts(IterationStartsEvent event) {
			logger.info("Starting to fix teleported trips.");
			TeleportedTripRouter planRouter = new TeleportedTripRouter();

			population.getPersons().values().forEach(planRouter::run);
			logger.info("Finished teleported trips.");
		}

		private class TeleportedTripRouter implements PersonAlgorithm {
			@Override
			public void run(Person person) {
				Plan plan = person.getSelectedPlan();

				TimeTracker timeTracker = new TimeTracker(timeInterpretation);

				for (TripStructureUtils.Trip t : TripStructureUtils.getTrips(plan)) {
					String mode = TripStructureUtils.identifyMainMode(t.getTripElements());

//					if (mode.equals(TransportMode.pt)) {
//						continue;
//					}
//
//					if (config.qsim().getMainModes().contains(mode)) {
//						continue;
//					}

					if (!mode.equals(TransportMode.ride)) {
						continue;
					}

					timeTracker.addActivity(t.getOriginActivity());

					List<? extends PlanElement> newTrip = tripRouter.calcRoute(
						mode,
						FacilitiesUtils.toFacility(t.getOriginActivity(), activityFacilities),
						FacilitiesUtils.toFacility(t.getDestinationActivity(), activityFacilities),
						timeTracker.getTime().seconds(),
						plan.getPerson(),
						t.getTripAttributes()
					);

//					logger.info("Calculated new trip with main mode {} for person {}.", mode, person.getId());

					TripRouter.insertTrip(plan, t.getOriginActivity(), newTrip, t.getDestinationActivity());

					timeTracker.addElements(newTrip);
				}
			}
		}
	}
}
