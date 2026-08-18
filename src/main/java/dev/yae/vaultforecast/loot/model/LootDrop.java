package dev.yae.vaultforecast.loot.model;

import net.minecraft.component.ComponentMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record LootDrop(List<LootEntry> items) {
    private static final Comparator<LootEntry> CANONICAL_ORDER = Comparator
            .comparing((LootEntry entry) -> Registries.ITEM.getId(entry.item()).toString())
            .thenComparingInt(LootEntry::count)
            .thenComparingInt(entry -> entry.components().hashCode());

    public LootDrop {
        items = List.copyOf(items);
    }

    public static LootDrop of(List<ItemStack> list) {
        List<LootEntry> result = new ArrayList<>();
        for (ItemStack itemStack : list) {
            result.add(LootEntry.fromStack(itemStack));
        }
        return new LootDrop(result);
    }

    public static LootDrop canonical(List<ItemStack> stacks) {
        Map<StackKey, Integer> totals = new LinkedHashMap<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            StackKey key = new StackKey(stack.getItem(), stack.getImmutableComponents());
            totals.merge(key, stack.getCount(), Integer::sum);
        }

        List<LootEntry> entries = new ArrayList<>(totals.size());
        totals.forEach((key, count) -> entries.add(new LootEntry(key.item(), count, key.components())));
        entries.sort(CANONICAL_ORDER);

        return new LootDrop(entries);
    }

    private record StackKey(Item item, ComponentMap components) {
    }
}
