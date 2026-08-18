package dev.yae.vaultforecast.loot;

import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum VaultType {
    NORMAL("normal", "chests/trial_chambers/reward"),
    OMINOUS("ominous", "chests/trial_chambers/reward_ominous");

    private final String id;
    private final Identifier lootTable;

    VaultType(String id, String lootTablePath) {
        this.id = id;
        this.lootTable = Identifier.ofVanilla(lootTablePath);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return id;
    }

    public Identifier lootTable() {
        return lootTable;
    }

    public String lootTableId() {
        return lootTable.toString();
    }

    public static Optional<VaultType> fromId(String id) {
        String normalised = id.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> type.id.equals(normalised))
                .findFirst();
    }

    public static List<String> ids() {
        return Arrays.stream(values()).map(VaultType::id).toList();
    }
}
