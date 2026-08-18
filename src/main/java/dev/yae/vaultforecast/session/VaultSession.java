package dev.yae.vaultforecast.session;

import dev.yae.vaultforecast.generation.VaultGenerationRequest;
import dev.yae.vaultforecast.generation.VaultGenerationResult;
import dev.yae.vaultforecast.loot.VaultType;
import dev.yae.vaultforecast.loot.model.LootDrop;
import dev.yae.vaultforecast.prediction.LootFinder;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class VaultSession {
    private VaultGenerationRequest request;
    private Map<VaultType, VaultTypeState> states = new EnumMap<>(VaultType.class);

    public void load(VaultGenerationResult result) {
        Map<VaultType, VaultTypeState> loaded = new EnumMap<>(VaultType.class);
        for (VaultType type : VaultType.values()) {
            loaded.put(type, new VaultTypeState(result.drops(type)));
        }

        this.request = result.request();
        this.states = loaded;
    }

    public boolean isLoaded() {
        return request != null && !states.isEmpty();
    }

    public Optional<VaultTypeState> state(VaultType type) {
        return Optional.ofNullable(states.get(type));
    }

    public Optional<LootFinder> finder(VaultType type) {
        return state(type).map(VaultTypeState::finder);
    }

    public List<LootDrop> drops(VaultType type) {
        return state(type).map(VaultTypeState::drops).orElseGet(List::of);
    }

    public Optional<VaultGenerationRequest> request() {
        return Optional.ofNullable(request);
    }

    public void clear() {
        request = null;
        states = new EnumMap<>(VaultType.class);
    }
}
