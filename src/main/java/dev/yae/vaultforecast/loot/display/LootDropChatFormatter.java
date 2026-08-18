package dev.yae.vaultforecast.loot.display;

import dev.yae.vaultforecast.loot.model.LootDrop;
import dev.yae.vaultforecast.loot.model.LootEntry;
import dev.yae.vaultforecast.loot.rarity.LootRarity;
import dev.yae.vaultforecast.loot.rarity.LootRarityClassifier;
import net.minecraft.component.ComponentMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LootDropChatFormatter {
    private static final String SEPARATOR = "────────────────────────";
    private static final String BULLET = "• ";

    private final LootRarityClassifier classifier;

    public LootDropChatFormatter() {
        this(new LootRarityClassifier());
    }

    public LootDropChatFormatter(LootRarityClassifier classifier) {
        this.classifier = classifier;
    }

    public List<Text> format(LootDrop drop, int globalDropNumber, int openingsAway) {
        List<Text> lines = new ArrayList<>();
        lines.add(separator());
        lines.add(header(globalDropNumber, openingsAway));

        for (DisplayEntry entry : displayEntries(drop)) {
            lines.add(itemLine(entry));
        }

        return List.copyOf(lines);
    }

    public Text separator() {
        return Text.literal(SEPARATOR).formatted(Formatting.DARK_GRAY);
    }

    private static Text header(int globalDropNumber, int openingsAway) {
        return Text.literal("#" + globalDropNumber)
                .formatted(Formatting.WHITE)
                .append(Text.literal("  (%d opening%s away)".formatted(openingsAway, openingsAway == 1 ? "" : "s"))
                        .formatted(Formatting.GRAY));
    }

    private static Text itemLine(DisplayEntry entry) {
        MutableText name = entry.stack().getName().copy().setStyle(nameStyle(entry));

        return Text.literal(BULLET)
                .formatted(Formatting.DARK_GRAY)
                .append(name)
                .append(Text.literal(" x").formatted(Formatting.GRAY))
                .append(Text.literal(String.valueOf(entry.count())).formatted(Formatting.WHITE));
    }

    private static Style nameStyle(DisplayEntry entry) {
        Style style = Style.EMPTY.withHoverEvent(new HoverEvent.ShowItem(entry.stack()));

        return switch (entry.rarity()) {
            case VALUABLE -> style.withColor(Formatting.WHITE).withBold(true);
            case RARE -> style.withColor(Formatting.WHITE);
            case COMMON -> style.withColor(Formatting.GRAY);
        };
    }

    private List<DisplayEntry> displayEntries(LootDrop drop) {
        Map<MergeKey, Integer> totals = new LinkedHashMap<>();
        Map<MergeKey, LootEntry> examples = new LinkedHashMap<>();

        for (LootEntry entry : drop.items()) {
            MergeKey key = new MergeKey(entry.item(), entry.components());
            totals.merge(key, entry.count(), Integer::sum);
            examples.putIfAbsent(key, entry);
        }

        List<DisplayEntry> entries = new ArrayList<>(totals.size());
        totals.forEach((key, count) -> {
            LootEntry example = examples.get(key);
            LootEntry merged = new LootEntry(example.item(), count, example.components());
            entries.add(new DisplayEntry(merged.toStack(), count, classifier.classify(merged), key));
        });

        entries.sort(ORDER);
        return entries;
    }

    private static final Comparator<DisplayEntry> ORDER = Comparator
            .comparingInt((DisplayEntry entry) -> entry.rarity().ordinal())
            .thenComparing(entry -> Registries.ITEM.getId(entry.key().item()).toString())
            .thenComparingInt(DisplayEntry::count)
            .thenComparingInt(entry -> entry.key().components().hashCode());

    private record MergeKey(Item item, ComponentMap components) {
    }

    private record DisplayEntry(ItemStack stack, int count, LootRarity rarity, MergeKey key) {
    }
}
