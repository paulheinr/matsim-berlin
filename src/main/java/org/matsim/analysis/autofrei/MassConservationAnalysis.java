package org.matsim.analysis.autofrei;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.PopulationUtils;

import java.util.Set;

public class MassConservationAnalysis {
	public static void main(String[] args) {
		String base = "/Users/paulh/public-svn/matsim/scenarios/countries/de/berlin/berlin-v6.4/output/berlin-v6.4-10pct/";
//		Population output = PopulationUtils.readPopulation(base + "berlin-v6.4.output_plans.xml.gz");
//		Population experienced = PopulationUtils.readPopulation(base + "berlin-v6.4.output_experienced_plans.xml.gz");

		Population initial = PopulationUtils.readPopulation(base + "../../input/berlin-v6.4-10pct.plans-initial.xml.gz");

		Set<Id<Person>> set = Set.of(Id.createPersonId("bb_a2c70697"));

		filter(initial, set);
//		filter(output, set);

//		PopulationUtils.writePopulation(output, "output.xml");
		PopulationUtils.writePopulation(initial, "output_initial.xml");
	}

	private static void filter(Population population, Set<Id<Person>> positiveSet) {
		population.getPersons().entrySet().removeIf(entry -> !positiveSet.contains(entry.getKey()));
		for (Id<Person> personId : positiveSet) {
			Person person = population.getPersons().get(personId);
			PersonUtils.removeUnselectedPlans(person);
		}
	}
}
