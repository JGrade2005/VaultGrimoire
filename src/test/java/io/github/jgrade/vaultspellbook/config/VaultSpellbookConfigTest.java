package io.github.jgrade.vaultspellbook.config;

import io.github.jgrade.vaultspellbook.augment.AugmentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VaultSpellbookConfigTest {
    @Test
    void secondsToTicksRoundsAndNeverReturnsZero() {
        assertEquals(20, VaultSpellbookConfig.secondsToTicks(1.0));
        assertEquals(10, VaultSpellbookConfig.secondsToTicks(0.5));
        assertEquals(1, VaultSpellbookConfig.secondsToTicks(0.01));
    }

    @Test
    void sizeUpHasNoLimit() {
        assertEquals(Integer.MAX_VALUE, VaultSpellbookConfig.maxCount(AugmentType.SIZE_UP));
    }
}
