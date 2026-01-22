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


import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.utils.collections.CollectionUtils;

import java.util.Set;

/**
 * @author mrieser, jfbischoff (SBB)
 */
public class ParkingCostConfigGroup extends ReflectiveConfigGroup {

	public static final String GROUP_NAME = "parkingCosts";

	@Parameter
	@Comment("Activitiy types where no parking costs are charged, e.g., at home. char sequence must be part of the activity.")
	public String activityTypesWithoutParkingCost = null;

	public ParkingCostConfigGroup() {
		super(GROUP_NAME);
	}

	public Set<String> getActivityTypesWithoutParkingCost() {
		return CollectionUtils.stringToSet(activityTypesWithoutParkingCost);
	}
}
