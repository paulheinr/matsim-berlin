package org.matsim.run.policies;

import com.google.common.collect.ImmutableSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.contrib.drt.estimator.DrtEstimatorModule;
import org.matsim.contrib.drt.estimator.impl.DirectTripBasedDrtEstimator;
import org.matsim.contrib.drt.estimator.impl.distribution.NormalDistributionGenerator;
import org.matsim.contrib.drt.estimator.impl.trip_estimation.ConstantRideDurationEstimator;
import org.matsim.contrib.drt.estimator.impl.waiting_time_estimation.ConstantWaitingTimeEstimator;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.extensions.pt.fare.intermodalTripFareCompensator.IntermodalTripFareCompensatorConfigGroup;
import org.matsim.extensions.pt.fare.intermodalTripFareCompensator.IntermodalTripFareCompensatorsConfigGroup;
import org.matsim.extensions.pt.fare.intermodalTripFareCompensator.IntermodalTripFareCompensatorsModule;
import org.matsim.run.OpenBerlinDrtScenario;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import picocli.CommandLine;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

/**
 * Berlin scenario including estimated drt.
 * This class uses the changes made in OpenBerlinDrtScenario and changes some of them.
 * All necessary configs will be made in this class.
 */
public class OpenBerlinDrtEstimatorScenario extends OpenBerlinDrtScenario {
	Logger log = LogManager.getLogger(OpenBerlinDrtEstimatorScenario.class);

	@CommandLine.Option(names = "--typ-wt", description = "typical waiting time (base)", defaultValue = "300")
	protected double typicalWaitTime;

	@CommandLine.Option(names = "--wt-std", description = "waiting time standard deviation", defaultValue = "0.3")
	protected double waitTimeStd;

	//		ride time alpha + beta for pooled drt service from below paper. See Table 1.
	//	https://api-depositonce.tu-berlin.de/server/api/core/bitstreams/82f8e8b5-7c7c-4bf2-a636-5b8b1ab7fe1d/content
	@CommandLine.Option(names = "--ride-time-alpha", description = "ride time estimator alpha", defaultValue = "1.5")
	protected double rideTimeAlpha;

	@CommandLine.Option(names = "--ride-time-beta", description = "ride time estimator beta", defaultValue = "360")
	protected double rideTimeBeta;

	@CommandLine.Option(names = "--ride-time-std", description = "ride duration standard deviation", defaultValue = "0.3")
	protected double rideTimeStd;

	@CommandLine.Option(names = "--intermodal", defaultValue = "DRT_REGULAR_AND_INTERMODAL", description = "INTERMODAL_DRT_ONLY: Drt can only be used for access/egress to PT. DRT_REGULAR_AND_INTERMODAL: Drt used for intermodal access/egress to PT and as separate mode.")
	private DrtIntermodalityHandling intermodal;

	@CommandLine.Option(names = "--drt-fare", description = "Daily drt fare to be charged for drt trips. Default = -3Eu := same as PT", defaultValue = "-3.0")
	private double drtFare;

	@Nullable
	@Override
	public Config prepareConfig(Config config) {
		//		apply all config changes from base scenario class
//		plus all changes from OpenBerlinDrtScenario. Some of them will be overwritten in the following.
		super.prepareConfig(config);

		//modify output directory and runId
		config.controller().setOutputDirectory(config.controller().getOutputDirectory() + "-alpha-" + rideTimeAlpha + "-beta-" + rideTimeBeta + "-fare-" + drtFare);
		config.controller().setRunId(config.controller().getRunId() + "-alpha-" + rideTimeAlpha + "-beta-" + rideTimeBeta + "-fare-" + drtFare);

		MultiModeDrtConfigGroup multiModeDrtConfigGroup = ConfigUtils.addOrGetModule(config, MultiModeDrtConfigGroup.class);

//		we want to estimate drt, so we do not need the pre-defined vehicles file
		multiModeDrtConfigGroup.getModalElements().forEach(e -> {
			e.vehiclesFile = null;
			e.drtServiceAreaShapeFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v6.4/input/shp/Berlin_25832.shp";
		});

		if (intermodal == DrtIntermodalityHandling.INTERMODAL_DRT_ONLY) {
			//		remove drt from mode choice
			Set<String> modes = new HashSet<>(Set.of(config.subtourModeChoice().getModes()));
			modes.remove(TransportMode.drt);
			config.subtourModeChoice().setModes(modes.toArray(new String[0]));
		}

		// set to drt estimate and teleport
//		this enables the usage of the DrtEstimator by CL
		for (DrtConfigGroup drtConfigGroup : multiModeDrtConfigGroup.getModalElements()) {
			drtConfigGroup.simulationType = DrtConfigGroup.SimulationType.estimateAndTeleport;
		}

		config.removeModule("");

		return config;
	}

	@Override
	public void prepareScenario(Scenario scenario) {
		//		apply all scenario changes from base scenario class
		super.prepareScenario(scenario);

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
			drtDummy.getAttributes().putAttribute("startLink", "1119935543");
			drtDummy.getAttributes().putAttribute("serviceBeginTime", 0.);
			drtDummy.getAttributes().putAttribute("serviceEndTime", 86400.);

			scenario.getVehicles().addVehicle(drtDummy);
		}
	}

	@Override
	public void prepareControler(Controler controler) {
		//		apply all controller changes from base scenario class
		super.prepareControler(controler);

		for (DrtConfigGroup drtConfigGroup : MultiModeDrtConfigGroup.get(controler.getConfig()).getModalElements()) {
			controler.addOverridingModule(new AbstractModule() {
				@Override
				public void install() {
					DrtEstimatorModule.bindEstimator(binder(), drtConfigGroup.mode).toInstance(
						new DirectTripBasedDrtEstimator.Builder()
//							typical waiting time is set as minimal waiting time. it will only be applied if the typical waiting time of a service area is >= minimal waiting time.
							.setWaitingTimeEstimator(new ConstantWaitingTimeEstimator(typicalWaitTime))
							.setWaitingTimeDistributionGenerator(new NormalDistributionGenerator(1, waitTimeStd))
							.setRideDurationEstimator(new ConstantRideDurationEstimator(rideTimeAlpha, rideTimeBeta))
							.setRideDurationDistributionGenerator(new NormalDistributionGenerator(2, rideTimeStd))
							.build()
					);
				}
			});
		}
	}

	/**
	 * this method overrides the addIntermodalTripFareCompensatorsModule method in OpenBerlinDrtScenario (parent class).
	 * we only want to use intermodal trip fare compensation if a drt fare is charged at all.
	 */
	@Override
	public void addIntermodalTripFareCompensatorsModule(Controler controler) {
//		we do not need intermodal trip fare compensation when drt has no fare
		if (drtFare != 0.) {
			controler.addOverridingModule(new IntermodalTripFareCompensatorsModule());
		}
	}

	/**
	 * this method overrides the configureIntermodalTripFareCompensation method in OpenBerlinDrtScenario (parent class).
	 * we only want to use intermodal trip fare compensation if a drt fare is charged at all.
	 */
	@Override
	public void configureIntermodalTripFareCompensation(Config config, ScoringConfigGroup.ModeParams ptParams, Set<String> drtModes) {
		//		we do not need intermodal trip fare compensation when drt has no fare
		if (drtFare != 0.) {
			IntermodalTripFareCompensatorsConfigGroup compensatorsConfig = ConfigUtils.addOrGetModule(config, IntermodalTripFareCompensatorsConfigGroup.class);

			//assume that (all) the drt is fully integrated in pt, i.e. fare integration
			IntermodalTripFareCompensatorConfigGroup drtCompensationCfg = new IntermodalTripFareCompensatorConfigGroup();
			drtCompensationCfg.setCompensationCondition(IntermodalTripFareCompensatorConfigGroup.CompensationCondition.PtModeUsedAnywhereInTheDay);
			drtCompensationCfg.setCompensationMoneyPerDay(drtFare);
			drtCompensationCfg.setNonPtModes(ImmutableSet
				.<String>builder()
				.addAll(drtModes)
				.build());
			compensatorsConfig.addParameterSet(drtCompensationCfg);
		}
	}

	/**
	 * Helper enum to enable/disable functionalities.
	 */
	private enum DrtIntermodalityHandling {INTERMODAL_DRT_ONLY, DRT_REGULAR_AND_INTERMODAL}
}
