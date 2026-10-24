package io.hmg.appchallenge.foundation.probe;

import ai.pleos.playground.vehicle.Vehicle;
import ai.pleos.playground.vehicle.api.CarInfo;
import ai.pleos.playground.vehicle.api.EvBattery;
import ai.pleos.playground.vehicle.api.Fuel;
import ai.pleos.playground.vehicle.api.Gear;

/**
 * Vehicle 2.3.2 getters are public on the JVM and private in Kotlin metadata.
 * This probe-only bridge stays in the pleosProbe source set.
 */
public final class ProbeVehicleDomains {
    private ProbeVehicleDomains() {
    }

    public static Gear gear(Vehicle vehicle) {
        return vehicle.getGear();
    }

    public static Fuel fuel(Vehicle vehicle) {
        return vehicle.getFuel();
    }

    public static EvBattery evBattery(Vehicle vehicle) {
        return vehicle.getEvBattery();
    }

    public static CarInfo carInfo(Vehicle vehicle) {
        return vehicle.getCarInfo();
    }
}
