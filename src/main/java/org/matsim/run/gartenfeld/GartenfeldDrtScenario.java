package org.matsim.run.gartenfeld;


import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.options.ShpOptions;
import org.matsim.contrib.drt.estimator.DrtEstimatorModule;
import org.matsim.contrib.drt.estimator.impl.DirectTripBasedDrtEstimator;
import org.matsim.contrib.drt.estimator.impl.distribution.NormalDistributionGenerator;
import org.matsim.contrib.drt.estimator.impl.trip_estimation.ConstantRideDurationEstimator;
import org.matsim.contrib.drt.estimator.impl.waiting_time_estimation.ShapeFileBasedWaitingTimeEstimator;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtModule;
import org.matsim.contrib.dvrp.passenger.PassengerRequestValidator;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeQSimModule;
import org.matsim.contrib.dvrp.run.DvrpModule;
import org.matsim.contrib.dvrp.run.DvrpQSimComponents;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ReplanningConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.utils.io.IOUtils;
import picocli.CommandLine;

import java.util.Collection;

/**
 * Scenario class for Gartenfeld DRT scenario.
 * <p>
 * Like its parent class GartenfeldScenario, this scenario has its own input files, which extend the OpenBerlin scenario files with inhabitants and road infrastructure specific to Gartenfeld.
 * See {@link org.matsim.prepare.gartenfeld.CreateGartenfeldComplete} for the creation of these input files.
 */
public class GartenfeldDrtScenario extends GartenfeldScenario {

	//	run params re drt are contained in separate class DrtOptions
	@CommandLine.ArgGroup(heading = "%nDrt options%n", exclusive = false, multiplicity = "0..1")
	private final DrtAndIntermodalityOptions drtOpt = new DrtAndIntermodalityOptions();

	@Override
	protected Config prepareConfig(Config config) {

//		apply all changes from base run class
//		this also includes changes from berlin v6.4
		super.prepareConfig(config);

//		apply necessary config changes related to drt and drt-pt intermodality
		drtOpt.configureDrtConfig(config);

//		changes for 1 it with intermodal drt
//		config.controller().setLastIteration(1);
//
//		Collection<ReplanningConfigGroup.StrategySettings> strategies = config.replanning().getStrategySettings();
//
//		for (ReplanningConfigGroup.StrategySettings s : strategies) {
//			if (s.getSubpopulation().equals(SUBPOP_PERSON)) {
//				switch (s.getStrategyName()) {
//					case DefaultPlanStrategiesModule.DefaultSelector.ChangeExpBeta:
//						s.setWeight(0.);
//						break;
//					case DefaultPlanStrategiesModule.DefaultStrategy.ReRoute:
//						s.setWeight(1.);
//						break;
//					case DefaultPlanStrategiesModule.DefaultStrategy.SubtourModeChoice:
//						s.setWeight(0.);
//						break;
//					case DefaultPlanStrategiesModule.DefaultStrategy.TimeAllocationMutator:
//						s.setWeight(0.);
//						break;
//					default: throw new IllegalStateException("Invalid replanning strategy named: " + s.getStrategyName());
//				}
//			}
//		}

		return config;
	}

	@Override
	public void prepareScenario(Scenario scenario) {
//		apply all scenario changes from base scenario class
		super.prepareScenario(scenario);

//		apply all necessary scenario changes for drt simulation
		drtOpt.configureDrtScenario(scenario);
	}

	@Override
	public void prepareControler(Controler controler) {
		Config config = controler.getConfig();

		Scenario scenario = controler.getScenario();
		Network network = scenario.getNetwork();
		ShpOptions shp = new ShpOptions(IOUtils.extendUrl(config.getContext(), drtOpt.getDrtAreaShp()).toString(), null, null);

//		apply all controller changes from base scenario class
		super.prepareControler(controler);

		controler.addOverridingModule(new DvrpModule());
		controler.addOverridingModule(new MultiModeDrtModule());

//		the following cannot be "experts only" (like requested from KN) because without it DRT would not work
//		here, the DynActivityEngine, PreplanningEngine + DvrpModule for each drt mode are added to the qsim components
//		this is necessary for drt / dvrp to work!
		// there is a syntax that can achieve the same thing but it does not need the "components". kai, jun'25
		controler.configureQSimComponents(DvrpQSimComponents.activateAllModes(ConfigUtils.addOrGetModule(controler.getConfig(), MultiModeDrtConfigGroup.class)));

		MultiModeDrtConfigGroup multiModeDrtConfigGroup = MultiModeDrtConfigGroup.get(config);
		for (DrtConfigGroup drtConfigGroup : multiModeDrtConfigGroup.getModalElements()) {
			controler.addOverridingModule(new AbstractModule() {
				@Override
				public void install() {
					DrtEstimatorModule.bindEstimator(binder(), drtConfigGroup.getMode()).toInstance(
						new DirectTripBasedDrtEstimator.Builder()
//							typical waiting time is set as minimal waiting time. it will only be applied if the typical waiting time of a service area is >= minimal waiting time.
							.setWaitingTimeEstimator(new ShapeFileBasedWaitingTimeEstimator(network, shp.readFeatures(), drtOpt.getTypicalWaitTime()))
							.setWaitingTimeDistributionGenerator(new NormalDistributionGenerator(1, drtOpt.getWaitTimeStd()))
							.setRideDurationEstimator(new ConstantRideDurationEstimator(drtOpt.getRideTimeAlpha(), drtOpt.getRideTimeBeta()))
							.setRideDurationDistributionGenerator(new NormalDistributionGenerator(2, drtOpt.getRideTimeStd()))
							.build()
					);
				}
			});

			// Overwrite the passenger request validator with the ShpBasedDrtRequestValidator
			controler.addOverridingQSimModule(new AbstractDvrpModeQSimModule(drtConfigGroup.getMode()) {
				@Override
				protected void configureQSim() {
					bindModal(PassengerRequestValidator.class).toProvider(
						modalProvider(getter -> new ShpBasedDrtRequestValidator(shp))).asEagerSingleton();
				}
			});
		}
	}

}
