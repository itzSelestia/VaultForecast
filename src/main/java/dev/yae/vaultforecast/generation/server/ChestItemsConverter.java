package dev.yae.vaultforecast.generation.server;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.DataResult;
import dev.yae.vaultforecast.loot.model.LootDrop;
import dev.yae.vaultforecast.generation.VaultGenerationException;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.RegistryOps;

import java.util.ArrayList;
import java.util.List;

public final class ChestItemsConverter {
    private final RegistryOps<NbtElement> ops;

    public ChestItemsConverter(RegistryWrapper.WrapperLookup registries) {
        this.ops = RegistryOps.of(NbtOps.INSTANCE, registries);
    }

    public NbtList parseItems(String snbt, int index) throws VaultGenerationException {
        NbtElement parsed;
        try {
            parsed = StringNbtReader.fromOps(NbtOps.INSTANCE).read(snbt);
        } catch (CommandSyntaxException exception) {
            throw new VaultGenerationException(
                    "Could not read the loot the vanilla server reported for drop %d.".formatted(index + 1), exception);
        }

        if (!(parsed instanceof NbtList items)) {
            throw new VaultGenerationException(
                    "The vanilla server reported drop %d as %s instead of an item list."
                            .formatted(index + 1, parsed.getClass().getSimpleName()));
        }

        if (items.isEmpty()) {
            throw new VaultGenerationException(
                    "The vanilla server produced an empty chest for drop %d, so a loot command must have failed."
                            .formatted(index + 1));
        }

        return items;
    }

    public LootDrop toDrop(NbtList items, int index) throws VaultGenerationException {
        List<ItemStack> stacks = new ArrayList<>(items.size());

        for (NbtElement element : items) {
            DataResult<ItemStack> result = ItemStack.CODEC.parse(ops, element);
            ItemStack stack = result.result().orElse(null);

            if (stack == null) {
                throw new VaultGenerationException(
                        "Could not read an item of drop %d from the vanilla server: %s"
                                .formatted(index + 1, result.error().map(DataResult.Error::message).orElse("unknown error")));
            }

            stacks.add(stack);
        }

        return LootDrop.canonical(stacks);
    }
}
