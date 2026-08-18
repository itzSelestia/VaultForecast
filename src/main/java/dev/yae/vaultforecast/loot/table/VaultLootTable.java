package dev.yae.vaultforecast.loot.table;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class VaultLootTable {
    private final List<Pool> pools;

    public VaultLootTable(Pool... pools) {
        this.pools = List.of(pools);
    }

    public List<ItemStack> generate(LootRollContext context) {
        List<ItemStack> stacks = new ArrayList<>();
        generateRaw(context, stack -> splitOversized(stack, stacks::add));
        return stacks;
    }

    private void generateRaw(LootRollContext context, Consumer<ItemStack> output) {
        for (Pool pool : pools) {
            pool.roll(context, output);
        }
    }

    private static void splitOversized(ItemStack stack, Consumer<ItemStack> output) {
        if (stack.getCount() < stack.getMaxCount()) {
            output.accept(stack);
            return;
        }

        int remaining = stack.getCount();
        while (remaining > 0) {
            ItemStack part = stack.copyWithCount(Math.min(stack.getMaxCount(), remaining));
            remaining -= part.getCount();
            output.accept(part);
        }
    }

    public record Pool(LootNumber rolls, Float chance, List<Entry> entries) {
        public static Pool of(LootNumber rolls, Entry... entries) {
            return new Pool(rolls, null, List.of(entries));
        }

        public static Pool withChance(float chance, LootNumber rolls, Entry... entries) {
            return new Pool(rolls, chance, List.of(entries));
        }

        void roll(LootRollContext context, Consumer<ItemStack> output) {
            if (chance != null && context.random().nextFloat() >= chance) {
                return;
            }

            int count = rolls.nextInt(context);
            for (int roll = 0; roll < count; roll++) {
                rollEntry(context, output);
            }
        }

        private void rollEntry(LootRollContext context, Consumer<ItemStack> output) {
            List<Entry> candidates = entries.stream().filter(entry -> entry.weight() > 0).toList();
            int totalWeight = candidates.stream().mapToInt(Entry::weight).sum();

            if (candidates.isEmpty() || totalWeight == 0) {
                return;
            }

            if (candidates.size() == 1) {
                candidates.getFirst().generate(context, output);
                return;
            }

            int pick = context.random().nextInt(totalWeight);
            for (Entry entry : candidates) {
                pick -= entry.weight();
                if (pick < 0) {
                    entry.generate(context, output);
                    return;
                }
            }
        }
    }

    public sealed interface Entry {
        int weight();

        void generate(LootRollContext context, Consumer<ItemStack> output);

        static Entry item(Item item, int weight, ItemModifier... modifiers) {
            return new ItemEntry(item, weight, List.of(modifiers));
        }

        static Entry table(VaultLootTable table, int weight) {
            return new TableEntry(table, weight);
        }
    }

    public record ItemEntry(Item item, int weight, List<ItemModifier> modifiers) implements Entry {
        @Override
        public void generate(LootRollContext context, Consumer<ItemStack> output) {
            ItemStack stack = new ItemStack(item);
            for (ItemModifier modifier : modifiers) {
                stack = modifier.apply(stack, context);
            }
            output.accept(stack);
        }
    }

    public record TableEntry(VaultLootTable table, int weight) implements Entry {
        @Override
        public void generate(LootRollContext context, Consumer<ItemStack> output) {
            table.generateRaw(context, output);
        }
    }
}
