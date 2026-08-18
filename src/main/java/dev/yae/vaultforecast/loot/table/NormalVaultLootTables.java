package dev.yae.vaultforecast.loot.table;

import dev.yae.vaultforecast.loot.table.VaultLootTable.Entry;
import dev.yae.vaultforecast.loot.table.VaultLootTable.Pool;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.registry.tag.EnchantmentTags;

import java.util.List;

public final class NormalVaultLootTables {
    private static final VaultLootTable COMMON = new VaultLootTable(
            Pool.of(
                    LootNumber.constant(1),
                    Entry.item(Items.ARROW, 4, ItemModifier.setCount(LootNumber.uniform(2, 8))),
                    Entry.item(Items.TIPPED_ARROW, 4,
                            ItemModifier.setCount(LootNumber.uniform(2, 8)),
                            ItemModifier.setPotion(Potions.POISON)),
                    Entry.item(Items.EMERALD, 4, ItemModifier.setCount(LootNumber.uniform(2, 4))),
                    Entry.item(Items.WIND_CHARGE, 3, ItemModifier.setCount(LootNumber.uniform(1, 3))),
                    Entry.item(Items.IRON_INGOT, 3, ItemModifier.setCount(LootNumber.uniform(1, 4))),
                    Entry.item(Items.HONEY_BOTTLE, 3, ItemModifier.setCount(LootNumber.uniform(1, 2))),
                    Entry.item(Items.OMINOUS_BOTTLE, 2,
                            ItemModifier.setCount(LootNumber.constant(1)),
                            ItemModifier.setOminousBottleAmplifier(LootNumber.uniform(0, 1))),
                    Entry.item(Items.WIND_CHARGE, 1, ItemModifier.setCount(LootNumber.uniform(4, 12))),
                    Entry.item(Items.DIAMOND, 1, ItemModifier.setCount(LootNumber.uniform(1, 2)))
            )
    );

    private static final VaultLootTable RARE = new VaultLootTable(
            Pool.of(
                    LootNumber.constant(1),
                    Entry.item(Items.EMERALD, 3, ItemModifier.setCount(LootNumber.uniform(2, 4))),
                    Entry.item(Items.SHIELD, 3, ItemModifier.setDamage(LootNumber.uniformFloat(0.5F, 1.0F))),
                    Entry.item(Items.BOW, 3,
                            ItemModifier.enchantWithLevels(LootNumber.uniform(5, 15), EnchantmentTags.ON_RANDOM_LOOT)),
                    Entry.item(Items.CROSSBOW, 2,
                            ItemModifier.enchantWithLevels(LootNumber.uniform(5, 20), EnchantmentTags.ON_RANDOM_LOOT)),
                    Entry.item(Items.IRON_AXE, 2,
                            ItemModifier.enchantWithLevels(LootNumber.uniform(0, 10), EnchantmentTags.ON_RANDOM_LOOT)),
                    Entry.item(Items.IRON_CHESTPLATE, 2,
                            ItemModifier.enchantWithLevels(LootNumber.uniform(0, 10), EnchantmentTags.ON_RANDOM_LOOT)),
                    Entry.item(Items.GOLDEN_CARROT, 2, ItemModifier.setCount(LootNumber.uniform(1, 2))),
                    Entry.item(Items.BOOK, 2, ItemModifier.enchantRandomly(List.of(
                            Enchantments.SHARPNESS,
                            Enchantments.BANE_OF_ARTHROPODS,
                            Enchantments.EFFICIENCY,
                            Enchantments.FORTUNE,
                            Enchantments.SILK_TOUCH,
                            Enchantments.FEATHER_FALLING))),
                    Entry.item(Items.BOOK, 2, ItemModifier.enchantRandomly(List.of(
                            Enchantments.RIPTIDE,
                            Enchantments.LOYALTY,
                            Enchantments.CHANNELING,
                            Enchantments.IMPALING,
                            Enchantments.MENDING))),
                    Entry.item(Items.DIAMOND_CHESTPLATE, 1,
                            ItemModifier.enchantWithLevels(LootNumber.uniform(5, 15), EnchantmentTags.ON_RANDOM_LOOT)),
                    Entry.item(Items.DIAMOND_AXE, 1,
                            ItemModifier.enchantWithLevels(LootNumber.uniform(5, 15), EnchantmentTags.ON_RANDOM_LOOT))
            )
    );

    private static final VaultLootTable UNIQUE = new VaultLootTable(
            Pool.of(
                    LootNumber.constant(1),
                    Entry.item(Items.GOLDEN_APPLE, 4),
                    Entry.item(Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE, 3),
                    Entry.item(Items.GUSTER_BANNER_PATTERN, 2),
                    Entry.item(Items.MUSIC_DISC_PRECIPICE, 2),
                    Entry.item(Items.TRIDENT, 1)
            )
    );

    public static final VaultLootTable REWARD = new VaultLootTable(
            Pool.of(
                    LootNumber.constant(1),
                    Entry.table(RARE, 8),
                    Entry.table(COMMON, 2)
            ),
            Pool.of(
                    LootNumber.uniform(1, 3),
                    Entry.table(COMMON, 1)
            ),
            Pool.withChance(0.25F,
                    LootNumber.constant(1),
                    Entry.table(UNIQUE, 1)
            )
    );

    private NormalVaultLootTables() {
    }
}
