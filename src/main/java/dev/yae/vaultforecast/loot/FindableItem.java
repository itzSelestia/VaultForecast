package dev.yae.vaultforecast.loot;

import dev.yae.vaultforecast.loot.model.LootDrop;
import dev.yae.vaultforecast.loot.model.LootEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

public enum FindableItem {
    HEAVY_CORE("heavy_core", Set.of(VaultType.OMINOUS), item(() -> Items.HEAVY_CORE)),
    ENCHANTED_GAPPLE("enchanted_gapple", Set.of(VaultType.OMINOUS), item(() -> Items.ENCHANTED_GOLDEN_APPLE)),
    WIND_BURST("wind_burst", Set.of(VaultType.OMINOUS), FindableItem::isWindBurstBook),
    TRIDENT("trident", Set.of(VaultType.NORMAL), item(() -> Items.TRIDENT)),
    OMINOUS_BOTTLE("ominous_bottle", Set.of(VaultType.NORMAL, VaultType.OMINOUS), item(() -> Items.OMINOUS_BOTTLE));

    private final String id;
    private final Set<VaultType> supportedTypes;
    private final Predicate<LootEntry> matcher;

    FindableItem(String id, Set<VaultType> supportedTypes, Predicate<LootEntry> matcher) {
        this.id = id;
        this.supportedTypes = Set.copyOf(supportedTypes);
        this.matcher = matcher;
    }

    public String id() {
        return id;
    }

    public Set<VaultType> supportedTypes() {
        return supportedTypes;
    }

    public boolean supports(VaultType type) {
        return supportedTypes.contains(type);
    }

    public boolean matches(LootEntry entry) {
        return matcher.test(entry);
    }

    public boolean matches(LootDrop drop) {
        return drop.items().stream().anyMatch(this::matches);
    }

    public OptionalInt firstMatch(List<LootDrop> drops, int fromIndex) {
        for (int index = Math.max(0, fromIndex); index < drops.size(); index++) {
            if (matches(drops.get(index))) {
                return OptionalInt.of(index);
            }
        }
        return OptionalInt.empty();
    }

    public static Optional<FindableItem> fromId(String id) {
        String normalised = id.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(item -> item.id.equals(normalised))
                .findFirst();
    }

    public static List<String> ids() {
        return Arrays.stream(values()).map(FindableItem::id).toList();
    }

    public static List<String> idsFor(VaultType type) {
        return Arrays.stream(values())
                .filter(item -> item.supports(type))
                .map(FindableItem::id)
                .toList();
    }

    private static Predicate<LootEntry> item(Supplier<Item> item) {
        return entry -> entry.item() == item.get();
    }

    private static boolean isWindBurstBook(LootEntry entry) {
        if (entry.item() != Items.ENCHANTED_BOOK) {
            return false;
        }

        ItemEnchantmentsComponent enchantments = entry.components().get(DataComponentTypes.STORED_ENCHANTMENTS);
        if (enchantments == null) {
            return false;
        }

        for (RegistryEntry<net.minecraft.enchantment.Enchantment> enchantment : enchantments.getEnchantments()) {
            if (enchantment.matchesKey(Enchantments.WIND_BURST)) {
                return true;
            }
        }

        return false;
    }
}
