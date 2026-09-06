package dev.sevenclient.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the vanilla item-use gate.
 *
 * Vanilla sets itemUseCooldown to 4 after every use, which caps held right-click
 * at 5 uses/second. Zeroing it lets the genuine input path in handleInputEvents
 * run every tick instead. This changes only the client-side rate limit -- each
 * resulting interaction is still built and sequenced by the vanilla interaction
 * manager, so sequence numbers stay consistent.
 */
@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {

    @Accessor("itemUseCooldown")
    void seven$setItemUseCooldown(int value);

    @Accessor("itemUseCooldown")
    int seven$getItemUseCooldown();
}
