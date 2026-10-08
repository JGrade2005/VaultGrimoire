package io.github.jgrade.vaultspellbook.ability.special;

import io.github.jgrade.vaultspellbook.VaultSpellbook;
import io.github.jgrade.vaultspellbook.ability.VaultAbilities;
import io.github.jgrade.vaultspellbook.cast.VaultCaster;
import iskallia.vault.block.entity.AbilitySuppressorTileEntity;
import iskallia.vault.event.ActiveFlags;
import iskallia.vault.gear.data.VaultGearData;
import iskallia.vault.init.ModConfigs;
import iskallia.vault.init.ModGearAttributes;
import iskallia.vault.item.tool.ToolItem;
import iskallia.vault.skill.ability.effect.spi.AbstractVeinMinerAbility;
import iskallia.vault.skill.base.TieredSkill;
import iskallia.vault.util.BlockBreakHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.world.BlockEvent.BreakEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.Optional;

// Glyph vein mining. Mirrors VH's own handler (AbstractVeinMinerAbility.onBlockMined)
// with the same checks (no fake players, hammering tools excluded, ability suppressor, deny list,
// IS_AOE_MINING re-entry flag) but without its "Vein Miner must be the selected ability" condition.
// Runs at normal priority, after VH's HIGH handler: when VH vein-mines natively it cancels the event,
// so this handler is skipped and blocks are never mined twice.
@Mod.EventBusSubscriber(modid = VaultSpellbook.MOD_ID)
public final class VeinMinerBreakHandler {
    // Private in VH 1.18.2-3.21.6.6884 (AbstractVeinMinerAbility.java:38); the dig logic itself is public.
    private static final Field HANDLER_FIELD = findHandlerField();

    private VeinMinerBreakHandler() {
    }

    @SubscribeEvent
    public static void onBlockBreak(BreakEvent event) {
        if (HANDLER_FIELD == null
                || event.getWorld().isClientSide()
                || event.getPlayer() instanceof FakePlayer
                || !(event.getPlayer() instanceof ServerPlayer player)
                || !(event.getWorld() instanceof ServerLevel level)) {
            return;
        }
        Optional<String> specializationId = VeinMinerSessions.get(player.getUUID());
        if (specializationId.isEmpty() || !VaultCaster.isAllowedHere(player, specializationId.get())) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        if (tool.getItem() instanceof ToolItem && VaultGearData.read(tool).hasAttribute(ModGearAttributes.HAMMERING)) {
            return;
        }
        if (AbilitySuppressorTileEntity.hasAbilitySuppressorAround(player)) {
            player.displayClientMessage(new TextComponent("Ability Suppressor nearby").withStyle(ChatFormatting.RED), true);
            return;
        }
        Optional<AbstractVeinMinerAbility> ability = findAbility(player, specializationId.get());
        if (ability.isEmpty()) {
            VeinMinerSessions.stop(player.getUUID()); // respecced or unlearned since the glyph was cast
            return;
        }
        if (ModConfigs.ABILITIES_VEIN_MINER_DENY_CONFIG.isItemDenied(tool)) {
            return;
        }
        BlockBreakHandler handler = getHandler(ability.get());
        if (handler == null) {
            return;
        }
        BlockPos pos = event.getPos();
        ActiveFlags.IS_AOE_MINING.runIfNotSet(() -> {
            if (handler.areaDig(level, player, pos, level.getBlockState(pos).getBlock())) {
                event.setCanceled(true);
            }
        });
    }

    // The player's own Vein Miner instance, only while that specialization is still learned and selected.
    private static Optional<AbstractVeinMinerAbility> findAbility(ServerPlayer player, String specializationId) {
        return VaultAbilities.findSpecialization(VaultAbilities.getServerTree(player), specializationId)
                .filter(TieredSkill::isUnlocked)
                .map(TieredSkill::getChild)
                .filter(AbstractVeinMinerAbility.class::isInstance)
                .map(AbstractVeinMinerAbility.class::cast);
    }

    private static BlockBreakHandler getHandler(AbstractVeinMinerAbility ability) {
        try {
            return (BlockBreakHandler) HANDLER_FIELD.get(ability);
        } catch (IllegalAccessException e) {
            VaultSpellbook.LOGGER.error("Could not read Vein Miner's block break handler", e);
            return null;
        }
    }

    private static Field findHandlerField() {
        try {
            Field field = AbstractVeinMinerAbility.class.getDeclaredField("blockBreakHandler");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            VaultSpellbook.LOGGER.error("Vault Hunters' AbstractVeinMinerAbility has no 'blockBreakHandler' field; "
                    + "glyph vein mining is disabled for this Vault Hunters version", e);
            return null;
        }
    }
}
