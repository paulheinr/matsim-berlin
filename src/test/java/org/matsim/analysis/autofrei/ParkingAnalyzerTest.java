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
		// Person 1: departs from link1 at t=1, arrives at link2 at t=2
		VehicleEntersTrafficEvent ve1 = new VehicleEntersTrafficEvent(1., Id.createPersonId("p1"), Id.createLinkId("1"), Id.createVehicleId("p1"), "car", 1.0);
		VehicleLeavesTrafficEvent vl1 = new VehicleLeavesTrafficEvent(2., Id.createPersonId("p1"), Id.createLinkId("2"), Id.createVehicleId("p1"), "car", 1.0);

		// Person 2: departs from link1 at t=3, arrives at link2 at t=4
		VehicleEntersTrafficEvent ve2 = new VehicleEntersTrafficEvent(3., Id.createPersonId("p2"), Id.createLinkId("1"), Id.createVehicleId("p2"), "car", 1.0);
		VehicleLeavesTrafficEvent vl2 = new VehicleLeavesTrafficEvent(4., Id.createPersonId("p2"), Id.createLinkId("2"), Id.createVehicleId("p2"), "car", 1.0);

		ParkingAnalyzer.ParkingEventHandler peh = ParkingAnalyzer.run((em) -> {
			em.processEvent(ve1);
			em.processEvent(vl1);
			em.processEvent(ve2);
			em.processEvent(vl2);
		});

		{
			Map<Id<Link>, List<ParkingAnalyzer.OccupancyChange>> actual = peh.getOccupancyChangesByLink();
			Map<Id<Link>, List<ParkingAnalyzer.OccupancyChange>> expected = Map.of(
				Id.createLinkId("1"), List.of(
					new ParkingAnalyzer.OccupancyChange(0., 2.),  // initial: 2 parked cars
					new ParkingAnalyzer.OccupancyChange(1., -1),  // p1 departs
					new ParkingAnalyzer.OccupancyChange(3., -1)   // p2 departs
				),
				Id.createLinkId("2"), List.of(
					new ParkingAnalyzer.OccupancyChange(2., 1),   // p1 arrives
					new ParkingAnalyzer.OccupancyChange(4., 1)    // p2 arrives
				)
			);

			Assertions.assertEquals(expected, actual);
		}

		{
			Map<Id<Link>, List<ParkingAnalyzer.OccupancyEntry>> actual = peh.getOccupancyEntriesByLink();
			Map<Id<Link>, List<ParkingAnalyzer.OccupancyEntry>> expected = Map.of(
				Id.createLinkId("1"), List.of(
					new ParkingAnalyzer.OccupancyEntry(0., 1., 2),   // initial: 2 parked
					new ParkingAnalyzer.OccupancyEntry(1., 3., 1),   // after p1 departs: 1 parked
					new ParkingAnalyzer.OccupancyEntry(3., Double.POSITIVE_INFINITY, 0)  // after p2 departs: 0 parked
				),
				Id.createLinkId("2"), List.of(
					new ParkingAnalyzer.OccupancyEntry(0., 2., 0),   // initially: 0 parked
					new ParkingAnalyzer.OccupancyEntry(2., 4., 1),   // after p1 arrives: 1 parked
					new ParkingAnalyzer.OccupancyEntry(4., Double.POSITIVE_INFINITY, 2)  // after p2 arrives: 2 parked
				)
			);

			Assertions.assertEquals(expected, actual);
		}
	}

	// Tests that one link with some first departures and intermediate arrivals is correctly handled.
	@Test
	void threePersons() {
		// link1 is of interest
		// person1 departs at link0 at t=1
		VehicleEntersTrafficEvent ve1 = new VehicleEntersTrafficEvent(1., Id.createPersonId("p1"), Id.createLinkId("0"), Id.createVehicleId("p1"), "car", 1.0);
		// person1 arrives at link1 at t=2
		VehicleLeavesTrafficEvent vl1 = new VehicleLeavesTrafficEvent(2., Id.createPersonId("p1"), Id.createLinkId("1"), Id.createVehicleId("p1"), "car", 1.0);
		// person2 departs at link1 at t=3
		VehicleEntersTrafficEvent ve2 = new VehicleEntersTrafficEvent(3., Id.createPersonId("p2"), Id.createLinkId("1"), Id.createVehicleId("p2"), "car", 1.0);
		// person3 departs at link1 at t=4
		VehicleEntersTrafficEvent ve3 = new VehicleEntersTrafficEvent(4., Id.createPersonId("p3"), Id.createLinkId("1"), Id.createVehicleId("p3"), "car", 1.0);
		// person2 arrives at link2 at t=5
		VehicleLeavesTrafficEvent vl2 = new VehicleLeavesTrafficEvent(5., Id.createPersonId("p2"), Id.createLinkId("2"), Id.createVehicleId("p2"), "car", 1.0);
		// person3 arrives at link3 at t=6
		VehicleLeavesTrafficEvent vl3 = new VehicleLeavesTrafficEvent(6., Id.createPersonId("p3"), Id.createLinkId("3"), Id.createVehicleId("p3"), "car", 1.0);

		ParkingAnalyzer.ParkingEventHandler peh = ParkingAnalyzer.run((em) -> {
			em.processEvent(ve1);
			em.processEvent(vl1);
			em.processEvent(ve2);
			em.processEvent(ve3);
			em.processEvent(vl2);
			em.processEvent(vl3);
		});

		{
			Map<Id<Link>, List<ParkingAnalyzer.OccupancyChange>> actual = peh.getOccupancyChangesByLink();
			Map<Id<Link>, List<ParkingAnalyzer.OccupancyChange>> expected = Map.of(
				Id.createLinkId("0"), List.of(
					new ParkingAnalyzer.OccupancyChange(0., 1.),  // initial: 1 parked (p1)
					new ParkingAnalyzer.OccupancyChange(1., -1)   // p1 departs
				),
				Id.createLinkId("1"), List.of(
					new ParkingAnalyzer.OccupancyChange(0., 2.),  // initial: 2 parked (p2, p3)
					new ParkingAnalyzer.OccupancyChange(2., 1),   // p1 arrives
					new ParkingAnalyzer.OccupancyChange(3., -1),  // p2 departs
					new ParkingAnalyzer.OccupancyChange(4., -1)   // p3 departs
				),
				Id.createLinkId("2"), List.of(
					new ParkingAnalyzer.OccupancyChange(5., 1)    // p2 arrives
				),
				Id.createLinkId("3"), List.of(
					new ParkingAnalyzer.OccupancyChange(6., 1)    // p3 arrives
				)
			);

			Assertions.assertEquals(expected, actual);
		}

		{
			Map<Id<Link>, List<ParkingAnalyzer.OccupancyEntry>> actual = peh.getOccupancyEntriesByLink();
			Map<Id<Link>, List<ParkingAnalyzer.OccupancyEntry>> expected = Map.of(
				Id.createLinkId("0"), List.of(
					new ParkingAnalyzer.OccupancyEntry(0., 1., 1),   // initial: 1 parked
					new ParkingAnalyzer.OccupancyEntry(1., Double.POSITIVE_INFINITY, 0)  // after p1 departs: 0
				),
				Id.createLinkId("1"), List.of(
					new ParkingAnalyzer.OccupancyEntry(0., 2., 2),   // initial: 2 parked (p2, p3)
					new ParkingAnalyzer.OccupancyEntry(2., 3., 3),   // after p1 arrives: 3
					new ParkingAnalyzer.OccupancyEntry(3., 4., 2),   // after p2 departs: 2
					new ParkingAnalyzer.OccupancyEntry(4., Double.POSITIVE_INFINITY, 1)  // after p3 departs: 1
				),
				Id.createLinkId("2"), List.of(
					new ParkingAnalyzer.OccupancyEntry(0., 5., 0),   // initially: 0
					new ParkingAnalyzer.OccupancyEntry(5., Double.POSITIVE_INFINITY, 1)  // after p2 arrives: 1
				),
				Id.createLinkId("3"), List.of(
					new ParkingAnalyzer.OccupancyEntry(0., 6., 0),   // initially: 0
					new ParkingAnalyzer.OccupancyEntry(6., Double.POSITIVE_INFINITY, 1)  // after p3 arrives: 1
				)
			);

			Assertions.assertEquals(expected, actual);
		}
	}

	// Two persons depart at a link and than arrive at another link with different modes.
	@Test
	void twoPersons_differentModes() {
		// Person 1: uses "car" mode, departs from link1 at t=1, arrives at link2 at t=2
		VehicleEntersTrafficEvent ve1 = new VehicleEntersTrafficEvent(1., Id.createPersonId("p1"), Id.createLinkId("1"), Id.createVehicleId("p1"), "car", 1.0);
		VehicleLeavesTrafficEvent vl1 = new VehicleLeavesTrafficEvent(2., Id.createPersonId("p1"), Id.createLinkId("2"), Id.createVehicleId("p1"), "car", 1.0);

		// Person 2: uses "truck" mode, departs from link1 at t=3, arrives at link2 at t=4
		VehicleEntersTrafficEvent ve2 = new VehicleEntersTrafficEvent(3., Id.createPersonId("p2"), Id.createLinkId("1"), Id.createVehicleId("p2"), "truck", 1.0);
		VehicleLeavesTrafficEvent vl2 = new VehicleLeavesTrafficEvent(4., Id.createPersonId("p2"), Id.createLinkId("2"), Id.createVehicleId("p2"), "truck", 1.0);

		ParkingAnalyzer.ParkingEventHandler peh = ParkingAnalyzer.run((em) -> {
			em.processEvent(ve1);
			em.processEvent(vl1);
			em.processEvent(ve2);
			em.processEvent(vl2);
		});

		{
			// Both modes contribute to occupancy changes on the same links
			Map<Id<Link>, List<ParkingAnalyzer.OccupancyChange>> actual = peh.getOccupancyChangesByLink();
			Map<Id<Link>, List<ParkingAnalyzer.OccupancyChange>> expected = Map.of(
				Id.createLinkId("1"), List.of(
					new ParkingAnalyzer.OccupancyChange(0., 2.),  // initial: 2 parked (1 car, 1 truck)
					new ParkingAnalyzer.OccupancyChange(1., -1),  // p1 (car) departs
					new ParkingAnalyzer.OccupancyChange(3., -1)   // p2 (truck) departs
				),
				Id.createLinkId("2"), List.of(
					new ParkingAnalyzer.OccupancyChange(2., 1),   // p1 (car) arrives
					new ParkingAnalyzer.OccupancyChange(4., 1)    // p2 (truck) arrives
				)
			);

			Assertions.assertEquals(expected, actual);
		}

		{
			Map<Id<Link>, List<ParkingAnalyzer.OccupancyEntry>> actual = peh.getOccupancyEntriesByLink();
			Map<Id<Link>, List<ParkingAnalyzer.OccupancyEntry>> expected = Map.of(
				Id.createLinkId("1"), List.of(
					new ParkingAnalyzer.OccupancyEntry(0., 1., 2),   // initial: 2 parked
					new ParkingAnalyzer.OccupancyEntry(1., 3., 1),   // after p1 departs: 1
					new ParkingAnalyzer.OccupancyEntry(3., Double.POSITIVE_INFINITY, 0)  // after p2 departs: 0
				),
				Id.createLinkId("2"), List.of(
					new ParkingAnalyzer.OccupancyEntry(0., 2., 0),   // initially: 0
					new ParkingAnalyzer.OccupancyEntry(2., 4., 1),   // after p1 arrives: 1
					new ParkingAnalyzer.OccupancyEntry(4., Double.POSITIVE_INFINITY, 2)  // after p2 arrives: 2
				)
			);

			Assertions.assertEquals(expected, actual);
		}
	}

	static class IntegrationTest {
		@Test
		void occupancyCalculation() {
			throw new NotImplementedException();
		}
	}
}
