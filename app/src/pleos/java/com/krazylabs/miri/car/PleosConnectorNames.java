package com.krazylabs.miri.car;

import ai.pleos.playground.vehicle.constant.control.EvConnectorType;

import java.util.ArrayList;
import java.util.List;

/**
 * Pleos marks {@code EvConnectorType.Companion} internal, so Kotlin in this app cannot call
 * {@code toEvConnectorArrayOrNull}. The method and the field are public bytecode, and this
 * helper is the debug probe's Pleos column.
 */
public final class PleosConnectorNames {
    private PleosConnectorNames() {
    }

    public static String labels(int[] values) {
        List<Integer> boxed = new ArrayList<>(values.length);
        for (int value : values) {
            boxed.add(value);
        }
        List<EvConnectorType> mapped = EvConnectorType.Companion.toEvConnectorArrayOrNull(boxed);
        if (mapped == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < mapped.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(mapped.get(i).name());
        }
        out.append(']');
        return out.toString();
    }
}
