package io.github.jgrade.vaultspellbook.cast;

import net.minecraft.world.phys.Vec3;

// Where a Bundled Spell's payload is cast: point abilities happen at position
// (VH's SkillSource position) and projectile abilities relaunch from it along direction.
public record CastOrigin(Vec3 position, Vec3 direction) {
}
