package dev.sevenclient.mixin;

import dev.sevenclient.util.RotationSync;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * HEAD of sendMovementPackets: the last moment before rotation is serialised.
 *
 * Snapping here rather than per-frame is what lets the rendered motion stay
 * continuous while every transmitted rotation remains an exact multiple of the
 * mouse step. See RotationSync.
 */
@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {

    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void seven$beforeMovementPackets(CallbackInfo ci) {
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        RotationSync.snapBeforeSend(self);
        RotationSync.beginSilent(self);
    }

    @Inject(method = "sendMovementPackets", at = @At("RETURN"))
    private void seven$afterMovementPackets(CallbackInfo ci) {
        RotationSync.endSilent((ClientPlayerEntity) (Object) this);
    }
}
