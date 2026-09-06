package dev.sevenclient.mixin;

import dev.sevenclient.SevenClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Attack event source.
 *
 * TAIL rather than HEAD: by the time this fires the attack packet has already
 * been queued, so sprint-reset modules react to a hit that definitely happened
 * and in the correct order relative to it. Firing at HEAD would let a key pulse
 * alter sprint state in the same tick the attack is still being built.
 */
@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {

    @Inject(method = "attackEntity", at = @At("TAIL"))
    private void seven$onAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        dev.sevenclient.util.Diagnostics.attackEvents++;
        if (SevenClient.get() != null) {
            SevenClient.get().modules.onAttack(target);
        }
    }
}
