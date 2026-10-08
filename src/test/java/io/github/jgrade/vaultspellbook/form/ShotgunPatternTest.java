package io.github.jgrade.vaultspellbook.form;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShotgunPatternTest {
    private static final Vec3 NORTH = new Vec3(0, 0, -1);
    // Vec3.directionFromRotation uses Minecraft's sine table, accurate to about 1e-4.
    private static final double TABLE = 1.0E-3;

    @Test
    void noSplitKeepsTheAim() {
        List<Vec3> directions = ShotgunPattern.directions(NORTH, 0, 15);
        assertEquals(1, directions.size());
        assertEquals(0.0, directions.get(0).distanceTo(NORTH), TABLE);
    }

    @Test
    void eachSplitDoublesTheDirections() {
        for (int splits = 0; splits <= 6; splits++) {
            assertEquals(1 << splits, ShotgunPattern.directions(NORTH, splits, 15).size());
        }
    }

    @Test
    void firstSplitIsHorizontalWithTheSpreadBetweenTheHalves() {
        List<Vec3> directions = ShotgunPattern.directions(NORTH, 1, 15);
        assertEquals(0.0, directions.get(0).y, TABLE);
        assertEquals(0.0, directions.get(1).y, TABLE);
        double degrees = Math.toDegrees(Math.acos(directions.get(0).normalize().dot(directions.get(1).normalize())));
        assertEquals(15.0, degrees, 0.05);
    }

    @Test
    void secondSplitAddsTheVerticalHalves() {
        List<Vec3> directions = ShotgunPattern.directions(NORTH, 2, 15);
        long above = directions.stream().filter(d -> d.y > 0.01).count();
        long below = directions.stream().filter(d -> d.y < -0.01).count();
        assertEquals(2, above);
        assertEquals(2, below);
    }

    @Test
    void directionsAreUnitLength() {
        for (Vec3 direction : ShotgunPattern.directions(new Vec3(1, 0.5, 0.2), 4, 15)) {
            assertEquals(1.0, direction.length(), TABLE);
        }
    }
}
