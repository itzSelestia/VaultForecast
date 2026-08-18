package dev.yae.vaultforecast.generation;

import dev.yae.vaultforecast.loot.VaultType;
import dev.yae.vaultforecast.loot.model.LootDrop;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record VaultGenerationResult(
        long seed,
        int countPerType,
        GenerationMethod method,
        Map<VaultType, List<LootDrop>> drops
) {
    public VaultGenerationResult {
        EnumMap<VaultType, List<LootDrop>> copy = new EnumMap<>(VaultType.class);

        for (VaultType type : VaultType.values()) {
            List<LootDrop> sequence = drops.get(type);

            if (sequence == null) {
                throw new IllegalArgumentException("No %s drops were generated".formatted(type.id()));
            }
            if (sequence.size() != countPerType) {
                throw new IllegalArgumentException(
                        "Generated %d %s drops instead of %d".formatted(sequence.size(), type.id(), countPerType));
            }

            copy.put(type, List.copyOf(sequence));
        }

        drops = Collections.unmodifiableMap(copy);
    }

    public List<LootDrop> drops(VaultType type) {
        return drops.get(type);
    }

    public VaultGenerationRequest request() {
        return new VaultGenerationRequest(seed, countPerType, method);
    }
}
