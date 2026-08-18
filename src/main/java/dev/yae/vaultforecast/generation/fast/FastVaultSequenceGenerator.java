package dev.yae.vaultforecast.generation.fast;

import dev.yae.vaultforecast.generation.GenerationProgressListener;
import dev.yae.vaultforecast.generation.VaultGenerationRequest;
import dev.yae.vaultforecast.generation.VaultGenerationResult;
import dev.yae.vaultforecast.generation.VaultSequenceGenerator;
import dev.yae.vaultforecast.loot.VaultType;
import dev.yae.vaultforecast.loot.model.LootDrop;
import net.minecraft.registry.RegistryWrapper;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class FastVaultSequenceGenerator implements VaultSequenceGenerator {
    private final RegistryWrapper.WrapperLookup registries;

    public FastVaultSequenceGenerator(RegistryWrapper.WrapperLookup registries) {
        this.registries = registries;
    }

    @Override
    public VaultGenerationResult generate(VaultGenerationRequest request, GenerationProgressListener progressListener) {
        int count = request.count();
        Map<VaultType, List<LootDrop>> drops = new EnumMap<>(VaultType.class);

        for (VaultType type : VaultType.values()) {
            progressListener.onStage("Rolling %d %s drops locally...".formatted(count, type.displayName()));

            drops.put(type, VaultLootSequence.generate(
                    type,
                    request.seed(),
                    count,
                    registries,
                    generated -> progressListener.onProgress(type, generated, count)));
        }

        return new VaultGenerationResult(request.seed(), count, request.method(), drops);
    }
}
