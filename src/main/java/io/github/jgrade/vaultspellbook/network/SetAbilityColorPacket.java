package io.github.jgrade.vaultspellbook.network;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import io.github.jgrade.vaultspellbook.color.AbilityColor;
import io.github.jgrade.vaultspellbook.color.SpellColors;
import io.github.jgrade.vaultspellbook.item.VaultBookCaster;
import io.github.jgrade.vaultspellbook.item.VaultSpellbookItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

// Client -> server: set (or with no colour, clear) the colour of the glyph at a position of a spell
// slot on the Vault Spellbook the player holds. Ignored unless that cell still holds
// the named glyph.
public record SetAbilityColorPacket(int slot, int position, String glyphId, @Nullable AbilityColor color) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(slot);
        buffer.writeVarInt(position);
        buffer.writeUtf(glyphId);
        buffer.writeBoolean(color != null);
        if (color != null) {
            color.write(buffer);
        }
    }

    public static SetAbilityColorPacket decode(FriendlyByteBuf buffer) {
        return new SetAbilityColorPacket(buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(256),
                buffer.readBoolean() ? AbilityColor.read(buffer) : null);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        context.get().enqueueWork(() -> {
            if (player != null) {
                apply(player);
            }
        });
        context.get().setPacketHandled(true);
    }

    private void apply(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof VaultSpellbookItem) {
                List<AbstractSpellPart> recipe = new VaultBookCaster(stack).getSpell(slot).recipe;
                if (position >= 0 && position < recipe.size() && recipe.get(position) != null
                        && recipe.get(position).getId().equals(glyphId)) {
                    SpellColors.set(stack, slot, position, glyphId, color);
                }
                return;
            }
        }
    }
}
