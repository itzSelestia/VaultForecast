package dev.yae.vaultforecast.session;

import dev.yae.vaultforecast.generation.GenerationMethod;
import dev.yae.vaultforecast.generation.VaultGenerationRequest;
import dev.yae.vaultforecast.loot.VaultType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public record SavedSession(
        String session,
        long seed,
        int count,
        GenerationMethod method,
        Map<VaultType, Set<Integer>> candidates
) {
    public SavedSession {
        EnumMap<VaultType, Set<Integer>> copy = new EnumMap<>(VaultType.class);
        for (VaultType type : VaultType.values()) {
            copy.put(type, Set.copyOf(candidates.getOrDefault(type, Set.of())));
        }
        candidates = Collections.unmodifiableMap(copy);
    }

    public Set<Integer> candidates(VaultType type) {
        return candidates.get(type);
    }

    public int totalCandidates() {
        return candidates.values().stream().mapToInt(Set::size).sum();
    }

    public VaultGenerationRequest request() {
        return new VaultGenerationRequest(seed, count, method);
    }
}
