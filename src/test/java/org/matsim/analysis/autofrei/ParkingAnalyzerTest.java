package org.matsim.analysis.autofrei;

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

	@Test
	void test() {
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
				Id.createLinkId("2"), List.of(new ParkingAnalyzer.OccupancyChange(0., 0.), new ParkingAnalyzer.OccupancyChange(1.0, 1))
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

}
