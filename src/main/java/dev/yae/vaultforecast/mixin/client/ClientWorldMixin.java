package dev.yae.vaultforecast.mixin.client;

import dev.yae.vaultforecast.observation.VaultDropObserver;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {
    @Inject(method = "syncWorldEvent", at = @At("HEAD"))
    private void vaultforecast$onWorldEvent(
            Entity source,
            int eventId,
            BlockPos pos,
            int data,
            CallbackInfo info
    ) {
        VaultDropObserver.getInstance().onWorldEvent((ClientWorld) (Object) this, eventId, pos);
    }
}
