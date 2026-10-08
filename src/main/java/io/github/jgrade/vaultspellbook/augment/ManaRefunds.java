package io.github.jgrade.vaultspellbook.augment;

import iskallia.vault.mana.ManaAction;
import iskallia.vault.mana.ManaPlayer;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

// Discount: VH has no mana-cost event (costs come from gear, ManaCostHelper), so the
// augment refunds a share of the Vault mana a cast actually took, measured around the cast.
public final class ManaRefunds {
    private ManaRefunds() {
    }

    private static float getMana(ServerPlayer player) {
        return ((ManaPlayer) player).getMana();
    }

    // Runs the action and refunds the given share of the mana it took (nothing if it took none).
    public static <T> T withRefund(ServerPlayer player, float fraction, Supplier<T> action) {
        float before = getMana(player);
        T result = action.get();
        refund(player, (before - getMana(player)) * fraction);
        return result;
    }

    public static void refund(ServerPlayer player, float amount) {
        if (amount > 0) {
            ((ManaPlayer) player).increaseMana(ManaAction.PLAYER_ACTION, amount);
        }
    }
}
