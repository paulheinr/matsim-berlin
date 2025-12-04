package org.matsim.run.prepare;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.*;
import org.matsim.application.MATSimAppCommand;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt.utils.TransitScheduleValidator;
import org.matsim.vehicles.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;

import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

/**
 * Add Siemensbahn from Hauptbahnhof to Gartenfeld to an existing scenario.
 */
public final class AddSiemensbahnToScenario implements MATSimAppCommand {
	private static final Logger log = LogManager.getLogger(AddSiemensbahnToScenario.class);

	@CommandLine.Option(names = "--network", description = "Path to network file", required = true)
	private String networkFile;
	@CommandLine.Option(names = "--transit-vehicles", description = "Path to transit vehicles file", required = true)
	private String transitVehiclesFile;
	@CommandLine.Option(names = "--transit-schedule", description = "Path to transit schedule file", required = true)
	private String transitScheduleFile;
	@CommandLine.Option(names = "--output", description = "Output path of the prepared network", required = true)
	private String outputPath;

	public static void main(String[] args) {
		new AddSiemensbahnToScenario().execute(args);
	}

	@Override
	public Integer call() throws Exception {
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

		new TransitScheduleReader(scenario).readFile(transitScheduleFile);
		new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);
		new MatsimVehicleReader(scenario.getTransitVehicles()).readFile(transitVehiclesFile);

		addSiemensbahn(scenario);

//		write output files
		new NetworkWriter(scenario.getNetwork()).write(Paths.get(outputPath, "gartenfeld-v6.4.network-cutout-with-SiBa-10min.xml.gz").toString());
		log.info("Network including Siemensbahn until Gartenfeld written to {}", Paths.get(outputPath, "gartenfeld-v6.4.network-cutout-with-SiBa-10min.xml.gz"));

		new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(Paths.get(outputPath, "berlin-v6.4-transitSchedule-with-SiBa-10min.xml.gz").toString());
		log.info("Transit schedule including Siemensbahn until Gartenfeld written to {}", Paths.get(outputPath, "berlin-v6.4-transitSchedule-with-SiBa-10min.xml.gz"));

		new MatsimVehicleWriter(scenario.getTransitVehicles()).writeFile(Paths.get(outputPath, "berlin-v6.4-transitVehicles-with-SiBa-10min.xml.gz").toString());
		log.info("Transit schedule including Siemensbahn until Gartenfeld written to {}", Paths.get(outputPath, "berlin-v6.4-transitVehicles-with-SiBa-10min.xml.gz"));

		return 0;
	}

	public void addSiemensbahn(Scenario scenario) {
		Network network = scenario.getNetwork();
		NetworkFactory networkFactory = network.getFactory();
		TransitSchedule schedule = scenario.getTransitSchedule();
		TransitScheduleFactory scheduleFactory = schedule.getFactory();
		Vehicles transitVehicles = scenario.getTransitVehicles();

		// vehicle type
		//S-Bahn
		VehicleType vehicleTypeSBahn = transitVehicles.getVehicleTypes().get(Id.create("S-Bahn_veh_type", VehicleType.class));

		// get existing stations and add new stations (nodes) Siemensbahn to network
		Node hauptbahnhof = network.getNodes().get(Id.createNodeId("pt_359974_SuburbanRailway"));
		Node perlebergerBruecke = NetworkUtils.createAndAddNode(network, Id.createNodeId("pt_116410_SuburbanRailway"), new Coord( 795604.72, 5829611.59));
		Node westhafen = network.getNodes().get(Id.createNodeId("pt_473821_SuburbanRailway"));
		Node beusselstrasse = network.getNodes().get(Id.createNodeId("pt_502749_SuburbanRailway"));
		Node jungfernheide = network.getNodes().get(Id.createNodeId("pt_397108_SuburbanRailway"));
		Node wernerwerk = NetworkUtils.createAndAddNode(network, Id.createNodeId("pt_116420_SuburbanRailway"), new Coord(789976.12, 5829083.16));
		Node siemensstadt = NetworkUtils.createAndAddNode(network, Id.createNodeId("pt_116430_SuburbanRailway"), new Coord(789100.47, 5829563.55));
		Node gartenfeld = NetworkUtils.createAndAddNode(network, Id.createNodeId("pt_116440_SuburbanRailway"), new Coord(788060.58, 5830351.63));

		// get existing links and create new links Siemensbahn
		Link hauptbahnhofPerlebergerBruecke = createLink("pt_359974_SuburbanRailway-pt_116410_SuburbanRailway", hauptbahnhof, perlebergerBruecke, networkFactory);
		Link perlebergerBrueckeHauptbahnhof = createLink("pt_116410_SuburbanRailway-pt_359974_SuburbanRailway", perlebergerBruecke, hauptbahnhof, networkFactory);
		Link perlebergerBrueckeWesthafen = createLink("pt_116410_SuburbanRailway-pt_473821_SuburbanRailway", perlebergerBruecke, westhafen, networkFactory);
		Link westhafenPerlebergerBruecke = createLink("pt_473821_SuburbanRailway-pt_116410_SuburbanRailway", westhafen, perlebergerBruecke, networkFactory);
		Link westhafenBeusselstrasse = network.getLinks().get(Id.createLinkId("pt_473821_SuburbanRailway-pt_502749_SuburbanRailway"));
		Link beusselstrasseWesthafen = network.getLinks().get(Id.createLinkId("pt_502749_SuburbanRailway-pt_473821_SuburbanRailway"));
		Link beusselstrasseJungfernheide = network.getLinks().get(Id.createLinkId("pt_502749_SuburbanRailway-pt_397108_SuburbanRailway"));
		Link jungfernheideBeusselstrasse = network.getLinks().get(Id.createLinkId("pt_397108_SuburbanRailway-pt_502749_SuburbanRailway"));
		Link jungfernheideWernerwerk = createLink("pt_397108_SuburbanRailway-pt_116420_SuburbanRailway", jungfernheide, wernerwerk, networkFactory);
		Link wernerwerkJungfernheide = createLink("pt_116420_SuburbanRailway-pt_397108_SuburbanRailway", wernerwerk, jungfernheide, networkFactory);
		Link wernerwerkSiemensstadt = createLink("pt_116420_SuburbanRailway-pt_116430_SuburbanRailway", wernerwerk, siemensstadt, networkFactory);
		Link siemensstadtWernerwerk = createLink("pt_116430_SuburbanRailway-pt_116420_SuburbanRailway", siemensstadt, wernerwerk, networkFactory);
		Link siemensstadtGartenfeld = createLink("pt_116430_SuburbanRailway-pt_116440_SuburbanRailway", siemensstadt, gartenfeld, networkFactory);
		Link gartenfeldSiemensstadt = createLink("pt_116440_SuburbanRailway-pt_116430_SuburbanRailway", gartenfeld, siemensstadt, networkFactory);

		// get existing loop links and create loop links
		Link stationHauptbahnhof = network.getLinks().get(Id.createLinkId("pt_359974_SuburbanRailway"));
		Link stationPerlebergerBruecke = createLink("pt_116410_SuburbanRailway", perlebergerBruecke, perlebergerBruecke, networkFactory);
		Link stationWesthafen = network.getLinks().get(Id.createLinkId("pt_473821_SuburbanRailway"));
		Link stationBeusselstrasse = network.getLinks().get(Id.createLinkId("pt_502749_SuburbanRailway"));
		Link stationJungfernheide = network.getLinks().get(Id.createLinkId("pt_397108_SuburbanRailway"));
		Link stationWernerwerk = createLink("pt_116420_SuburbanRailway", wernerwerk, wernerwerk, networkFactory);
		Link stationSiemensstadt = createLink("pt_116430_SuburbanRailway", siemensstadt, siemensstadt, networkFactory);
		Link stationGartenfeld = createLink("pt_116440_SuburbanRailway", gartenfeld, gartenfeld, networkFactory);

		//		add links to network
		Set.of(hauptbahnhofPerlebergerBruecke, perlebergerBrueckeHauptbahnhof, perlebergerBrueckeWesthafen, westhafenPerlebergerBruecke, jungfernheideWernerwerk,
				wernerwerkJungfernheide, wernerwerkSiemensstadt, siemensstadtWernerwerk, siemensstadtGartenfeld, gartenfeldSiemensstadt, stationPerlebergerBruecke,
				stationWernerwerk, stationSiemensstadt, stationGartenfeld)
			.forEach(network::addLink);

		// stop facilities e to w
		TransitStopFacility stopFacility1EW = scheduleFactory.createTransitStopFacility(Id.create("HauptbahnhofEW", TransitStopFacility.class), hauptbahnhof.getCoord(), false);
		TransitStopFacility stopFacility2EW = scheduleFactory.createTransitStopFacility(Id.create("PerlebergerBrueckeEW", TransitStopFacility.class), perlebergerBruecke.getCoord(), false);
		TransitStopFacility stopFacility3EW = scheduleFactory.createTransitStopFacility(Id.create("WesthafenEW", TransitStopFacility.class), westhafen.getCoord(), false);
		TransitStopFacility stopFacility4EW = scheduleFactory.createTransitStopFacility(Id.create("BeusselstrasseEW", TransitStopFacility.class), beusselstrasse.getCoord(), false);
		TransitStopFacility stopFacility5EW = scheduleFactory.createTransitStopFacility(Id.create("JungfernheideEW", TransitStopFacility.class), jungfernheide.getCoord(), false);
		TransitStopFacility stopFacility6EW = scheduleFactory.createTransitStopFacility(Id.create("WernerwerkEW", TransitStopFacility.class), wernerwerk.getCoord(), false);
		TransitStopFacility stopFacility7EW = scheduleFactory.createTransitStopFacility(Id.create("SiemensstadtEW", TransitStopFacility.class), siemensstadt.getCoord(), false);
		TransitStopFacility stopFacility8EW = scheduleFactory.createTransitStopFacility(Id.create("GartenfeldEW", TransitStopFacility.class), gartenfeld.getCoord(), false);
		// stop facilities w to e
		TransitStopFacility stopFacility1WE = scheduleFactory.createTransitStopFacility(Id.create("HauptbahnhofWE", TransitStopFacility.class), hauptbahnhof.getCoord(), false);
		TransitStopFacility stopFacility2WE = scheduleFactory.createTransitStopFacility(Id.create("PerlebergerBrueckeWE", TransitStopFacility.class), perlebergerBruecke.getCoord(), false);
		TransitStopFacility stopFacility3WE = scheduleFactory.createTransitStopFacility(Id.create("WesthafenWE", TransitStopFacility.class), westhafen.getCoord(), false);
		TransitStopFacility stopFacility4WE = scheduleFactory.createTransitStopFacility(Id.create("BeusselstrasseWE", TransitStopFacility.class), beusselstrasse.getCoord(), false);
		TransitStopFacility stopFacility5WE = scheduleFactory.createTransitStopFacility(Id.create("JungfernheideWE", TransitStopFacility.class), jungfernheide.getCoord(), false);
		TransitStopFacility stopFacility6WE = scheduleFactory.createTransitStopFacility(Id.create("WernerwerkWE", TransitStopFacility.class), wernerwerk.getCoord(), false);
		TransitStopFacility stopFacility7WE = scheduleFactory.createTransitStopFacility(Id.create("SiemensstadtWE", TransitStopFacility.class), siemensstadt.getCoord(), false);
		TransitStopFacility stopFacility8WE = scheduleFactory.createTransitStopFacility(Id.create("GartenfeldWE", TransitStopFacility.class), gartenfeld.getCoord(), false);

//		set link ids. has to be done separately, seufz
		stopFacility1EW.setLinkId(stationHauptbahnhof.getId());
		stopFacility2EW.setLinkId(stationPerlebergerBruecke.getId());
		stopFacility3EW.setLinkId(stationWesthafen.getId());
		stopFacility4EW.setLinkId(stationBeusselstrasse.getId());
		stopFacility5EW.setLinkId(stationJungfernheide.getId());
		stopFacility6EW.setLinkId(stationWernerwerk.getId());
		stopFacility7EW.setLinkId(stationSiemensstadt.getId());
		stopFacility8EW.setLinkId(stationGartenfeld.getId());
		stopFacility1WE.setLinkId(stationHauptbahnhof.getId());
		stopFacility2WE.setLinkId(stationPerlebergerBruecke.getId());
		stopFacility3WE.setLinkId(stationWesthafen.getId());
		stopFacility4WE.setLinkId(stationBeusselstrasse.getId());
		stopFacility5WE.setLinkId(stationJungfernheide.getId());
		stopFacility6WE.setLinkId(stationWernerwerk.getId());
		stopFacility7WE.setLinkId(stationSiemensstadt.getId());
		stopFacility8WE.setLinkId(stationGartenfeld.getId());

//		add transit stops to s hedule
		Set.of(stopFacility1EW, stopFacility2EW, stopFacility3EW, stopFacility4EW, stopFacility5EW, stopFacility6EW, stopFacility7EW, stopFacility8EW)
				.forEach(schedule::addStopFacility);
		Set.of(stopFacility1WE, stopFacility2WE, stopFacility3WE, stopFacility4WE, stopFacility5WE, stopFacility6WE, stopFacility7WE, stopFacility8WE)
			.forEach(schedule::addStopFacility);

		// stations e to w
		TransitRouteStop stop1EW = scheduleFactory.createTransitRouteStop(stopFacility1EW, 0, 0);
		TransitRouteStop stop2EW = scheduleFactory.createTransitRouteStop(stopFacility2EW, 100, 130);
		TransitRouteStop stop3EW = scheduleFactory.createTransitRouteStop(stopFacility3EW, 197, 227);
		TransitRouteStop stop4EW = scheduleFactory.createTransitRouteStop(stopFacility4EW, 298, 328);
		TransitRouteStop stop5EW = scheduleFactory.createTransitRouteStop(stopFacility5EW, 459, 489);
		TransitRouteStop stop6EW = scheduleFactory.createTransitRouteStop(stopFacility6EW, 631, 661);
		TransitRouteStop stop7EW = scheduleFactory.createTransitRouteStop(stopFacility7EW, 735, 765);
		TransitRouteStop stop8EW = scheduleFactory.createTransitRouteStop(stopFacility8EW, 855, 885);

		// stations w to e
		TransitRouteStop stop3WE = scheduleFactory.createTransitRouteStop(stopFacility8WE, 236, 266);
		TransitRouteStop stop4WE = scheduleFactory.createTransitRouteStop(stopFacility7WE, 356, 386);
		TransitRouteStop stop5WE = scheduleFactory.createTransitRouteStop(stopFacility6WE, 460, 490);
		TransitRouteStop stop6WE = scheduleFactory.createTransitRouteStop(stopFacility5WE, 632, 662);
		TransitRouteStop stop7WE = scheduleFactory.createTransitRouteStop(stopFacility4WE, 793, 823);
		TransitRouteStop stop8WE = scheduleFactory.createTransitRouteStop(stopFacility3WE, 894, 924);
		TransitRouteStop stop9WE = scheduleFactory.createTransitRouteStop(stopFacility2WE, 991, 1021);
		TransitRouteStop stop10WE = scheduleFactory.createTransitRouteStop(stopFacility1WE, 1121, 1151);

		List<TransitRouteStop> transitRouteStopsEW = List.of(stop1EW, stop2EW, stop3EW, stop4EW, stop5EW, stop6EW, stop7EW, stop8EW);
		List<TransitRouteStop> transitRouteStopsWE = List.of(stop3WE, stop4WE, stop5WE, stop6WE, stop7WE, stop8WE, stop9WE, stop10WE);
		//set await departure time to true
		transitRouteStopsEW.forEach(s -> s.setAwaitDepartureTime(true));
		transitRouteStopsWE.forEach(s -> s.setAwaitDepartureTime(true));

//		create network route for transit route creation
		NetworkRoute networkRouteEW = RouteUtils.createLinkNetworkRouteImpl(stationHauptbahnhof.getId(),
			List.of(hauptbahnhofPerlebergerBruecke.getId(), stationPerlebergerBruecke.getId(), perlebergerBrueckeWesthafen.getId(), stationWesthafen.getId(), westhafenBeusselstrasse.getId(), stationBeusselstrasse.getId(),
				beusselstrasseJungfernheide.getId(), stationJungfernheide.getId(), jungfernheideWernerwerk.getId(), stationWernerwerk.getId(), wernerwerkSiemensstadt.getId(), stationSiemensstadt.getId(),
				siemensstadtGartenfeld.getId()), stationGartenfeld.getId());
		NetworkRoute networkRouteWE = RouteUtils.createLinkNetworkRouteImpl(stationGartenfeld.getId(),
			List.of(gartenfeldSiemensstadt.getId(), stationSiemensstadt.getId(),
				siemensstadtWernerwerk.getId(), stationWernerwerk.getId(), wernerwerkJungfernheide.getId(), stationJungfernheide.getId(), jungfernheideBeusselstrasse.getId(), stationBeusselstrasse.getId(), beusselstrasseWesthafen.getId(),
				stationWesthafen.getId(), westhafenPerlebergerBruecke.getId(), stationPerlebergerBruecke.getId(), perlebergerBrueckeHauptbahnhof.getId()), stationHauptbahnhof.getId());

//		create transit routes
		TransitRoute transitRouteEW = scheduleFactory.createTransitRoute(Id.create("SiBaEW", TransitRoute.class),
				networkRouteEW, transitRouteStopsEW, TransportMode.pt);
		TransitRoute transitRouteWE = scheduleFactory.createTransitRoute(Id.create("SiBaWE", TransitRoute.class),
				networkRouteWE, transitRouteStopsWE, TransportMode.pt);

		// create departures and vehicles for each departure E to W
		addDeparturesAndTransitVehicles(transitRouteEW, "SiBa_vehicleEW_", scheduleFactory, transitVehicles, vehicleTypeSBahn);
		// create departures and vehicles for each departure W to E
		addDeparturesAndTransitVehicles(transitRouteWE, "SiBa_vehicleWE_", scheduleFactory, transitVehicles, vehicleTypeSBahn);

		// transit line E to W
		TransitLine transitLineEW = scheduleFactory.createTransitLine(Id.create("SiBaEW", TransitLine.class));
		transitLineEW.addRoute(transitRouteEW);
		schedule.addTransitLine(transitLineEW);

		// line W to E
		TransitLine transitLineWE = scheduleFactory.createTransitLine(Id.create("SiBaWE", TransitLine.class));
		transitLineWE.addRoute(transitRouteWE);
		schedule.addTransitLine(transitLineWE);

		//Check schedule and network
		TransitScheduleValidator.ValidationResult checkResult = TransitScheduleValidator.validateAll(schedule, network);
		List<String> warnings = checkResult.getWarnings();
		if (!warnings.isEmpty())
			log.warn("TransitScheduleValidator warnings: {}", String.join("\n", warnings));

		if (checkResult.isValid()) {
			log.info("TransitSchedule and Network valid according to TransitScheduleValidator");
		} else {
			log.error("TransitScheduleValidator errors: {}", String.join("\n", checkResult.getErrors()));
			throw new IllegalStateException("TransitSchedule and/or Network invalid");
		}
	}

	private void addDeparturesAndTransitVehicles(TransitRoute transitRoute, String vehicleIdPrefix, TransitScheduleFactory scheduleFactory, Vehicles transitVehicles, VehicleType vehicleTypeSBahn) {
		for (int i = 3 * 3600; i < 24 * 3600; i += 600) {
			Departure departure = scheduleFactory.createDeparture(Id.create("departure_" + i, Departure.class), i);
			Vehicle vehicle = transitVehicles.getFactory().createVehicle(Id.createVehicleId(vehicleIdPrefix + "100" + i), vehicleTypeSBahn);
			departure.setVehicleId(vehicle.getId());

			transitVehicles.addVehicle(vehicle);
			transitRoute.addDeparture(departure);
		}
	}

	private Link createLink(String id, Node from, Node to, NetworkFactory networkFactory) {
		Link link = networkFactory.createLink(Id.createLinkId(id), from, to);
		link.setAllowedModes(Set.of(TransportMode.pt));
		link.setFreespeed(15);
		link.setCapacity(10000);
		return link;
	}
}
