package org.matsim.run.gartenfeld;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.application.options.ShpOptions;
import org.matsim.contrib.drt.estimator.DrtEstimatorParams;
import org.matsim.contrib.drt.optimizer.constraints.DrtOptimizationConstraintsParams;
import org.matsim.contrib.drt.optimizer.constraints.DrtOptimizationConstraintsSetImpl;
import org.matsim.contrib.drt.optimizer.insertion.extensive.ExtensiveInsertionSearchParams;
import org.matsim.contrib.drt.routing.DrtRoute;
import org.matsim.contrib.drt.routing.DrtRouteFactory;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.DrtConfigs;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.GeoFileWriter;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.pt.config.TransitRouterConfigGroup;
import org.matsim.run.prepare.PrepareTransitSchedule;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import picocli.CommandLine;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class bundles some run parameter options and functionalities connected to drt-scenarios.
 */
public class DrtAndIntermodalityOptions {
	private static final Logger log = LogManager.getLogger(DrtAndIntermodalityOptions.class);

	@CommandLine.Option(names = "--typ-wt", description = "typical waiting time (base)", defaultValue = "900")
	protected double typicalWaitTime;

	@CommandLine.Option(names = "--wt-std", description = "waiting time standard deviation", defaultValue = "0.3")
	protected double waitTimeStd;

	@CommandLine.Option(names = "--ride-time-alpha", description = "ride time estimator alpha", defaultValue = "1.")
	protected double rideTimeAlpha;

	@CommandLine.Option(names = "--ride-time-beta", description = "ride time estimator beta", defaultValue = "0.0")
	protected double rideTimeBeta;

	@CommandLine.Option(names = "--ride-time-std", description = "ride duration standard deviation", defaultValue = "0.3")
	protected double rideTimeStd;

	@CommandLine.Option(names = "--drt-shp", description = "Path to shp file for adding drt not network links as an allowed mode.", defaultValue = "../gartenfeld/v6.4/shp/drt-area/gartenfeld_paulsternstrasse_bhf_utm32n.shp")
	private String drtAreaShp;

	@CommandLine.Option(names = "--intermodal-shp", description = "Path to shp file for adding intermodal tags for drt to pt intermodality.", defaultValue = "../gartenfeld/v6.4/shp/intermodal-area/intermodal_area_gartenfeld_paulsternstrasse_bhf_utm32n.shp")
	private String intermodalAreaShp;

	@CommandLine.Option(names = "--intermodal", defaultValue = "ENABLED", description = "enable intermodality for DRT service")
	private GartenfeldUtils.FunctionalityHandling intermodal;

	@CommandLine.Option(names = "--drt-fare", defaultValue = "ENABLED", description = "enable fares for DRT service. The fare will be the same as for pt.")
	private GartenfeldUtils.FunctionalityHandling fareHandling;

	/**
	 * a helper method, which makes all necessary config changes to simulate drt.
	 */
	public void configureDrtConfig(Config config) {
		DvrpConfigGroup dvrpConfigGroup = ConfigUtils.addOrGetModule(config, DvrpConfigGroup.class);
		dvrpConfigGroup.setNetworkModes(Set.of(TransportMode.drt));

		MultiModeDrtConfigGroup multiModeDrtConfigGroup = ConfigUtils.addOrGetModule(config, MultiModeDrtConfigGroup.class);

		if (multiModeDrtConfigGroup.getModalElements().isEmpty()) {
			DrtConfigGroup drtConfigGroup = new DrtConfigGroup();
			drtConfigGroup.setOperationalScheme(DrtConfigGroup.OperationalScheme.serviceAreaBased);
			drtConfigGroup.setStopDuration(60.);
			drtConfigGroup.setDrtServiceAreaShapeFile(IOUtils.extendUrl(config.getContext(), getDrtAreaShp()).toString());

//			optimization params now are in its own paramSet, hence the below lines
			DrtOptimizationConstraintsParams optimizationConstraints = new DrtOptimizationConstraintsParams();
			DrtOptimizationConstraintsSetImpl optimizationConstraintsSet = new DrtOptimizationConstraintsSetImpl();
			optimizationConstraintsSet.setMaxWaitTime(1200.);
			optimizationConstraintsSet.setMaxTravelTimeBeta(1200.);
			optimizationConstraintsSet.setMaxTravelTimeAlpha(1.5);
			optimizationConstraints.addParameterSet(optimizationConstraintsSet);
//			set maxwalk distance to transit search radius. Drt is feeder for Pt.
			optimizationConstraintsSet.setMaxWalkDistance(ConfigUtils.addOrGetModule(config, TransitRouterConfigGroup.class).getSearchRadius());
			drtConfigGroup.addParameterSet(optimizationConstraints);
			drtConfigGroup.addParameterSet(new ExtensiveInsertionSearchParams());

//			drt estimator param set is necessary for newer matsim version than used in lausitzv2.0
			drtConfigGroup.addParameterSet(new DrtEstimatorParams());

			//			check if every feature of shp file has attr typ_wt for drt estimation. Add attr with standard value if not present
//			+ set new shp file as drtServiceAreaShapeFile
			checkServiceAreaShapeFile(config, drtConfigGroup);

			multiModeDrtConfigGroup.addDrtConfigGroup(drtConfigGroup);
		}

		// set to drt estimate and teleport
//		this enables the usage of the DrtEstimator by CL
		for (DrtConfigGroup drtConfigGroup : multiModeDrtConfigGroup.getModalElements()) {
			drtConfigGroup.setSimulationType(DrtConfigGroup.SimulationType.estimateAndTeleport);
		}

//		this is needed for DynAgents for DVRP
		config.qsim().setSimStarttimeInterpretation(QSimConfigGroup.StarttimeInterpretation.onlyUseStarttime);

		ScoringConfigGroup scoringConfigGroup = ConfigUtils.addOrGetModule(config, ScoringConfigGroup.class);

		if (!scoringConfigGroup.getModes().containsKey(TransportMode.drt)) {
//			add mode params for drt if missing and set ASC = pt + marg utility of traveling = 0 + daily mon constant = pt
			addDrtModeParamsBasedOnPtModeParams(scoringConfigGroup, getFareHandling());
		}

//		creates a drt staging activity and adds it to the scoring params
		DrtConfigs.adjustMultiModeDrtConfig(multiModeDrtConfigGroup, config.scoring(), config.routing());

		if (intermodal == GartenfeldUtils.FunctionalityHandling.ENABLED) {
			SwissRailRaptorConfigGroup srrConfig = ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class);

//			we need to configure walk-pt intermodality if it has not been done in base case.
			if (GartenfeldScenario.getExplicitWalkIntermodalityBaseCase() == GartenfeldUtils.FunctionalityHandling.DISABLED) {
				GartenfeldUtils.setExplicitIntermodalityParamsForWalkToPt(srrConfig );
			}
//			add drt as access egress mode for pt
			SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet accessEgressDrtParam = new SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet();
			accessEgressDrtParam.setMode(TransportMode.drt);
//			initial search radius same as for walk
			accessEgressDrtParam.setInitialSearchRadius(5000);
			accessEgressDrtParam.setMaxRadius(5000);
			accessEgressDrtParam.setSearchExtensionRadius(1000);
			accessEgressDrtParam.setStopFilterAttribute("allowDrtAccessEgress");
			accessEgressDrtParam.setStopFilterValue("true");
			srrConfig.addIntermodalAccessEgress(accessEgressDrtParam);
		}

//		add drt to mode choice
		List<String> modes = new ArrayList<>(List.of(config.subtourModeChoice().getModes()));
		modes.add(TransportMode.drt);

		config.subtourModeChoice().setModes(modes.toArray(new String[0]));
	}

	/**
	 * a helper method, which makes all necessary scenario changes to simulate drt.
	 */
	public void configureDrtScenario(Scenario scenario) {
		//		drt route factory has to be added as factory for drt routes, as there were no drt routes before.
		scenario.getPopulation()
			.getFactory()
			.getRouteFactories()
			.setRouteFactory(DrtRoute.class, new DrtRouteFactory());

//		prepare network for drt
//		preparation needs to be done with berlin shp not service area shp

		//		add drt as allowed mode for berlin
		Geometry geometry = new ShpOptions(IOUtils.extendUrl(scenario.getConfig().getContext(), "../gartenfeld/v6.4/shp/area_utm32n.shp").toString(), null, null).getGeometry();

//		with the estimator, drt is teleported, but we may need drt as an allowed mode for
//		separate drt post simulation
		for (Link link : scenario.getNetwork().getLinks().values()) {
			if (link.getAllowedModes().contains(TransportMode.car) || link.getId().toString().contains("DNG")) {
//				DNG links might not have car as an allowed mode because of central garage
				boolean isInside = MGC.coord2Point(link.getFromNode().getCoord()).within(geometry) ||
					MGC.coord2Point(link.getToNode().getCoord()).within(geometry);

				if (isInside) {
					Set<String> modes = new HashSet<>();
					modes.add(TransportMode.drt);
					modes.addAll(link.getAllowedModes());
					link.setAllowedModes(modes);
				}
			}
		}
		NetworkUtils.cleanNetwork(scenario.getNetwork(), Set.of(TransportMode.drt));


		//		add drt veh type if not already existing
		Id<VehicleType> drtTypeId = Id.create(TransportMode.drt, VehicleType.class);
		if (!scenario.getVehicles().getVehicleTypes().containsKey(drtTypeId)) {
//			drt veh type = car veh type, but capacity 1 passenger
			VehicleType drtType = VehicleUtils.createVehicleType(drtTypeId);

			VehicleUtils.copyFromTo(scenario.getVehicles().getVehicleTypes().get(Id.create(TransportMode.car, VehicleType.class)), drtType);
			drtType.setDescription("drt vehicle copied from car vehicle type");
			VehicleCapacity capacity = drtType.getCapacity();
			capacity.setSeats(1);

			scenario.getVehicles().addVehicleType(drtType);

			Vehicle drtDummy = VehicleUtils.createVehicle(Id.createVehicleId("drtDummy"), drtType);
			drtDummy.getAttributes().putAttribute("dvrpMode", TransportMode.drt);
			drtDummy.getAttributes().putAttribute("startLink", GartenfeldLinkChooser.accessLink.toString());
			drtDummy.getAttributes().putAttribute("serviceBeginTime", 0.);
			drtDummy.getAttributes().putAttribute("serviceEndTime", 86400.);

			scenario.getVehicles().addVehicle(drtDummy);
		}

		//			tag intermodal pt stops for intermodality between pt and drt
		if (intermodal == GartenfeldUtils.FunctionalityHandling.ENABLED) {
			PrepareTransitSchedule.tagIntermodalStops(scenario.getTransitSchedule(), new ShpOptions(IOUtils.extendUrl(scenario.getConfig().getContext(), intermodalAreaShp).toString(), null, null));
		}
	}

	/**
	 * Helper method to add drt mode params based on pt mode params.
	 */
	public void addDrtModeParamsBasedOnPtModeParams(ScoringConfigGroup scoringConfigGroup, GartenfeldUtils.FunctionalityHandling fareHandling) {
//		ASC drt = ASC pt as discussed in PHD seminar24
//		in this scenario pt pricing is done via daily monetary constant. see berlin 6.4 config
		scoringConfigGroup.addModeParams(new ScoringConfigGroup.ModeParams(TransportMode.drt)
			.setConstant(scoringConfigGroup.getModes().get(TransportMode.pt).getConstant())
			.setMarginalUtilityOfTraveling(-0.));

		if (fareHandling == GartenfeldUtils.FunctionalityHandling.ENABLED) {
//			if we charge (pt/drt) fare via daily constant, we should always set daily constant for drt=0.
//			drt is only access/egress to/from Paulsternstr. Hence, the fare is already paid with the daily constant of pt.
			scoringConfigGroup.getModes().get(TransportMode.drt).setDailyMonetaryConstant(0.);
		} else {
			scoringConfigGroup.getModes().get(TransportMode.drt).setDailyMonetaryConstant(0.);
		}
	}

	private void checkServiceAreaShapeFile(Config config, DrtConfigGroup drtConfigGroup) {
		ShpOptions shp = new ShpOptions(IOUtils.extendUrl(config.getContext(), getDrtAreaShp()).toString(), null, null);
		List<SimpleFeature> features = shp.readFeatures();
		List<SimpleFeature> newFeatures = new ArrayList<>();
		boolean adapted = false;
		for (SimpleFeature feature : features) {
			if (feature.getAttribute("typ_wt") == null) {
				SimpleFeatureType existingFeatureType = feature.getFeatureType();

				SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
				builder.init(existingFeatureType);

				builder.add("typ_wt", Double.class);
				SimpleFeatureType newFeatureType = builder.buildFeatureType();

				SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(newFeatureType);

				List<Object> existingAttributes = feature.getAttributes();
				featureBuilder.addAll(existingAttributes);
				featureBuilder.add(10 * 60.);

				// Step 7: Build the new feature with a unique ID (same geometry, updated attributes)
				SimpleFeature newFeature = featureBuilder.buildFeature(feature.getID());
				newFeatures.add(newFeature);
				adapted = true;
			} else {
				newFeatures.add(feature);
			}
		}

		if (adapted) {
			String newServiceAreaPath;
			try {
				File file = new File(Path.of(IOUtils.extendUrl(config.getContext(), getDrtAreaShp()).toURI()).getParent().toString(),
					Path.of(IOUtils.extendUrl(config.getContext(), getDrtAreaShp()).toURI()).getFileName().toString().split(".shp")[0] + "-with-waiting-time.shp");
				newServiceAreaPath = file.getAbsolutePath();
			} catch (URISyntaxException e) {
				throw new IllegalArgumentException("Error handling the Drt service area shapefile URI.", e);
			}


			log.warn("For drt service area shape file {}, at least one feature did not have the obligatory attribute typ_wt. " +
				"The attribute is needed for drt estimation. The attribute was added with a standard value of 10min for those features " +
				"and saved to file {}.", IOUtils.extendUrl(config.getContext(), getDrtAreaShp()), newServiceAreaPath);

			GeoFileWriter.writeGeometries(newFeatures, newServiceAreaPath);
			drtConfigGroup.setDrtServiceAreaShapeFile(newServiceAreaPath);
		}
	}

	/**
	 * get der service area shp file.
	 */
	public String getDrtAreaShp() {
		return drtAreaShp;
	}

	/**
	 * get typical waiting time for drt estimator.
	 */
	public double getTypicalWaitTime() {
		return typicalWaitTime;
	}

	/**
	 * get waiting time standard deviation for drt estimator.
	 */
	public double getWaitTimeStd() {
		return waitTimeStd;
	}

	/**
	 * get alpha for ride time estimation for drt estimator.
	 */
	public double getRideTimeAlpha() {
		return rideTimeAlpha;
	}

	/**
	 * get beta for ride time estimation for drt estimator.
	 */
	public double getRideTimeBeta() {
		return rideTimeBeta;
	}

	/**
	 * get ride time standard deviation for drt estimator.
	 */
	public double getRideTimeStd() {
		return rideTimeStd;
	}

	/**
	 * get drt fare handling parameter.
	 */
	public GartenfeldUtils.FunctionalityHandling getFareHandling() {
		return fareHandling;
	}
}
