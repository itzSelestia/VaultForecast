package dev.yae.vaultforecast.loot.table;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.OminousBottleAmplifierComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

import java.util.List;

@FunctionalInterface
public interface ItemModifier {
    ItemStack apply(ItemStack stack, LootRollContext context);

    static ItemModifier setCount(LootNumber count) {
        return (stack, context) -> {
            stack.setCount(count.nextInt(context));
            return stack;
        };
    }

    static ItemModifier setPotion(RegistryEntry<Potion> potion) {
        return (stack, context) -> {
            stack.apply(
                    DataComponentTypes.POTION_CONTENTS,
                    PotionContentsComponent.DEFAULT,
                    potion,
                    PotionContentsComponent::with
            );
            return stack;
        };
    }

    static ItemModifier setOminousBottleAmplifier(LootNumber amplifier) {
        return (stack, context) -> {
            int value = MathHelper.clamp(amplifier.nextInt(context), 0, 4);
            stack.set(DataComponentTypes.OMINOUS_BOTTLE_AMPLIFIER, new OminousBottleAmplifierComponent(value));
            return stack;
        };
    }

    static ItemModifier setDamage(LootNumber damage) {
        return (stack, context) -> {
            float rolled = damage.nextFloat(context);
            if (stack.isDamageable()) {
                int maxDamage = stack.getMaxDamage();
                float remaining = 1.0F - MathHelper.clamp(rolled, 0.0F, 1.0F);
                stack.setDamage(MathHelper.floor(remaining * maxDamage));
            }
            return stack;
        };
    }

    static ItemModifier enchantWithLevels(LootNumber levels, TagKey<Enchantment> options) {
        return (stack, context) -> EnchantmentHelper.enchant(
                context.random(),
                stack,
                levels.nextInt(context),
                context.enchantments().getOrThrow(options).stream()
        );
    }

    static ItemModifier enchantRandomly(List<RegistryKey<Enchantment>> options) {
        return (stack, context) -> {
            List<RegistryEntry.Reference<Enchantment>> candidates = options.stream()
                    .map(key -> context.enchantments().getOrThrow(key))
                    .toList();

            RegistryEntry<Enchantment> enchantment = Util.getRandom(candidates, context.random());
            int level = MathHelper.nextInt(
                    context.random(),
                    enchantment.value().getMinLevel(),
                    enchantment.value().getMaxLevel()
            );

            ItemStack result = stack.isOf(Items.BOOK) ? new ItemStack(Items.ENCHANTED_BOOK) : stack;
            result.addEnchantment(enchantment, level);
            return result;
        };
    }

    static ItemModifier setEnchantment(RegistryKey<Enchantment> enchantment, int level) {
        return (stack, context) -> {
            ItemStack result = stack.isOf(Items.BOOK) ? new ItemStack(Items.ENCHANTED_BOOK) : stack;
            RegistryEntry<Enchantment> entry = context.enchantments().getOrThrow(enchantment);
            EnchantmentHelper.apply(result, builder -> builder.set(entry, MathHelper.clamp(level, 0, 255)));
            return result;
        };
    }
}
