package org.matsim.analysis.autofrei;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
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
import org.matsim.run.policies.autofrei.RunAutofreiPolicy;

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

	public static void run(String events, String networkPath, String output) {
		EventsManager eventsManager = EventsUtils.createEventsManager();
		Set<String> modes = Set.of(TransportMode.car, TransportMode.truck, "freight", RunAutofreiPolicy.NEW_MODE_SMALL_SCALE_COMMERCIAL);

		ParkingInitializerEventsHandler initializer = new ParkingInitializerEventsHandler(modes);
		eventsManager.addHandler(initializer);

		EventsUtils.readEvents(eventsManager, events);
		Map<Id<Link>, Double> initial = initializer.getCountByLink();

		EventsManager eventsManager1 = EventsUtils.createEventsManager();
		ParkingEventHandler parkingHandler = new ParkingEventHandler(initial, modes);
		eventsManager1.addHandler(parkingHandler);
		EventsUtils.readEvents(eventsManager1, events);

		Network network = NetworkUtils.readNetwork(networkPath);
		parkingHandler.writeCsv(Path.of(output), network);
	}

	private static class ParkingEventHandler implements VehicleEntersTrafficEventHandler, VehicleLeavesTrafficEventHandler {
		private final Map<Id<Link>, Double> initial;
		private final Map<Id<Link>, List<OccupancyChange>> occupancyChangeByLink = new HashMap<>(600000);

		private final Set<String> parkingModes;
		private final Map<String, Map<Id<Person>, Id<Link>>> lastParkingLinkByPersonAndMode = new HashMap<>(600000);

		public ParkingEventHandler(Map<Id<Link>, Double> initial, Set<String> parkingModes) {
			this.initial = initial;
			this.parkingModes = parkingModes;
		}

		@Override
		public void handleEvent(VehicleEntersTrafficEvent event) {
			if (isPt(event.getLinkId())) {
				// Ignore pt links
				return;
			}

			if (!parkingModes.contains(event.getNetworkMode())) {
				// Other mode than parking mode => ignore
				return;
			}

			occupancyChangeByLink.putIfAbsent(event.getLinkId(), new LinkedList<>());
			var list = occupancyChangeByLink.get(event.getLinkId());
			if (list.isEmpty()) {
				// This Enter event is the first event for this link
				// no occupancy known yet, use initial occupancy
				Double initialOccupancy = initial.get(event.getLinkId());

				if (initialOccupancy == null) {
					throw new RuntimeException("Vehicle is leaving link " + event.getLinkId() + ", but no initial occupancy is known.");
				}

				list.add(new OccupancyChange(0, initialOccupancy));
			}

			list.add(new OccupancyChange(event.getTime(), -1.));

			// We need to check mass conservation here. If a person leaves a link where he/she never parked, we need to remove the parking count at the last link.
			// This might happen if the activity locations are very close and the coord Distance in subtour mode choice is > 0.
			lastParkingLinkByPersonAndMode.putIfAbsent(event.getNetworkMode(), new HashMap<>());
			var linkByPerson = lastParkingLinkByPersonAndMode.get(event.getNetworkMode());
			Id<Link> lastLink = linkByPerson.get(event.getPersonId());

			if (!lastLink.equals(event.getLinkId())) {
				// remove the parking at the last link
				List<OccupancyChange> occupancyChanges = occupancyChangeByLink.get(lastLink);
				if (occupancyChanges == null) {
					throw new RuntimeException("No occupancy changes found for link " + lastLink + " while trying to remove a parking event.");
				}
				occupancyChanges.add(new OccupancyChange(event.getTime(), -1.));
				linkByPerson.remove(lastLink);
			}
		}

		@Override
		public void handleEvent(VehicleLeavesTrafficEvent event) {
			if (isPt(event.getLinkId())) {
				// Ignore pt links
				return;
			}

			if (!parkingModes.contains(event.getNetworkMode())) {
				// Other mode than parking mode => ignore
				return;
			}

			// update history with initial occupancy if needed
			occupancyChangeByLink.putIfAbsent(event.getLinkId(), new ArrayList<>());
			var list = occupancyChangeByLink.get(event.getLinkId());
			if (list.isEmpty()) {
				// This Leave event is the first event for this link
				// no occupancy known yet, use initial occupancy
				Double initialOccupancy = initial.getOrDefault(event.getLinkId(), 0.); // we might have links with 0 initial occupancy => they are not in the map
				list.add(new OccupancyChange(0, initialOccupancy));
			}
			list.add(new OccupancyChange(event.getTime(), 1.));

			// track where this person last parked
			lastParkingLinkByPersonAndMode.putIfAbsent(event.getNetworkMode(), new HashMap<>());
			var linkByPerson = lastParkingLinkByPersonAndMode.get(event.getNetworkMode());
			linkByPerson.put(event.getPersonId(), event.getLinkId());
		}

		void writeCsv(Path file, Network network) {
			var header = List.of("linkId", "from_time", "to_time", "length", "occupancy", "initial");
			var rows = new ArrayList<List<String>>();
			for (var entry : occupancyChangeByLink.entrySet()) {
				Id<Link> linkId = entry.getKey();

				List<OccupancyChange> changes = entry.getValue();

				OccupancyEntry max = convert(changes).stream().max(Comparator.comparing(OccupancyEntry::occupancy)).orElseThrow();
				var row = List.of(
					linkId.toString(),
					String.valueOf(max.fromTime()),
					String.valueOf(max.toTime()),
					String.valueOf(network.getLinks().get(linkId).getLength()),
					String.valueOf(max.occupancy()),
					String.valueOf(initial.getOrDefault(linkId, -1.))
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

		static List<OccupancyEntry> convert(List<OccupancyChange> occupancyChanges) {
			List<OccupancyEntry> entries = new ArrayList<>();
			occupancyChanges.sort(Comparator.comparingDouble(OccupancyChange::time));
			double currentOccupancy = 0.;
			double lastTime = 0.;

			for (OccupancyChange change : occupancyChanges) {
				// in case of time 0, only the occupancy is added and no entry is created
				if (change.time() > lastTime) {
					entries.add(new OccupancyEntry(lastTime, change.time(), currentOccupancy));
					lastTime = change.time();
				}
				currentOccupancy += change.change();
			}
			return entries;
		}
	}

	private static class ParkingInitializerEventsHandler implements VehicleEntersTrafficEventHandler, VehicleLeavesTrafficEventHandler {
		private final Map<Id<Link>, Double> countByLink = new HashMap<>(600000);
		private final Map<String, Set<Id<Person>>> personsAlreadyTravelledByMode = new HashMap<>(600000);
		private final Set<String> parkingModes;

		public ParkingInitializerEventsHandler(Set<String> parkingModes) {
			this.parkingModes = parkingModes;
		}

		@Override
		public void handleEvent(VehicleEntersTrafficEvent event) {
			if (isPt(event.getLinkId())) {
				// Ignore pt links
				return;
			}

			if (!parkingModes.contains(event.getNetworkMode())) {
				// Other mode than parking mode => ignore
				return;
			}

			// No mass conservation needs to be taken into account because we only track whether an agent is already travelled with a mode or not.
			// We don't care if the last trip ended at the same link where the new trip starts.
			Set<Id<Person>> persons = personsAlreadyTravelledByMode.get(event.getNetworkMode());
			boolean alreadyTravelled = persons.remove(event.getPersonId());

			if (!alreadyTravelled) {
				// Vehicle entered traffic without having left before => A car was already parked.
				countByLink.putIfAbsent(event.getLinkId(), 0.);
				double count = countByLink.get(event.getLinkId());
				count++;
				countByLink.put(event.getLinkId(), count);
			}
			// Nothing else to do: A vehicle already registered as traveled is now entering traffic.


		}

		@Override
		public void handleEvent(VehicleLeavesTrafficEvent event) {
			if (isPt(event.getLinkId())) {
				// Ignore pt links
				return;
			}

			if (!parkingModes.contains(event.getNetworkMode())) {
				// Other mode than parking mode => ignore
				return;
			}

			personsAlreadyTravelledByMode.putIfAbsent(event.getNetworkMode(), new HashSet<>());
			Set<Id<Person>> ids = personsAlreadyTravelledByMode.get(event.getNetworkMode());
			boolean before = ids.add(event.getPersonId());

			if (before) {
				throw new RuntimeException("Person " + event.getPersonId() + " is already en route with mode " + event.getNetworkMode());
			}
		}

		public Map<Id<Link>, Double> getCountByLink() {
			return countByLink;
		}
	}

	public double occupancy(Id<Link> linkId, double time) {
		return 0.0;
	}

	private static boolean isPt(Id<Link> linkId) {
		String s = linkId.toString();
		return s.startsWith("pt_") || s.contains("_pt_");
	}

	record OccupancyEntry(double fromTime, double toTime, double occupancy) {
	}

	record OccupancyChange(double time, double change) {
	}
}

