package org.matsim.analysis.autofrei;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.events.handler.VehicleEntersTrafficEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleLeavesTrafficEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.network.NetworkUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class ParkingAnalyzer {
	public static void main(String[] args) {
		String events = "./assets/filtered_parking_autofrei.xml.gz";
		String networkPath = "./assets/berlin-v6.4.output_network.xml.gz";
		String output = "./assets/parking_occupancy_autofrei.csv";

		run(events, networkPath, output);
	}

	private static void run(String events, String networkPath, String output) {
		EventsManager eventsManager = EventsUtils.createEventsManager();
		ParkingInitializerEventsHandler initializer = new ParkingInitializerEventsHandler();
		eventsManager.addHandler(initializer);

		EventsUtils.readEvents(eventsManager, events);
		Map<Id<Link>, Integer> initial = initializer.getCountByLink();

		EventsManager eventsManager1 = EventsUtils.createEventsManager();
		ParkingEventHandler parkingHandler = new ParkingEventHandler(initial);
		eventsManager1.addHandler(parkingHandler);
		EventsUtils.readEvents(eventsManager1, events);

		Network network = NetworkUtils.readNetwork(networkPath);
		parkingHandler.writeCsv(Path.of(output), network);
	}

	private static class ParkingEventHandler implements VehicleEntersTrafficEventHandler, VehicleLeavesTrafficEventHandler {
		private final Map<Id<Link>, Integer> initial;
		private final Map<Id<Link>, List<OccupancyEntry>> occupancyByLink = new HashMap<>(600000);

		public ParkingEventHandler(Map<Id<Link>, Integer> initial) {
			this.initial = initial;
		}

		@Override
		public void handleEvent(VehicleEntersTrafficEvent event) {
			if (isPt(event.getLinkId())) {
				// Ignore pt links
				return;
			}

			occupancyByLink.putIfAbsent(event.getLinkId(), new ArrayList<>());
			var list = occupancyByLink.get(event.getLinkId());
			if (list.isEmpty()) {
				// no occupancy known yet, use initial occupancy
				Integer initialOccupancy = initial.get(event.getLinkId()); //crashes if no initial occupancy known

				if (initialOccupancy == null) {
					System.err.println("No initial occupancy known for link " + event.getLinkId() + ", assuming 0");
					initialOccupancy = 0;
				}

				list.add(new OccupancyEntry(0, event.getTime(), initialOccupancy));
			} else {
				// get last entry and create new one with increased occupancy
				var last = list.getLast();
				list.add(new OccupancyEntry(last.toTime(), event.getTime(), last.occupancy() - 1));
			}
		}

		@Override
		public void handleEvent(VehicleLeavesTrafficEvent event) {
			if (isPt(event.getLinkId())) {
				// Ignore pt links
				return;
			}

			occupancyByLink.putIfAbsent(event.getLinkId(), new ArrayList<>());
			var list = occupancyByLink.get(event.getLinkId());
			if (list.isEmpty()) {
				// no occupancy known yet, use initial occupancy
				int initialOccupancy = initial.getOrDefault(event.getLinkId(), 0); // we might have links with 0 initial occupancy => they are not in the map
				list.add(new OccupancyEntry(0, event.getTime(), initialOccupancy));
			} else {
				// get last entry and create new one with increased occupancy
				var last = list.getLast();
				list.add(new OccupancyEntry(last.toTime(), event.getTime(), last.occupancy() + 1));
			}
		}

		void writeCsv(Path file, Network network) {
			var header = List.of("linkId", "from_time", "to_time", "length", "occupancy", "initial");
			var rows = new ArrayList<List<String>>();
			for (var entry : occupancyByLink.entrySet()) {
				Id<Link> linkId = entry.getKey();

				OccupancyEntry max = entry.getValue().stream().max(Comparator.comparing(OccupancyEntry::occupancy)).orElseThrow();
				var row = List.of(
					linkId.toString(),
					String.valueOf(max.fromTime()),
					String.valueOf(max.toTime()),
					String.valueOf(network.getLinks().get(linkId).getLength()),
					String.valueOf(max.occupancy()),
					String.valueOf(initial.getOrDefault(linkId, -1))
				);
				rows.add(row);
			}

			// Use Apache Commons CSV to write the file
			try (var writer = java.nio.file.Files.newBufferedWriter(file);
				 var csvPrinter = org.apache.commons.csv.CSVFormat.DEFAULT.builder().setHeader(header.toArray(new String[0])).build().print(writer)) {
				for (var row : rows) {
					csvPrinter.printRecord(row);
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private static class ParkingInitializerEventsHandler implements VehicleEntersTrafficEventHandler, VehicleLeavesTrafficEventHandler {
		private final Map<Id<Link>, Integer> countByLink = new HashMap<>(600000);
		private final Map<Id<Person>, Id<Link>> personLinkMap = new HashMap<>(600000);
		private double currentTime = 0;

		@Override
		public void handleEvent(VehicleEntersTrafficEvent event) {
			if (isPt(event.getLinkId())) {
				// Ignore pt links
				return;
			}

			currentTime = event.getTime();

			if (event.getPersonId().equals(Id.createPersonId("freight_2514"))) {
				System.out.println("debug");
			}

			var before = personLinkMap.remove(event.getPersonId());
			if (before == null) {
				// Vehicle entered traffic without having left before => A car was already parked.
				countByLink.putIfAbsent(event.getLinkId(), 0);
				int count = countByLink.get(event.getLinkId());
				count++;
				countByLink.put(event.getLinkId(), count);
			} else {
				// Check consistency
				if (!before.equals(event.getLinkId())) {
//					throw new RuntimeException("Person " + event.getPersonId() + " left from link " + before + " but entered at link " + event.getLinkId());
					// log warning
					System.err.println("Warning: Person " + event.getPersonId() + " left from link " + before + " but entered at link " + event.getLinkId());
				}
				// Nothing else to do: A car already registered as left before is now entering traffic.
			}

		}

		@Override
		public void handleEvent(VehicleLeavesTrafficEvent event) {
			if (isPt(event.getLinkId())) {
				// Ignore pt links
				return;
			}

			currentTime = event.getTime();

			if (event.getPersonId().equals(Id.createPersonId("freight_2514"))) {
				System.out.println("debug");
			}

			var before = personLinkMap.putIfAbsent(event.getPersonId(), event.getLinkId());
			if (before != null) {
				throw new RuntimeException("Person " + event.getPersonId() + " is already mapped to link " + before);
			}
		}

		public Map<Id<Link>, Integer> getCountByLink() {
			return countByLink;
		}
	}

	private static boolean isPt(Id<Link> linkId) {
		String s = linkId.toString();
		return s.startsWith("pt_") || s.contains("_pt_");
	}

	record OccupancyEntry(double fromTime, double toTime, int occupancy) {
	}
}

