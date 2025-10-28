package org.matsim.run.scoring;

import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.core.scoring.functions.ModeUtilityParameters;
import org.matsim.core.scoring.functions.ScoringParameters;
import org.matsim.pt.routes.DefaultTransitPassengerRoute;

/**
 * Scores individual transit legs based on the type of transit vehicle used.
 */
public final class TransitTripScoring implements SumScoringFunction.TripScoring {

	private final ScoringParameters parameters;
	private final TransitRouteToMode routeToMode;
	private double score = 0;

	public TransitTripScoring(ScoringParameters parameters, TransitRouteToMode routeToMode) {
		this.parameters = parameters;
		this.routeToMode = routeToMode;
	}

	@Override
	public void handleTrip(TripStructureUtils.Trip trip) {

		for (Leg leg : trip.getLegsOnly()) {

			// Score individual pt legs by their transport mode
			if (leg.getRoute() instanceof DefaultTransitPassengerRoute pt) {

				String ptMode = routeToMode.getMode(pt);
				if (ptMode != null && this.parameters.modeParams.containsKey(ptMode)) {
					ModeUtilityParameters p = this.parameters.modeParams.get(ptMode);

					// Perform the standard leg scoring
					score += p.constant;
					score += p.marginalUtilityOfDistance_m * pt.getDistance();
					score += p.monetaryDistanceCostRate * this.parameters.marginalUtilityOfMoney * pt.getDistance();

					if (leg.getTravelTime().isDefined()) {
						score += p.marginalUtilityOfTraveling_s * leg.getTravelTime().seconds();
					}
				}
			}
		}
	}

	@Override
	public double getScore() {
		return score;
	}

	@Override
	public void finish() {
		// Nothing to be done
	}

	@Override
	public void explainScore(StringBuilder out) {
		out.append("transit_score=").append(score);
	}

}
