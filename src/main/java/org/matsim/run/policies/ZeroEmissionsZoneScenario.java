package org.matsim.run.policies;

import com.google.inject.Key;
import com.google.inject.name.Names;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.MATSimApplication;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.network.algorithms.MultimodalNetworkCleaner;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.algorithms.PermissibleModesCalculator;
import org.matsim.core.router.MultimodalLinkChooser;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.run.OpenBerlinScenario;
import org.matsim.utils.gis.shp2matsim.ShpGeometryUtils;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import picocli.CommandLine;

import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This scenario class extends the OpenBerlinScenario by the functionality to create a zero-emissions-zone,
 * meaning that combustion engine vehicles (cev) are forbidden to enter and drive.
 */
public class ZeroEmissionsZoneScenario extends OpenBerlinScenario {

    public static final String ELECTRIC_CAR = "electric_car";
    public static final String ELECTRIC_TRUCK = "electric_truck";
    public static final String ELECTRIC_RIDE = "electric_ride";
    public static final String ELECTRIC_FREIGHT = "electric_freight";
    private static final Logger log = LogManager.getLogger(ZeroEmissionsZoneScenario.class);

    @CommandLine.Option(names = "--area",
            defaultValue = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v6.4/input/shp/car-ban-area/hundekopf-carBanArea-25832.shp",
            description = "Path to (single geom) shape file depicting the area where combustion engines are banned from!")
    private static String URL_2_ZEZ_SINGLE_GEOM_SHAPE_FILE;

    @CommandLine.Option(names = "--cev-road-types",
            defaultValue = "motorway",
            description = "Can be one of [nowhere, motorway, motorwayAndPrimaryAndTrunk]. Determines the type of roads inside in the ban area, where combustion engine vehicles are allowed to drive.")
    private static CEVAllowedOnRoadTypesInsideBanArea ROAD_TYPES_CAR_ALLOWED;

    @CommandLine.Option(names = "--ev-cost-constant-factor",
            defaultValue = "1.5",
            description = "Multiplier for the daily monetary constant of electric vehicle modes compared to their combustion engine counterparts.")
    private static double EV_COST_CONSTANT_FACTOR;

    @CommandLine.Option(names = "--ev-cost-distance-factor",
            defaultValue = "0.8",
            description = "Multiplier for the monetary distance rate of electric vehicle modes compared to their combustion engine counterparts.")
    private static double EV_COST_DISTANCE_FACTOR;



    private enum CEVAllowedOnRoadTypesInsideBanArea {
        nowhere, motorway, motorwayAndPrimaryAndTrunk
    }

    public static void main(String[] args) {
        MATSimApplication.run(ZeroEmissionsZoneScenario.class, args);
    }

    @Override
    protected Config prepareConfig(Config config) {
        config = super.prepareConfig(config);

        //add mode parameters for electric modes
        prepareModeParams(config);

        //add electric modes to the mode choice set
        List<String> modeChoiceModes = new ArrayList<>(List.of(config.subtourModeChoice().getModes()));
        modeChoiceModes.add(ELECTRIC_CAR);
        modeChoiceModes.add(ELECTRIC_RIDE);
        config.subtourModeChoice().setModes(modeChoiceModes.toArray(new String[0]));

        //add electric car to the chain-based modes
        List<String> chainBasedModes = new ArrayList<>(List.of(config.subtourModeChoice().getChainBasedModes()));
        chainBasedModes.add(ELECTRIC_CAR);
        config.subtourModeChoice().setChainBasedModes(chainBasedModes.toArray(new String[0]));

        //add electric modes to the QSim main modes
        config.qsim().getMainModes().add(ELECTRIC_CAR);
        config.qsim().getMainModes().add(ELECTRIC_TRUCK);
        config.qsim().getMainModes().add(ELECTRIC_RIDE);

        //add electric modes to the routing network modes
        List<String> networkModes = new ArrayList<>(config.routing().getNetworkModes());
        networkModes.add(ELECTRIC_CAR);
        networkModes.add(ELECTRIC_TRUCK);
        networkModes.add(ELECTRIC_RIDE);
        config.routing().setNetworkModes(networkModes);

        return config;
    }

    private static void prepareModeParams(Config config) {
        ScoringConfigGroup.ModeParams electricCarParams = new ScoringConfigGroup.ModeParams(ELECTRIC_CAR);
        ScoringConfigGroup.ModeParams carParams = config.scoring().getModes().get(TransportMode.car);
        //copy all scoring params from car
        copyModeParams(carParams, electricCarParams);
        electricCarParams.setDailyMonetaryConstant(carParams.getDailyMonetaryConstant() * EV_COST_CONSTANT_FACTOR); //TODO adjust parametrization
        electricCarParams.setMonetaryDistanceRate(carParams.getMonetaryDistanceRate() * EV_COST_DISTANCE_FACTOR); //TODO adjust parametrization
        config.scoring().addModeParams(electricCarParams);

        ScoringConfigGroup.ModeParams electricRideParams = new ScoringConfigGroup.ModeParams(ELECTRIC_RIDE);
        ScoringConfigGroup.ModeParams rideParams = config.scoring().getModes().get(TransportMode.ride);
        //copy all scoring params from ride
        copyModeParams(rideParams, electricRideParams);
        electricRideParams.setDailyMonetaryConstant(rideParams.getDailyMonetaryConstant() * EV_COST_CONSTANT_FACTOR); //TODO adjust parametrization
        electricRideParams.setMonetaryDistanceRate(rideParams.getMonetaryDistanceRate() * EV_COST_DISTANCE_FACTOR); //TODO adjust parametrization
        config.scoring().addModeParams(electricRideParams);

        ScoringConfigGroup.ModeParams electricTruckParams = new ScoringConfigGroup.ModeParams(ELECTRIC_TRUCK);
        ScoringConfigGroup.ModeParams truckParams = config.scoring().getModes().get(TransportMode.truck);
        //copy all scoring params from truck
        copyModeParams(truckParams, electricTruckParams);
//        electricTruckParams.setDailyMonetaryConstant(truckParams.getConstant() * 1.5); //base is zero! --> no mode choice
        electricTruckParams.setMonetaryDistanceRate(truckParams.getMonetaryDistanceRate() * 0.8); //TODO adjust parametrization
        config.scoring().addModeParams(electricTruckParams);

        ScoringConfigGroup.ModeParams electricFreightParams = new ScoringConfigGroup.ModeParams(ELECTRIC_FREIGHT);
        ScoringConfigGroup.ModeParams freightParams = config.scoring().getModes().get("freight");
        //copy all scoring params from freight
        copyModeParams(freightParams, electricFreightParams);
//        electricTruckParams.setDailyMonetaryConstant(freightParams.getConstant() * 1.5); //base is zero! --> no mode choice
        electricFreightParams.setMonetaryDistanceRate(freightParams.getMonetaryDistanceRate() * 0.8); //TODO adjust parametrization
        config.scoring().addModeParams(electricFreightParams);
    }

    @Override
    protected void prepareScenario(Scenario scenario) {

        super.prepareScenario(scenario);

        //TODO: alternatively, read in dedicated vehicles file for policy case (i.e. don't make changes in the code) ???
        addElectricVehiclesToNetwork(scenario);

        //ban combustion engine vehicles from the defined area and delete all routes that touch the forbidden links
        banModesFromNetworkArea(scenario,
                IOUtils.resolveFileOrResource(URL_2_ZEZ_SINGLE_GEOM_SHAPE_FILE),
                Set.of(TransportMode.car, TransportMode.ride, TransportMode.truck, "freight"));

//        new NetworkWriter(scenario.getNetwork()).write("output/network-with-zez.xml.gz");
//        log.info("network written");

        prepareElectricVehicleTypes(scenario);
    }

    private static void prepareElectricVehicleTypes(Scenario scenario) {
        VehicleType carVehicleType = scenario.getVehicles().getVehicleTypes().get(Id.create(TransportMode.car, VehicleType.class));
        VehicleType electricCarType = VehicleUtils.createVehicleType(Id.create(ELECTRIC_CAR, VehicleType.class), ELECTRIC_CAR);
        copyVehicleTypeInformation(carVehicleType, electricCarType);

        VehicleType rideVehicleType = scenario.getVehicles().getVehicleTypes().get(Id.create(TransportMode.ride, VehicleType.class));
        VehicleType electricRideType = VehicleUtils.createVehicleType(Id.create(ELECTRIC_RIDE, VehicleType.class), ELECTRIC_RIDE);
        copyVehicleTypeInformation(rideVehicleType, electricRideType);

        VehicleType freightVehicleType = scenario.getVehicles().getVehicleTypes().get(Id.create("freight", VehicleType.class));
        VehicleType electricFreightType = VehicleUtils.createVehicleType(Id.create(ELECTRIC_FREIGHT, VehicleType.class), ELECTRIC_TRUCK);
        copyVehicleTypeInformation(freightVehicleType, electricFreightType);

        VehicleType truckVehicleType = scenario.getVehicles().getVehicleTypes().get(Id.create(TransportMode.truck, VehicleType.class));
        VehicleType electricTruckType = VehicleUtils.createVehicleType(Id.create(ELECTRIC_TRUCK, VehicleType.class), ELECTRIC_TRUCK);
        copyVehicleTypeInformation(truckVehicleType, electricTruckType);

        //now add the electric vehicle types to the scenario
        scenario.getVehicles().addVehicleType(electricCarType);
        scenario.getVehicles().addVehicleType(electricRideType);
        scenario.getVehicles().addVehicleType(electricTruckType);
        scenario.getVehicles().addVehicleType(electricFreightType);
    }

    private static void copyVehicleTypeInformation(VehicleType combustionEngingeVehicleType, VehicleType electricVehicleType) {
        //copy engine information from CEV to electric vehicle type
        combustionEngingeVehicleType.getEngineInformation().getAttributes().getAsMap().forEach((k, v) -> electricVehicleType.getEngineInformation().getAttributes().putAttribute(k,v));
        //then mark as electric
        electricVehicleType.getEngineInformation().getAttributes().putAttribute("HbefaEmissionsConcept", "electric");
        //now copy the cost information and adjust
        // -> this is only used for commercial transport I (ts) think. Thus, we leave it as is for now.
        //TODO double check if the above comment is true
//        if( combustionEngingeVehicleType.getCostInformation() != null){
//
//            electricVehicleType.getCostInformation().setCostsPerMeter(combustionEngingeVehicleType.getCostInformation().getCostsPerMeter() * 0.8); //
//            electricVehicleType.getCostInformation().setFixedCost(combustionEngingeVehicleType.getCostInformation().getFixedCosts() * 1.5); //
//            electricVehicleType.getCostInformation().setCostsPerSecond(combustionEngingeVehicleType.getCostInformation().getCostsPerSecond()); //
//            combustionEngingeVehicleType.getCostInformation().getAttributes().getAsMap().forEach((k, v) -> electricVehicleType.getCostInformation().getAttributes().putAttribute(k,v));
//        }

        //copy the rest of the vehicle type information
        electricVehicleType.setMaximumVelocity(combustionEngingeVehicleType.getMaximumVelocity());
        electricVehicleType.setFlowEfficiencyFactor(combustionEngingeVehicleType.getFlowEfficiencyFactor());
        electricVehicleType.setPcuEquivalents(combustionEngingeVehicleType.getPcuEquivalents());
    }

    @Override
    protected void prepareControler(Controler controler) {
        super.prepareControler(controler);

        controler.addOverridingModule(new ElectricTravelTimeBinding());

        //we need to override the PermissibleModesCalculator to account for electric vehicles
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                bind(PermissibleModesCalculator.class).to(ZEZPermissibleModesCalculator.class);
                bind(MultimodalLinkChooser.class).to(CarfreeMultimodalLinkChooser.class);
            }
        });
    }

    private static void addElectricVehiclesToNetwork(Scenario scenario) {
        // add electric vehicles to the network. Currently, we use the car network as proxy, but we could do this separately for trucks and freight as well.
        scenario.getNetwork().getLinks().values().forEach(link -> {
            Set<String> allowedModes = new HashSet<>(link.getAllowedModes());
            if(allowedModes.contains(TransportMode.car)){
                allowedModes.add(ELECTRIC_CAR);
                allowedModes.add(ELECTRIC_TRUCK);
                allowedModes.add(ELECTRIC_FREIGHT);
                allowedModes.add(ELECTRIC_RIDE);
                link.setAllowedModes(allowedModes);
            }
        });
        //we assume the car network was clean. thus, we do not need to clean the electric vehicle networks.
    }

    /**
     *  1) modifies the allowedModes of all links within {@code carFreeGeoms} except for road types specified by ROAD_TYPES_CAR_ALLOWED
     *  such that they do not contain any element of modesToBan.
     *  2) cleans the modal network for any element of modesToBan.
     *  3) deletes all routes touching the ZEZ
     *
     * @param scenario
     * @param url2CarFreeSingleGeomShapeFile
     * @param modesToBan
     */
    static void banModesFromNetworkArea(Scenario scenario, URL url2CarFreeSingleGeomShapeFile, Set<String> modesToBan){
        Set<String> roadTypesWithCombustionEnginesAllowed = new HashSet<>();
        switch (ROAD_TYPES_CAR_ALLOWED) {
            case nowhere:
                break;
            case motorway:
                roadTypesWithCombustionEnginesAllowed.add("motorway");
                break;
            case motorwayAndPrimaryAndTrunk:
                roadTypesWithCombustionEnginesAllowed.add("motorway");
                roadTypesWithCombustionEnginesAllowed.add("primary");
                roadTypesWithCombustionEnginesAllowed.add("trunk");
                break;
        }

        List<PreparedGeometry> carFreeGeoms = ShpGeometryUtils.loadPreparedGeometries(url2CarFreeSingleGeomShapeFile);

        log.info("start banning modes " + modesToBan.toString() + " from area defined by shape file " + url2CarFreeSingleGeomShapeFile + " except for road types " + roadTypesWithCombustionEnginesAllowed);

        Set<Id<Link>> forbiddenLinks = scenario.getNetwork().getLinks().values().parallelStream()
                .filter(l -> l.getAllowedModes().stream().anyMatch(modesToBan::contains))
                .filter(l -> {
                    String type = ((String) (l.getAttributes().getAttribute("type")));
                    return !(roadTypesWithCombustionEnginesAllowed.stream()
                            .anyMatch(type::contains));}) // cars remain allowed on excludedRoadTypes
                .filter(l -> ShpGeometryUtils.isCoordInPreparedGeometries(l.getToNode().getCoord(), carFreeGeoms))
                .map(Identifiable::getId)
                .collect(Collectors.toSet());

        forbiddenLinks.forEach(id -> {
            Link l = scenario.getNetwork().getLinks().get(id);
            Set<String> allowedModes = new HashSet<>(l.getAllowedModes());
            modesToBan.forEach(allowedModes::remove);
            l.setAllowedModes(allowedModes);
        });

        //clean the network(s)
        modesToBan.forEach(mode -> cleanModalNetwork(scenario.getNetwork(), mode));
        //delete all routes that now have links with unmatched modes, i.e. combustion engine vehicle routes that touch the forbidden links
        PopulationUtils.checkRouteModeAndReset(scenario.getPopulation(), scenario.getNetwork());
    }

    private static void cleanModalNetwork(Network network, String mode) {
        log.info("clean modal network for mode " + mode);
        Set<String> modes = new HashSet<>();
        modes.add(mode);
        new MultimodalNetworkCleaner(network).run(modes);
        log.info("finished");
    }

    private static void copyModeParams(ScoringConfigGroup.ModeParams fromParams, ScoringConfigGroup.ModeParams targetParams) {
        targetParams.setConstant(fromParams.getConstant());
        targetParams.setMarginalUtilityOfDistance(fromParams.getMarginalUtilityOfDistance());
        targetParams.setMarginalUtilityOfTraveling(fromParams.getMarginalUtilityOfTraveling());
        targetParams.setDailyUtilityConstant(fromParams.getDailyUtilityConstant());
        targetParams.setMonetaryDistanceRate(fromParams.getMonetaryDistanceRate());
        targetParams.setDailyMonetaryConstant(fromParams.getDailyMonetaryConstant());
    }

    /**
     * Add travel time bindings for ride and freight modes, which are not actually network modes.
     */
    public static final class ElectricTravelTimeBinding extends AbstractModule {

        public ElectricTravelTimeBinding() { }

        @Override
        public void install() {
            addTravelTimeBinding(ELECTRIC_RIDE).to(networkTravelTime());
            addTravelDisutilityFactoryBinding(ELECTRIC_RIDE).to(Key.get(TravelDisutilityFactory.class, Names.named(ELECTRIC_CAR)));

            addTravelTimeBinding(ELECTRIC_FREIGHT).to(Key.get(TravelTime.class, Names.named(ELECTRIC_TRUCK)));
            addTravelDisutilityFactoryBinding("freight").to(Key.get(TravelDisutilityFactory.class, Names.named(ELECTRIC_TRUCK)));
        }
    }
}
