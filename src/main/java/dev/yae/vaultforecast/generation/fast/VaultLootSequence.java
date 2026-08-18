package dev.yae.vaultforecast.generation.fast;

import dev.yae.vaultforecast.loot.VaultType;
import dev.yae.vaultforecast.loot.model.LootDrop;
import dev.yae.vaultforecast.loot.table.LootRollContext;
import dev.yae.vaultforecast.loot.table.VaultLootTable;
import dev.yae.vaultforecast.loot.table.VaultLootTables;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.random.RandomSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public final class VaultLootSequence {
    public static List<LootDrop> generate(
            VaultType type,
            long worldSeed,
            int size,
            RegistryWrapper.WrapperLookup registries
    ) {
        return generate(type, worldSeed, size, registries, generated -> {
        });
    }

    public static List<LootDrop> generate(
            VaultType type,
            long worldSeed,
            int size,
            RegistryWrapper.WrapperLookup registries,
            IntConsumer progress
    ) {
        Random random = new RandomSequence(worldSeed, type.lootTable()).getSource();
        LootRollContext context = new LootRollContext(random, registries);
        VaultLootTable table = VaultLootTables.forType(type);

        List<LootDrop> drops = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            drops.add(LootDrop.canonical(table.generate(context)));
            progress.accept(index + 1);
        }

        return List.copyOf(drops);
    }

    private VaultLootSequence() {
    }
}
