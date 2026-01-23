package org.matsim.analysis.autofrei;

import com.google.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.listener.IterationStartsListener;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.run.policies.autofrei.RunAutofreiPolicy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

public class ParkingAnalyzer implements IterationStartsListener {
	private static final Logger log = LogManager.getLogger(ParkingAnalyzer.class);

	public static void main(String[] args) {
		String events = "./assets/filtered_parking_autofrei.xml.gz";
		String networkPath = "./assets/berlin-v6.4.output_network.xml.gz";
		String output = "./assets/parking_occupancy_autofrei.csv";

		ParkingEventHandler peh = run(events);
		peh.writeCsv(Path.of(output), NetworkUtils.readNetwork(networkPath));
	}

	// convenience method to run the parking analyzer standalone
	public static ParkingEventHandler run(Consumer<EventsManager> readEvents) {
		EventsManager eventsManager = EventsUtils.createEventsManager();
		Set<String> modes = Set.of(TransportMode.car, TransportMode.truck, "freight", RunAutofreiPolicy.NEW_MODE_SMALL_SCALE_COMMERCIAL);

		ParkingInitializerEventsHandler initializer = new ParkingInitializerEventsHandler(modes);
		eventsManager.addHandler(initializer);

		readEvents.accept(eventsManager);
		Map<Id<Link>, Double> initial = initializer.getCountByLink(-1);

		EventsManager eventsManager1 = EventsUtils.createEventsManager();
		ParkingEventHandler parkingHandler = new ParkingEventHandler(initial, modes);
		eventsManager1.addHandler(parkingHandler);
		readEvents.accept(eventsManager1);

		return parkingHandler;
	}

	// convenience method to run the parking analyzer standalone
	public static ParkingEventHandler run(String events) {
		return run((em) -> EventsUtils.readEvents(em, events));
	}

	private int iteration = -1;

	@Inject
	private EventsManager eventsManager;

	@Inject
	private ParkingInitializerEventsHandler initializer;

	@Inject
	private ParkingEventHandler parkingEventHandler;

	/// Returns the occupancy of a link at a given time bin (from, to) in a given iteration. Both from and to are included.
	public List<OccupancyEntry> occupancy(int iteration, Id<Link> linkId, double from, double to) {
		if (iteration != this.iteration) {
			log.error("Requested occupancy for iteration {}, but current iteration is {}. Returning NaN.", iteration, this.iteration);
			throw new RuntimeException("Iteration " + iteration + " is out of order");
		}

		List<OccupancyEntry> occupancyEntries = parkingEventHandler.getOccupancyEntriesByLink(initializer.getCountByLink(iteration)).get(linkId);

		// filter entries to only those that overlap with [from, to]
		List<OccupancyEntry> list = new ArrayList<>();
		for (OccupancyEntry o : occupancyEntries) {
			// there are 2 cases that we won't include: (1) entry is completely before 'from' and (2) entry is completely after 'to'
			if (o.toTime() <= from || o.fromTime() >= to) {
				continue;
			}
			// otherwise, we have some overlap; shrink the entry to fit into [from, to]
			double entryFrom = Math.max(o.fromTime(), from);
			double entryTo = Math.min(o.toTime(), to);
			list.add(new OccupancyEntry(entryFrom, entryTo, o.occupancy()));
		}
		return list;
	}

	private static boolean isPt(Id<Link> linkId) {
		String s = linkId.toString();
		return s.startsWith("pt_") || s.contains("_pt_");
	}

	@Override
	public void notifyIterationStarts(IterationStartsEvent event) {
		this.iteration = event.getIteration();
	}

	public static class ParkingEventHandler implements VehicleEntersTrafficEventHandler, VehicleLeavesTrafficEventHandler {
		private final Map<Id<Link>, Double> initial;
		private final Map<Id<Link>, List<OccupancyChange>> occupancyChangesByLink = new HashMap<>(600000);
		private Map<Id<Link>, List<OccupancyEntry>> occupancyEntriesByLinkCache = null;

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

			occupancyChangesByLink.putIfAbsent(event.getLinkId(), new LinkedList<>());
			var list = occupancyChangesByLink.get(event.getLinkId());
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

			if (lastLink != null && !lastLink.equals(event.getLinkId())) {
				// remove the parking at the last link
				List<OccupancyChange> occupancyChanges = occupancyChangesByLink.get(lastLink);
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
			occupancyChangesByLink.putIfAbsent(event.getLinkId(), new ArrayList<>());
			var list = occupancyChangesByLink.get(event.getLinkId());
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

		@Override
		public void reset(int iteration) {
			// reset initial
			occupancyChangesByLink.clear();
			lastParkingLinkByPersonAndMode.clear();
			occupancyEntriesByLinkCache.clear();
		}

		void writeCsv(Path file, Network network) {
			var header = List.of("linkId", "from_time", "to_time", "length", "occupancy", "initial");
			var rows = new ArrayList<List<String>>();
			for (var entry : occupancyChangesByLink.entrySet()) {
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

		Map<Id<Link>, List<OccupancyChange>> getOccupancyChangesByLink(Map<Id<Link>, Double> initial) {
			initial.forEach((linkId, change) -> {
				occupancyChangesByLink.putIfAbsent(linkId, new LinkedList<>());
				var list = occupancyChangesByLink.get(linkId);
				list.addFirst(new OccupancyChange(0, change));
			});

			return occupancyChangesByLink;
		}

		public Map<Id<Link>, List<OccupancyEntry>> getOccupancyEntriesByLink(Map<Id<Link>, Double> initial) {
			if (occupancyEntriesByLinkCache == null) {
				//fill cache if needed
				occupancyEntriesByLinkCache = new HashMap<>();
				for (var entry : occupancyChangesByLink.entrySet()) {
					occupancyEntriesByLinkCache.put(entry.getKey(), convert(entry.getValue()));
				}
			}
			return this.occupancyEntriesByLinkCache;
		}
	}

	// This class is needed because we need to first determine the initial parking counts before we can track the parking events properly.
	private static class ParkingInitializerEventsHandler implements VehicleEntersTrafficEventHandler, VehicleLeavesTrafficEventHandler {
		private final Map<Id<Link>, Double> countByLink = new HashMap<>(600000);
		private final Map<String, Set<Id<Person>>> personsAlreadyTravelledByMode = new HashMap<>(600000);
		private final Set<String> parkingModes;
		private int iteration = -1;

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
			personsAlreadyTravelledByMode.putIfAbsent(event.getNetworkMode(), new HashSet<>());
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
			boolean added = ids.add(event.getPersonId());

			if (!added) {
				throw new RuntimeException("Person " + event.getPersonId() + " is already en route with mode " + event.getNetworkMode());
			}
		}

		@Override
		public void reset(int iteration) {
			this.iteration = iteration;
			countByLink.clear();
			personsAlreadyTravelledByMode.clear();
		}

		public Map<Id<Link>, Double> getCountByLink(int iteration) {
			if (iteration != this.iteration) {
				throw new IllegalStateException("Iteration " + iteration + " is out of order");
			}
			return countByLink;
		}
	}

	public record OccupancyEntry(double fromTime, double toTime, double occupancy) {
	}

	public record OccupancyChange(double time, double change) {
	}
}

