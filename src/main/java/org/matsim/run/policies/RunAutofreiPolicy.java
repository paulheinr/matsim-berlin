package org.matsim.run.policies;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.geometry.jts.JTS;
import org.locationtech.jts.geom.Coordinate;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.application.MATSimApplication;
import org.matsim.application.options.ShpOptions;
import org.matsim.application.prepare.population.CleanPopulation;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.MultimodalNetworkCleaner;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.run.OpenBerlinScenario;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class RunAutofreiPolicy extends OpenBerlinScenario {
	private static final Logger log = LogManager.getLogger(RunAutofreiPolicy.class);
	private static final Set<String> RESTRICTED_MODES = Set.of(TransportMode.car, TransportMode.ride);

	public static void main(String[] args) {
		MATSimApplication.run(RunAutofreiPolicy.class, args);
//		readWriteNetwork();
	}

	private static void readWriteNetwork() {
		Network network = NetworkUtils.readNetwork(ConfigUtils.loadConfig("input/v6.4/berlin-v6.4.config.xml").network().getInputFile());
		restrictHighwayLinks(network);
		NetworkUtils.writeNetwork(network, "berlin-v6.4.network-restricted.xml.gz");
	}

	@Override
	protected void prepareScenario(Scenario scenario) {
		super.prepareScenario(scenario);
		restrictHighwayLinks(scenario.getNetwork());
		cleanPopulation(scenario.getPopulation());
	}

	private static void cleanPopulation(Population population) {
		population.getPersons().values().stream()
			.flatMap(p -> p.getPlans().stream())
			.flatMap(p -> p.getPlanElements().stream())
			.forEach(p -> {
				removeActivityLocation(p);
				CleanPopulation.removeRouteFromLeg(p);
			});
	}

	private static void removeActivityLocation(PlanElement el) {
		if (el instanceof Activity act) {
			act.setLinkId(null);

			// there are agents with no coordinate. Only if the coordinate is set, we remove the facilityId the preserve the activity location.
//			if (act.getCoord() != null) {
//				act.setFacilityId(null);
//			}
		}
	}

	private static void restrictHighwayLinks(Network network) {
		File shapefile = new File("input/v6.4/umweltzone/Umweltzone_Berlin.shp");
		ShpOptions shpOptions = new ShpOptions(shapefile.getAbsolutePath(), "EPSG:25833", null);
		ShpOptions.Index index = shpOptions.createIndex("_");

		MathTransform transform;
		try {
			CoordinateReferenceSystem sourceCRS = org.geotools.referencing.CRS.decode("EPSG:25832");
			CoordinateReferenceSystem targetCRS = org.geotools.referencing.CRS.decode("EPSG:25833");
			transform = org.geotools.referencing.CRS.findMathTransform(sourceCRS, targetCRS, true);
		} catch (FactoryException e) {
			throw new RuntimeException(e);
		}

		Set<Id<Link>> links = new HashSet<>();
		Corrections corrections = manualCorrections();
		for (Link link : network.getLinks().values()) {
			// Check if in Hundekopf area
			if (notContainsNode(link.getFromNode(), index, transform) && notContainsNode(link.getToNode(), index, transform)) {
				continue;
			}

			// Check if street name in manual add corrections. In this case no restriction.
			String name = (String) link.getAttributes().getAttribute("name");
			if (corrections.addStreet().contains(name)) {
				continue;
			}

			// Check if link id in manual add corrections. In this case no restriction.
			if (corrections.addLink().contains(link.getId())) {
				continue;
			}

			boolean restrict = false;

			// Check highway type
			if (!NetworkUtils.getHighwayType(link).equals("primary")) {
				restrict = true;
			}

			// Check if street name in manual remove corrections
			if (corrections.removeStreet().contains(name)) {
				restrict = true;
			}

			// Check if link id in manual remove corrections
			if (corrections.removeLinks().contains(link.getId())) {
				restrict = true;
			}

			// apply restrictions
			if (restrict) {
				links.add(link.getId());
				for (String restrictedMode : RESTRICTED_MODES) {
					NetworkUtils.removeAllowedMode(link, restrictedMode);
				}
			}
		}
		log.info("Restricted {} links for car traffic.", links.size());

		MultimodalNetworkCleaner multimodalNetworkCleaner = new MultimodalNetworkCleaner(network);
		multimodalNetworkCleaner.run(RESTRICTED_MODES);
	}

	private static boolean notContainsNode(Node node, ShpOptions.Index index, MathTransform transform) {
		Coordinate point = MGC.coord2Coordinate(node.getCoord());
		Coordinate transformedCoordinate = null;
		try {
			transformedCoordinate = JTS.transform(point, null, transform);
		} catch (TransformException e) {
			throw new RuntimeException(e);
		}
		boolean ret = !index.contains(MGC.coordinate2Coord(transformedCoordinate));
		return ret;
	}

	private static Corrections manualCorrections() {
		HashSet<Id<Link>> linksRemove = new HashSet<>();
		HashSet<Id<Link>> linksAdd = new HashSet<>();
		HashSet<String> remove = new HashSet<>();
		HashSet<String> add = new HashSet<>();

		// Martin-Luther-Straße
		remove.add("Hofjägerallee");
		remove.add("Klingelhöferstraße");
		remove.add("Lützowplatz");
		remove.add("Herkulesbrücke");
		remove.add("Schillstraße");
		remove.add("An der Urania");
		remove.add("Martin-Luther-Straße");

		//Dominicusstraße
		linksRemove.add(Id.createLinkId("28497757#0"));
		linksRemove.add(Id.createLinkId("397052774"));
		linksRemove.add(Id.createLinkId("172655509#0"));
		linksRemove.add(Id.createLinkId("172655510#0"));
		linksRemove.add(Id.createLinkId("28497759#0"));
		linksRemove.add(Id.createLinkId("4525780#0"));
		linksRemove.add(Id.createLinkId("28497755#0"));
		linksRemove.add(Id.createLinkId("529086046"));

		//ADD (!) Tiergarten Spreetunnel
		add.add("Tunnel Tiergarten Spreebogen");
		linksAdd.add(Id.createLinkId("4401925#0"));
		linksAdd.add(Id.createLinkId("139099142#0"));

		//Landsberger Allee
		remove.add("Landsberger Allee");
		remove.add("Platz der Vereinten Nationen");
		remove.add("Mollstraße");

		//Karl-Liebknecht-Straße
		linksRemove.add(Id.createLinkId("1105526112#0"));
		linksRemove.add(Id.createLinkId("318282850#0"));
		linksRemove.add(Id.createLinkId("24914141#5"));
		linksRemove.add(Id.createLinkId("1101404262#0"));
		linksRemove.add(Id.createLinkId("112128418#0"));
		linksRemove.add(Id.createLinkId("1101404262#0"));
		linksRemove.add(Id.createLinkId("1020548406"));

		//Prenzlauer Allee
		remove.add("Prenzlauer Allee");

		return new Corrections(linksRemove, linksAdd, remove, add);
	}

	private record Corrections(Set<Id<Link>> removeLinks, Set<Id<Link>> addLink, Set<String> removeStreet, Set<String> addStreet) {
	}
}
