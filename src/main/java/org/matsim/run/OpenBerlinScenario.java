package org.matsim.run;

import com.google.inject.Key;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import org.jetbrains.annotations.Nullable;
import org.matsim.analysis.QsimTimingModule;
import org.matsim.analysis.personMoney.PersonMoneyEventsAnalysisModule;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.application.MATSimApplication;
import org.matsim.application.options.SampleOptions;
import org.matsim.contrib.bicycle.*;
import org.matsim.contrib.emissions.OsmHbefaMapping;
import org.matsim.contrib.emissions.utils.EmissionsConfigGroup;
import org.matsim.contrib.vsp.scoring.RideScoringParamsFromCarParams;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ReplanningConfigGroup;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule.DefaultSelector;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule.DefaultStrategy;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutilityFactory;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;
import org.matsim.run.scoring.AdvancedScoringConfigGroup;
import org.matsim.run.scoring.AdvancedScoringModule;
import org.matsim.simwrapper.SimWrapperConfigGroup;
import org.matsim.simwrapper.SimWrapperModule;
import picocli.CommandLine;
import playground.vsp.scoring.IncomeDependentUtilityOfMoneyPersonScoringParameters;

import java.util.List;

import static org.matsim.core.config.groups.ReplanningConfigGroup.*;

@CommandLine.Command(header = ":: Open Berlin Scenario ::", version = OpenBerlinScenario.VERSION, mixinStandardHelpOptions = true, showDefaultValues = true)
public class OpenBerlinScenario extends MATSimApplication {

	public static final String VERSION = "6.4";
	public static final String CRS = "EPSG:25832";
	public static final String SUBPOP_PERSON = "person";
	public static final String SUBPOP_FREIGHT = "freight";
	public static final String SUBPOP_GOODS = "goodsTraffic";
	public static final String SUBPOP_COM_PERSON = "commercialPersonTraffic";
	public static final String SUBPOP_COM_PERSON_SERVICE = "commercialPersonTraffic_service";

	//	To decrypt hbefa input files set MATSIM_DECRYPTION_PASSWORD as environment variable. ask VSP for access.
	private static final String HBEFA_2020_PATH = "https://svn.vsp.tu-berlin.de/repos/public-svn/3507bb3997e5657ab9da76dbedbb13c9b5991d3e/0e73947443d68f95202b71a156b337f7f71604ae/";
	private static final String HBEFA_FILE_COLD_DETAILED = HBEFA_2020_PATH + "82t7b02rc0rji2kmsahfwp933u2rfjlkhfpi2u9r20.enc";
	private static final String HBEFA_FILE_WARM_DETAILED = HBEFA_2020_PATH + "944637571c833ddcf1d0dfcccb59838509f397e6.enc";
	private static final String HBEFA_FILE_COLD_AVERAGE = HBEFA_2020_PATH + "r9230ru2n209r30u2fn0c9rn20n2rujkhkjhoewt84202.enc" ;
	private static final String HBEFA_FILE_WARM_AVERAGE = HBEFA_2020_PATH + "7eff8f308633df1b8ac4d06d05180dd0c5fdf577.enc";

//	@CommandLine.Mixin
//	private final SampleOptions sample = new SampleOptions(10, 25, 3, 1);
	// no longer allowed. kai, feb'26

	@CommandLine.Option(names = "--plan-selector", description = "Plan selector to use.", defaultValue = DefaultSelector.ChangeExpBeta)
	private String planSelector;

	public OpenBerlinScenario() {
		super(String.format("input/v%s/berlin-v%s.config.xml", VERSION, VERSION));
	}

	public OpenBerlinScenario(@Nullable String defaultConfigPath) {
		super(defaultConfigPath);
	}

	public static void main(String[] args) {
		MATSimApplication.run(OpenBerlinScenario.class, args);
	}

	@Override
	protected Config prepareConfig(Config config) {
		// yy consistency of scale factors is tested in config consistency. kai, feb'26

//		SimWrapperConfigGroup simwrapperConfig = ConfigUtils.addOrGetModule(config, SimWrapperConfigGroup.class);

//		if (sample.isSet()) {
//			double sampleSize = sample.getSample();
//
//			config.qsim().setFlowCapFactor(sampleSize);
//			config.qsim().setStorageCapFactor(sampleSize);
//
//			// Counts can be scaled with sample size
//			config.counts().setCountsScaleFactor(sampleSize);
//			simwrapperConfig.setSampleSize(sampleSize);
//
//			config.controller().setRunId(sample.adjustName(config.controller().getRunId()));
//			config.controller().setOutputDirectory(sample.adjustName(config.controller().getOutputDirectory()));
//			config.plans().setInputFile(sample.adjustName(config.plans().getInputFile()));
//		}
		///  There are now scale consistency checks (using the flow capacity as the reference value) in {@link org.matsim.core.config.groups.GlobalConfigGroup} and in {@link SimWrapperConfigGroup}.
		///  We have also decided to have separate config files for separate sample sizes, since those normally also need different ASCs.
		///  Since these are then different runs, we are also no longer re-writing file and directory names.
		///  kai, feb'26

		config.qsim().setUsingTravelTimeCheckInTeleportation(true);

		// overwrite ride scoring params with values derived from car
		RideScoringParamsFromCarParams.setRideScoringParamsBasedOnCarParams(config.scoring(), 1.0);

		//		the following needs to be in a separate method because we want to override the method in GartenfeldScenario
		// (not sure if I like this as a design.  kai, feb'25)
		configureActivityScoringParams(config);

//		add all necessary replanning strategies for all subpops
		// Required for all calibration strategies
		final ReplanningConfigGroup replanning = config.replanning();
		for (String subpopulation : List.of(SUBPOP_PERSON, SUBPOP_FREIGHT, SUBPOP_GOODS, SUBPOP_COM_PERSON, SUBPOP_COM_PERSON_SERVICE )) {
			replanning.addStrategySettings( new StrategySettings().setStrategyName(planSelector).setWeight(1.0).setSubpopulation(subpopulation) );
			replanning.addStrategySettings( new StrategySettings().setStrategyName( DefaultStrategy.ReRoute ).setWeight(0.15).setSubpopulation(subpopulation) );
		}
		replanning.addStrategySettings( new StrategySettings().setStrategyName( DefaultStrategy.TimeAllocationMutator ).setWeight(0.15).setSubpopulation(SUBPOP_PERSON) );
		replanning.addStrategySettings( new StrategySettings().setStrategyName( DefaultStrategy.SubtourModeChoice ).setWeight(0.15).setSubpopulation(SUBPOP_PERSON) );

		// Need to switch to warning for best score
		if (planSelector.equals( DefaultSelector.BestScore )) {
			config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.warn);
		}

		// Bicycle config must be present
		ConfigUtils.addOrGetModule(config, BicycleConfigGroup.class);

		// Add emissions configuration
		EmissionsConfigGroup eConfig = ConfigUtils.addOrGetModule(config, EmissionsConfigGroup.class);
		eConfig.setDetailedColdEmissionFactorsFile(HBEFA_FILE_COLD_DETAILED);
		eConfig.setDetailedWarmEmissionFactorsFile(HBEFA_FILE_WARM_DETAILED);
		eConfig.setAverageColdEmissionFactorsFile(HBEFA_FILE_COLD_AVERAGE);
		eConfig.setAverageWarmEmissionFactorsFile(HBEFA_FILE_WARM_AVERAGE);
		eConfig.setHbefaTableConsistencyCheckingLevel(EmissionsConfigGroup.HbefaTableConsistencyCheckingLevel.consistent);
		eConfig.setDetailedVsAverageLookupBehavior(EmissionsConfigGroup.DetailedVsAverageLookupBehavior.tryDetailedThenTechnologyAverageThenAverageTable);
		eConfig.setEmissionsComputationMethod(EmissionsConfigGroup.EmissionsComputationMethod.StopAndGoFraction);
		// (yy replace by chained setters once available)

		return config;
	}

	/**
	 * overridable method for adding activity scoring params.
	 */
	protected void configureActivityScoringParams(Config config) {
		Activities.addScoringParams(config, true);
	}

	@Override
	protected void prepareScenario(Scenario scenario) {
		OsmHbefaMapping.build().addHbefaMappings(scenario.getNetwork() ); // add hbefa link attributes
	}

	@Override
	protected void prepareControler(Controler controler) {
		controler.addOverridingModule(new SimWrapperModule());
		controler.addOverridingModule(new BerlinTravelTimeBinding() );
		controler.addOverridingModule(new QsimTimingModule());

		// AdvancedScoring is specific to matsim-berlin!
		if (ConfigUtils.hasModule(controler.getConfig(), AdvancedScoringConfigGroup.class)) {
			controler.addOverridingModule(new AdvancedScoringModule());
			controler.getConfig().scoring().setExplainScores(true);
		} else {
			// if the above config group is not present we still need income dependent scoring
			// this implementation also allows for person specific asc
			controler.addOverridingModule(new AbstractModule() {
				@Override
				public void install() {
					bind(ScoringParametersForPerson.class).to(IncomeDependentUtilityOfMoneyPersonScoringParameters.class).in( Singleton.class );
				}
			});
		}
		controler.addOverridingModule(new PersonMoneyEventsAnalysisModule());
	}

	/**
	 * Add travel time bindings for ride and freight modes.
	 */
	public static final class BerlinTravelTimeBinding extends AbstractModule {

		private final boolean carOnly;

		public BerlinTravelTimeBinding() {
			this.carOnly = false;
		}

		public BerlinTravelTimeBinding( boolean carOnly ) {
			this.carOnly = carOnly;
		}

		@Override
		public void install() {
			addTravelTimeBinding(TransportMode.ride).to( carTravelTime() );
			addTravelDisutilityFactoryBinding(TransportMode.ride).to(carTravelDisutilityFactoryKey());

			if (!carOnly) {
				addTravelTimeBinding("freight").to(Key.get(TravelTime.class, Names.named(TransportMode.truck)));
				addTravelDisutilityFactoryBinding("freight").to(Key.get(TravelDisutilityFactory.class, Names.named(TransportMode.truck)));

				bind(BicycleLinkSpeedCalculator.class).to(BicycleLinkSpeedCalculatorDefaultImpl.class);
//				I do not know why the following binding is needed here, because BicycleParamsDefaultImpl is bound in BicycleModule, but the controller is complaining.
//				so I added it here. -sm0226
				// The BicycleModule is nowhere bound.  kai, feb'26
				bind(BicycleParams.class).to(BicycleParamsDefaultImpl.class);

				// Bike should use free speed travel time
				addTravelTimeBinding(TransportMode.bike).to(BicycleTravelTime.class);
				addTravelDisutilityFactoryBinding(TransportMode.bike).to(OnlyTimeDependentTravelDisutilityFactory.class);
			}
		}
	}

}
