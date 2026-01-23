package org.matsim.run.deparking;

public class InverseLinearDeParkingApproach implements DeParkingApproach {
	@Override
	public double newParkingCost(double previousOccupancy, double previousCost) {
		// e.g., if occupancy was only 1/2, the new cost can also be 1/2 of the old to be more attractive.
		return previousOccupancy * previousCost;
	}
}
