package org.matsim.run.policies;

import jakarta.inject.Inject;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.Config;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.algorithms.PermissibleModesCalculator;

import java.util.*;

public class ZEZPermissibleModesCalculator implements PermissibleModesCalculator {

    private final List<String> availableModes;
    private final List<String> availableModesWithoutCar;
    private final boolean considerCarAvailability;

    private final List<String> carModes = List.of(ZeroEmissionsZoneScenario.ELECTRIC_CAR, TransportMode.car);

    @Inject
    public ZEZPermissibleModesCalculator(Config config) {
        this.availableModes = Arrays.asList(config.subtourModeChoice().getModes());

        if (this.availableModes.contains(TransportMode.car)) {
            final List<String> l = new ArrayList<String>(this.availableModes);
            l.removeAll(this.carModes);
            this.availableModesWithoutCar = Collections.unmodifiableList(l);
        } else {
            this.availableModesWithoutCar = this.availableModes;
        }

        this.considerCarAvailability = config.subtourModeChoice().considerCarAvailability();
    }

    @Override
    public Collection<String> getPermissibleModes(final Plan plan) {
        if (!considerCarAvailability) return availableModes;

        final Person person;
        try {
            person = plan.getPerson();
        }
        catch (ClassCastException e) {
            throw new IllegalArgumentException( "I need a PersonImpl to get car availability" );
        }

        final boolean carAvail =
                !"no".equals( PersonUtils.getLicense(person) ) &&
                        !"never".equals( PersonUtils.getCarAvail(person) );

        return carAvail ? availableModes : availableModesWithoutCar;
    }

}

