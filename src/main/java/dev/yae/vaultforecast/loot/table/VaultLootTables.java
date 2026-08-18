package dev.yae.vaultforecast.loot.table;

import dev.yae.vaultforecast.loot.VaultType;

public final class VaultLootTables {
    public static VaultLootTable forType(VaultType type) {
        return switch (type) {
            case NORMAL -> NormalVaultLootTables.REWARD;
            case OMINOUS -> OminousVaultLootTables.REWARD;
        };
    }

    private VaultLootTables() {
    }
}
