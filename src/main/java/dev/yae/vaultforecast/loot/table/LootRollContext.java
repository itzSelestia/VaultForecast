package dev.yae.vaultforecast.loot.table;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.random.Random;

public record LootRollContext(Random random, RegistryWrapper.WrapperLookup registries) {
    public RegistryWrapper.Impl<Enchantment> enchantments() {
        return registries.getOrThrow(RegistryKeys.ENCHANTMENT);
    }
}
