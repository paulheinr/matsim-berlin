/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2022 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.run.deparking;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.controler.AbstractModule;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author jfbischoff (SBB)
 */
public class ParkingCostModule extends AbstractModule {
	private final static List<String> PARKING_MODES = List.of(TransportMode.car, TransportMode.bike, TransportMode.truck, "freight");

	@Override
	public void install() {
		Collection<String> mainModes = switch (getConfig().controller().getMobsim()) {
			case "qsim" -> getConfig().qsim().getMainModes();
			case "hermes" -> getConfig().hermes().getMainModes();
			default -> throw new RuntimeException("ParkingCosts are currently supported for Qsim and Hermes");
		};

		for (String mode : PARKING_MODES) {
			if (mainModes.contains(mode)) {
				addEventHandlerBinding().toInstance(new MainModeParkingCostVehicleTracker(mode, Set.of()));
			} else {
				throw new RuntimeException("Mode " + mode + " not found in main modes: " + mainModes);
			}
		}
	}
}
