package com.krazylabs.miri.vehicle;

import ai.pleos.playground.vehicle.Vehicle;
import ai.pleos.playground.vehicle.api.CarInfo;
import ai.pleos.playground.vehicle.api.Door;
import ai.pleos.playground.vehicle.api.EvBattery;
import ai.pleos.playground.vehicle.api.Fuel;
import ai.pleos.playground.vehicle.api.Gear;
import ai.pleos.playground.vehicle.api.Light;
import ai.pleos.playground.vehicle.api.Odometer;
import ai.pleos.playground.vehicle.api.Seat;
import ai.pleos.playground.vehicle.api.TurnSignal;
import ai.pleos.playground.vehicle.api.Window;

/**
 * Vehicle 2.3.2 exposes getDoor/getWindow/... as public JVM methods, but the Kotlin metadata
 * marks the corresponding properties private. Java can call the public methods.
 */
public final class VehicleDomains {
    private VehicleDomains() {
    }

    public static Door door(Vehicle vehicle) {
        return vehicle.getDoor();
    }

    public static Window window(Vehicle vehicle) {
        return vehicle.getWindow();
    }

    public static Fuel fuel(Vehicle vehicle) {
        return vehicle.getFuel();
    }

    public static EvBattery evBattery(Vehicle vehicle) {
        return vehicle.getEvBattery();
    }

    public static Odometer odometer(Vehicle vehicle) {
        return vehicle.getOdometer();
    }

    public static CarInfo carInfo(Vehicle vehicle) {
        return vehicle.getCarInfo();
    }

    public static Seat seat(Vehicle vehicle) {
        return vehicle.getSeat();
    }

    public static TurnSignal turnSignal(Vehicle vehicle) {
        return vehicle.getTurnSignal();
    }

    public static Light light(Vehicle vehicle) {
        return vehicle.getLight();
    }

    public static Gear gear(Vehicle vehicle) {
        return vehicle.getGear();
    }
}
