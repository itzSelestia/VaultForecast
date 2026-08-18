package dev.yae.vaultforecast.session;

import dev.yae.vaultforecast.loot.model.LootDrop;
import dev.yae.vaultforecast.prediction.LootFinder;

import java.util.List;

public final class VaultTypeState {
    private final List<LootDrop> drops;
    private final LootFinder finder;

    public VaultTypeState(List<LootDrop> drops) {
        this.drops = List.copyOf(drops);
        this.finder = new LootFinder(this.drops);
    }

    public List<LootDrop> drops() {
        return drops;
    }

    public LootFinder finder() {
        return finder;
    }

    public int size() {
        return drops.size();
    }
}
