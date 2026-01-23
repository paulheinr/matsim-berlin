package org.matsim.analysis.autofrei;

import org.apache.commons.lang.NotImplementedException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.network.Link;
import org.matsim.testcases.MatsimTestUtils;

import java.util.List;
import java.util.Map;

class ParkingAnalyzerTest {
	@RegisterExtension
	MatsimTestUtils utils = new MatsimTestUtils();

	// One person enters traffic on link 1 at time 0, leaves traffic on link 2 at time 1.0.
	@Test
	void onePerson() {
		VehicleEntersTrafficEvent ve = new VehicleEntersTrafficEvent(1., Id.createPersonId("p"), Id.createLinkId("1"), Id.createVehicleId("p"), "car", 1.0);
		VehicleLeavesTrafficEvent vl = new VehicleLeavesTrafficEvent(1., Id.createPersonId("p"), Id.createLinkId("2"), Id.createVehicleId("p"), "car", 1.0);

		ParkingAnalyzer.ParkingEventHandler peh = ParkingAnalyzer.run((em) -> {
			em.processEvent(ve);
			em.processEvent(vl);
		});

		{
			Map<Id<Link>, List<ParkingAnalyzer.OccupancyChange>> actual = peh.getOccupancyChangesByLink();
			Map<Id<Link>, List<ParkingAnalyzer.OccupancyChange>> expected = Map.of(
				Id.createLinkId("1"), List.of(new ParkingAnalyzer.OccupancyChange(0., 1.), new ParkingAnalyzer.OccupancyChange(1.0, -1)),
				Id.createLinkId("2"), List.of(new ParkingAnalyzer.OccupancyChange(1., 1))
			);

			Assertions.assertEquals(expected, actual);
		}

		{
			Map<Id<Link>, List<ParkingAnalyzer.OccupancyEntry>> actual = peh.getOccupancyEntriesByLink();
			Map<Id<Link>, List<ParkingAnalyzer.OccupancyEntry>> expected = Map.of(
				Id.createLinkId("1"), List.of(new ParkingAnalyzer.OccupancyEntry(0., 1.0, 1), new ParkingAnalyzer.OccupancyEntry(1., Double.POSITIVE_INFINITY, 0)),
				Id.createLinkId("2"), List.of(new ParkingAnalyzer.OccupancyEntry(0., 1.0, 0), new ParkingAnalyzer.OccupancyEntry(1.0, Double.POSITIVE_INFINITY, 1))
			);

			Assertions.assertEquals(expected, actual);
		}
	}

	// Two persons depart at a link and than arrive at another link.
	@Test
	void twoPersons_sameRoute() {
		throw new NotImplementedException();
	}

	// Tests that one link with some first departures and intermediate arrivals is correctly handled.
	@Test
	void threePersons() {
		// link1 is of interest
		// person1 departs at link0
		// person1 arrives at link1
		// person2 departs at link1
		// person3 departs at link1
		// person2 arrives at link2
		// person3 arrives at link3
		throw new NotImplementedException();
	}

	// Two persons depart at a link and than arrive at another link with different modes.
	@Test
	void twoPersons_differentModes() {
		throw new NotImplementedException();
	}

	static class IntegrationTest {
		@Test
		void occupancyCalculation() {
			throw new NotImplementedException();
		}
	}
}
