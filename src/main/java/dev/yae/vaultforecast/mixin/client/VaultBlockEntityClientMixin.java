package dev.yae.vaultforecast.mixin.client;

import dev.yae.vaultforecast.observation.VaultDropObserver;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.VaultBlockEntity;
import net.minecraft.block.vault.VaultClientData;
import net.minecraft.block.vault.VaultSharedData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VaultBlockEntity.Client.class)
public class VaultBlockEntityClientMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private static void vaultforecast$observeVault(
            World world,
            BlockPos pos,
            BlockState state,
            VaultClientData clientData,
            VaultSharedData sharedData,
            CallbackInfo info
    ) {
        VaultDropObserver.getInstance().onVaultTick(world, pos, state);
    }
}
