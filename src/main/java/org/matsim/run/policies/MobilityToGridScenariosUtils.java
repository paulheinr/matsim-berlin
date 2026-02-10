package org.matsim.run.policies;

import com.google.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.AnalysisMainModeIdentifier;
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

	/**
	 * This is an adapted version of class OpenBerlinIntermodalPtDrtRouterAnalysisModeIdentifier for sharing modes.
	 */
	static final class OpenBerlinIntermodalPtSharingRouterAnalysisModeIdentifier implements AnalysisMainModeIdentifier {
		public static final String ANALYSIS_MAIN_MODE_PT_WITH_SHARING_USED_FOR_ACCESS_OR_EGRESS = "pt_w_sharing_used";
		private static final Logger log = LogManager.getLogger(OpenBerlinIntermodalPtSharingRouterAnalysisModeIdentifier.class);
		private final List<String> modeHierarchy = new ArrayList<>() ;
		private final List<String> sharingModes;

		@Inject
		OpenBerlinIntermodalPtSharingRouterAnalysisModeIdentifier() {
			sharingModes = Arrays.asList(OpenBerlinSharingScenario.E_SCOOTER);

			modeHierarchy.add( TransportMode.walk ) ;
			// TransportMode.bike is not registered as main mode, only "bicycle" ;
			modeHierarchy.add( TransportMode.bike );
			modeHierarchy.add( TransportMode.ride ) ;
			modeHierarchy.add( TransportMode.car ) ;
			modeHierarchy.add( "car2" ) ;
			for (String sharingMode: sharingModes) {
				modeHierarchy.add( sharingMode ) ;
			}
			modeHierarchy.add( TransportMode.pt ) ;
			modeHierarchy.add( "freight" );
			modeHierarchy.add( "truck" );

			// NOTE: This hierarchical stuff is not so great: is park-n-ride a car trip or a pt trip?  Could weigh it by distance, or by time spent
			// in respective mode.  Or have combined modes as separate modes.  In any case, can't do it at the leg level, since it does not
			// make sense to have the system calibrate towards something where we have counted the car and the pt part of a multimodal
			// trip as two separate trips. kai, sep'16
		}

		@Override public String identifyMainMode( List<? extends PlanElement> planElements ) {
			int mainModeIndex = -1 ;
			List<String> modesFound = new ArrayList<>();
			for ( PlanElement pe : planElements ) {
				int index;
				String mode;
				if ( pe instanceof Leg leg) {
					mode = leg.getMode();
				} else {
					continue;
				}
				if (mode.equals(TransportMode.non_network_walk)) {
					// skip, this is only a helper mode in case walk is routed on the network
					continue;
				}
				modesFound.add(mode);
				index = modeHierarchy.indexOf( mode ) ;
				if ( index < 0 ) {
					log.error("unknown mode={}", mode );
					throw new IllegalStateException("") ;
				}
				if ( index > mainModeIndex ) {
					mainModeIndex = index ;
				}
			}
			if (mainModeIndex == -1) {
				log.error("no main mode found for trip {}", planElements);
				throw new IllegalStateException("") ;
			}

			String mainMode = modeHierarchy.get( mainModeIndex ) ;
			// differentiate pt monomodal/intermodal
			if (mainMode.equals(TransportMode.pt)) {
				boolean isSharingPt = false;
				for (String modeFound: modesFound) {
					if (modeFound.equals(TransportMode.pt)) {
						continue;
					} else if (modeFound.equals(TransportMode.walk)) {
						continue;
					} else if (sharingModes.contains(modeFound)) {
						isSharingPt = true;
					} else {
						log.error("unknown intermodal pt trip: {}", planElements);
						throw new IllegalStateException("unknown intermodal pt trip");
					}
				}

				if (isSharingPt) {
					return ANALYSIS_MAIN_MODE_PT_WITH_SHARING_USED_FOR_ACCESS_OR_EGRESS;
				} else {
					return TransportMode.pt;
				}

			} else {
				return mainMode;
			}
		}
	}

	/**
	 * This is an adapted version of class OpenBerlinIntermodalPtDrtRouterModeIdentifier for sharing modes.
	 * I do not understand why this class is necessary as -- except some small differences -- is the same as OpenBerlinIntermodalPtSharingRouterAnalysisModeIdentifier.
	 * For OpenBerlinDrtScenario such a system of 2 classes seems to be necessary, so we will use it for sharing as well. -sm0226
	 */
	static final class OpenBerlinIntermodalPtSharingRouterModeIdentifier implements AnalysisMainModeIdentifier {
		private final List<String> modeHierarchy = new ArrayList<>() ;
		private final List<String> sharingModes;

		@Inject
		OpenBerlinIntermodalPtSharingRouterModeIdentifier() {
			sharingModes = Arrays.asList(OpenBerlinSharingScenario.E_SCOOTER);

			modeHierarchy.add( TransportMode.walk ) ;
			modeHierarchy.add( TransportMode.bike );
			modeHierarchy.add( TransportMode.ride ) ;
			modeHierarchy.add( TransportMode.car ) ;
			modeHierarchy.add( "car2" ) ;
			for (String sharingMode: sharingModes) {
				modeHierarchy.add( sharingMode ) ;
			}
			modeHierarchy.add( TransportMode.pt ) ;
			modeHierarchy.add( "freight" );

			// NOTE: This hierarchical stuff is not so great: is park-n-ride a car trip or a pt trip?  Could weigh it by distance, or by time spent
			// in respective mode.  Or have combined modes as separate modes.  In any case, can't do it at the leg level, since it does not
			// make sense to have the system calibrate towards something where we have counted the car and the pt part of a multimodal
			// trip as two separate trips. kai, sep'16
		}

		@Override public String identifyMainMode( List<? extends PlanElement> planElements ) {
			int mainModeIndex = -1 ;
			for ( PlanElement pe : planElements ) {
				int index;
				String mode;
				if ( pe instanceof Leg leg ) {
					mode = leg.getMode();
				} else {
					continue;
				}
				if (mode.equals(TransportMode.non_network_walk)) {
					// skip, this is only a helper mode in case walk is routed on the network
					continue;
				}
				index = modeHierarchy.indexOf( mode ) ;
				if ( index < 0 ) {
					log.error("unknown mode={}", mode);
					throw new IllegalStateException("") ;
				}
				if ( index > mainModeIndex ) {
					mainModeIndex = index ;
				}
			}
			if (mainModeIndex == -1) {
				log.error("no main mode found for trip {}", planElements);
				throw new IllegalStateException("") ;
			}
			return modeHierarchy.get( mainModeIndex ) ;
		}
	}
}
