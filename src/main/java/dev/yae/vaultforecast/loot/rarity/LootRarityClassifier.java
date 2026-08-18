package dev.yae.vaultforecast.loot.rarity;

import dev.yae.vaultforecast.loot.model.LootEntry;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.Set;

public final class LootRarityClassifier {

    private static final Set<Item> VALUABLE_ITEMS = Set.of(
            Items.ENCHANTED_GOLDEN_APPLE,
            Items.HEAVY_CORE,
            Items.TRIDENT
    );

    private static final Set<Item> RARE_ITEMS = Set.of(
            Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.FLOW_BANNER_PATTERN,
            Items.MUSIC_DISC_CREATOR,
            Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.GUSTER_BANNER_PATTERN,
            Items.MUSIC_DISC_PRECIPICE,
            Items.GOLDEN_APPLE,
            Items.DIAMOND_BLOCK,
            Items.OMINOUS_BOTTLE,
            Items.GOLDEN_CARROT,
            Items.HONEY_BOTTLE
    );

    private static final RegistryKey<Enchantment> VALUABLE_ENCHANTMENT = Enchantments.WIND_BURST;

    public LootRarity classify(LootEntry entry) {
        ComponentMap components = entry.components();

        if (VALUABLE_ITEMS.contains(entry.item()) || hasValuableEnchantment(components)) {
            return LootRarity.VALUABLE;
        }

        if (RARE_ITEMS.contains(entry.item()) || isEnchanted(components) || hasPotion(components)) {
            return LootRarity.RARE;
        }

        return LootRarity.COMMON;
    }

    private static boolean hasValuableEnchantment(ComponentMap components) {
        return contains(components.get(DataComponentTypes.STORED_ENCHANTMENTS), VALUABLE_ENCHANTMENT)
                || contains(components.get(DataComponentTypes.ENCHANTMENTS), VALUABLE_ENCHANTMENT);
    }

    private static boolean isEnchanted(ComponentMap components) {
        return isPresent(components.get(DataComponentTypes.STORED_ENCHANTMENTS))
                || isPresent(components.get(DataComponentTypes.ENCHANTMENTS));
    }

    private static boolean hasPotion(ComponentMap components) {
        PotionContentsComponent potion = components.get(DataComponentTypes.POTION_CONTENTS);
        return potion != null && potion.potion().isPresent();
    }

    private static boolean contains(ItemEnchantmentsComponent enchantments, RegistryKey<Enchantment> wanted) {
        if (enchantments == null) {
            return false;
        }

        for (RegistryEntry<Enchantment> enchantment : enchantments.getEnchantments()) {
            if (enchantment.matchesKey(wanted)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isPresent(ItemEnchantmentsComponent enchantments) {
        return enchantments != null && !enchantments.isEmpty();
    }
}
