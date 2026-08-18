package dev.yae.vaultforecast.loot.table;

import dev.yae.vaultforecast.loot.table.VaultLootTable.Entry;
import dev.yae.vaultforecast.loot.table.VaultLootTable.Pool;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.registry.tag.EnchantmentTags;

import java.util.List;

public final class OminousVaultLootTables {

    private static final VaultLootTable COMMON = new VaultLootTable(
            Pool.of(
                    LootNumber.constant(1),
                    Entry.item(Items.EMERALD, 5, ItemModifier.setCount(LootNumber.uniform(4, 10))),
                    Entry.item(Items.WIND_CHARGE, 4, ItemModifier.setCount(LootNumber.uniform(8, 12))),
                    Entry.item(Items.TIPPED_ARROW, 3,
                            ItemModifier.setCount(LootNumber.uniform(4, 12)),
                            ItemModifier.setPotion(Potions.STRONG_SLOWNESS)),
                    Entry.item(Items.DIAMOND, 2, ItemModifier.setCount(LootNumber.uniform(2, 3))),
                    Entry.item(Items.OMINOUS_BOTTLE, 1,
                            ItemModifier.setCount(LootNumber.constant(1)),
                            ItemModifier.setOminousBottleAmplifier(LootNumber.uniform(2, 4)))
            )
    );

    private static final VaultLootTable RARE = new VaultLootTable(
            Pool.of(
                    LootNumber.constant(1),
                    Entry.item(Items.EMERALD_BLOCK, 5),
                    Entry.item(Items.IRON_BLOCK, 4),
                    Entry.item(Items.CROSSBOW, 4,
                            ItemModifier.enchantWithLevels(LootNumber.uniform(5, 20), EnchantmentTags.ON_RANDOM_LOOT)),
                    Entry.item(Items.GOLDEN_APPLE, 3),
                    Entry.item(Items.DIAMOND_AXE, 3,
                            ItemModifier.enchantWithLevels(LootNumber.uniform(10, 20), EnchantmentTags.ON_RANDOM_LOOT)),
                    Entry.item(Items.DIAMOND_CHESTPLATE, 3,
                            ItemModifier.enchantWithLevels(LootNumber.uniform(10, 20), EnchantmentTags.ON_RANDOM_LOOT)),
                    Entry.item(Items.BOOK, 2, ItemModifier.enchantRandomly(List.of(
                            Enchantments.KNOCKBACK,
                            Enchantments.PUNCH,
                            Enchantments.SMITE,
                            Enchantments.LOOTING,
                            Enchantments.MULTISHOT))),
                    Entry.item(Items.BOOK, 2, ItemModifier.enchantRandomly(List.of(
                            Enchantments.BREACH,
                            Enchantments.DENSITY))),
                    Entry.item(Items.BOOK, 2, ItemModifier.setEnchantment(Enchantments.WIND_BURST, 1)),
                    Entry.item(Items.DIAMOND_BLOCK, 1)
            )
    );

    private static final VaultLootTable UNIQUE = new VaultLootTable(
            Pool.of(
                    LootNumber.constant(1),
                    Entry.item(Items.ENCHANTED_GOLDEN_APPLE, 3),
                    Entry.item(Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, 3),
                    Entry.item(Items.FLOW_BANNER_PATTERN, 2),
                    Entry.item(Items.MUSIC_DISC_CREATOR, 1),
                    Entry.item(Items.HEAVY_CORE, 1)
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
            Pool.withChance(0.75F,
                    LootNumber.constant(1),
                    Entry.table(UNIQUE, 1)
            )
    );

    private OminousVaultLootTables() {
    }
}
