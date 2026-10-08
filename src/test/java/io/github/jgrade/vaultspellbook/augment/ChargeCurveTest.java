package io.github.jgrade.vaultspellbook.augment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// The defaults: 10x, 1x less per second, 0.5x once it has passed 1x.
class ChargeCurveTest {
    private static float at(long ticks) {
        return ChargeCurve.beamMultiplier(ticks, 10.0, 1.0, 0.5);
    }

    @Test
    void firstSecondIsTheStartMultiplier() {
        assertEquals(10.0F, at(0));
        assertEquals(10.0F, at(19));
    }

    @Test
    void dropsOneStepEachSecond() {
        assertEquals(9.0F, at(20));
        assertEquals(5.0F, at(5 * 20));
        assertEquals(1.0F, at(9 * 20));
    }

    @Test
    void floorOnceItHasPassedOne() {
        assertEquals(0.5F, at(10 * 20));
        assertEquals(0.5F, at(60 * 20));
    }
}
