package io.github.jgrade.vaultspellbook.augment;

import io.github.jgrade.vaultspellbook.cast.CastOrigin;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Supplier;

// The augments in effect while our pipeline casts one ability for one player.
// VH computes area and spawns projectiles synchronously inside the cast call, so augments only need
// to apply for its duration. With an origin (a Bundled Spell landing), projectiles the cast spawns
// are moved there. Server thread only; nested casts restore the outer scope.
public final class CastScope {
    private static Active current;

    private CastScope() {
    }

    public static <T> T call(ServerPlayer player, CastModifiers modifiers, @Nullable CastOrigin origin, Supplier<T> action) {
        Active outer = current;
        current = new Active(player, modifiers, origin);
        try {
            return action.get();
        } finally {
            current = outer;
        }
    }

    public static Optional<Active> current() {
        return Optional.ofNullable(current);
    }

    public record Active(ServerPlayer player, CastModifiers modifiers, @Nullable CastOrigin origin) {
    }
}
