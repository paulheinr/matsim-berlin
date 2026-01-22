package org.matsim.run.deparking;

@FunctionalInterface
public interface DeParkingApproach {
	double newParkingCost(double occurrence, double previousCost);
}
