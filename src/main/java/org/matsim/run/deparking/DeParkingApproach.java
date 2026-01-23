package org.matsim.run.deparking;

@FunctionalInterface
public interface DeParkingApproach {
	double newParkingCost(double previousRelativeOccupancy, double previousCost);
}
