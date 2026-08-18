package dev.yae.vaultforecast.loot.model;

import net.minecraft.component.Component;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.ComponentType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.Objects;

public record LootEntry(Item item, int count, ComponentMap components) {
    public static LootEntry fromStack(ItemStack stack) {
        return new LootEntry(
                stack.getItem(),
                stack.getCount(),
                stack.getImmutableComponents()
        );
    }

    public ItemStack toStack() {
        ItemStack stack = new ItemStack(item, count);
        ComponentMap defaults = item.getComponents();

        for (Component<?> component : components) {
            if (!Objects.equals(defaults.get(component.type()), component.value())) {
                apply(stack, component);
            }
        }

        return stack;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void apply(ItemStack stack, Component<?> component) {
        stack.set((ComponentType) component.type(), component.value());
    }
}
