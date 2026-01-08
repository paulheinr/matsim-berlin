/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
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

package org.matsim.prepare.population;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.*;

/**
 * @author gleich
 *
 * Return a population with all agents who have activities within a certain geographic area or
 * pass through this area by car (and ignore all other agents).
 *
 */
public class ModifyIncomeAndPersonScoreAttributesStreamReading {

	public static void main(String[] args) {
		boolean makeIncomeEqual = false;
		boolean personAsc = true;
		double mean = 0.0;
		double sigma = 3.0;
		SortedSet<String> modes = new TreeSet<>();
		modes.addAll(List.of(TransportMode.bike));
		String inputPopulationPath = "../public-svn/matsim/scenarios/countries/de/berlin/berlin-v6.3/input/berlin-v6.3-10pct.plans.xml.gz";
		String outputPopulationPath = "../public-svn/matsim/scenarios/countries/de/berlin/berlin-v6.3/input/berlin-v6.3-10pct.plans"
			+ (makeIncomeEqual ? "_income_equal" : "") + (personAsc ? "_person_asc_"
			+ String.join("-", modes) + "_mean_" + mean + "_sigma_" + sigma + "_uniform" : "") +".xml.gz";

		Scenario inputScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

		System.out.println("initialize done");
		StreamingPopulationWriter popWriter = new StreamingPopulationWriter();
		popWriter.writeStartPlans(outputPopulationPath);

		SplittableRandom splittableRandom = new SplittableRandom(1234);

		StreamingPopulationReader spr = new StreamingPopulationReader(inputScenario);
		spr.addAlgorithm(person -> {
			if (makeIncomeEqual) {
				Double oldIncome = PersonUtils.getIncome(person);
				if (oldIncome != null) {
					double newIncome = 1;
					PersonUtils.setIncome(person, newIncome);
				}
			}
			if (personAsc) {
				Map<String, String> modeConstants = PersonUtils.getModeConstants(person);
				if (modeConstants == null) {
					modeConstants = new HashMap<>();
				}
				for (String mode: modes) {
					//				double modeConstant = RandomFromDistribution.nextLogNormalFromMeanAndSigma(splittableRandom, mean, sigma);
					double modeConstant = (splittableRandom.nextDouble() - 0.5) * 2 * sigma + mean; // linear

					modeConstants.put(mode, Double.toString(modeConstant));
					PersonUtils.setModeConstants(person, modeConstants);
				}
			}

			popWriter.writePerson(person);
		}
		);
		spr.readFile(inputPopulationPath);
		popWriter.writeEndPlans();
		System.out.println("ModifyIncomeStreamReading done");
	}

}
