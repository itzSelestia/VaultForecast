package dev.yae.vaultforecast.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.yae.vaultforecast.generation.GenerationMethod;
import dev.yae.vaultforecast.generation.VaultGenerationRequest;
import dev.yae.vaultforecast.loot.FindableItem;
import dev.yae.vaultforecast.loot.VaultType;

import java.util.List;
import java.util.Locale;

public final class VaultForecastCommandTree {
    public static final int DEFAULT_GET_COUNT = 10;
    public static final int MIN_GET_COUNT = 1;
    public static final int MAX_GET_COUNT = 50;

    public static final int MIN_MAX_GAP = 0;
    public static final int MAX_MAX_GAP = VaultGenerationRequest.MAX_COUNT;

    public static final VaultType LEGACY_GET_TYPE = VaultType.OMINOUS;

    @FunctionalInterface
    public interface TypedCountCommand<S> {
        int run(CommandContext<S> context, VaultType type, int count) throws CommandSyntaxException;
    }

    @FunctionalInterface
    public interface TypedCommand<S> {
        int run(CommandContext<S> context, VaultType type) throws CommandSyntaxException;
    }

    public record Handlers<S>(
            Command<S> seed,
            Command<S> load,
            Command<S> count,
            Command<S> watch,
            TypedCountCommand<S> get,
            TypedCommand<S> find,
            Command<S> resetAll,
            TypedCommand<S> resetType,
            Command<S> maxGap,
            Command<S> forget
    ) {
    }

    public static <S> LiteralArgumentBuilder<S> build(Handlers<S> handlers) {
        LiteralArgumentBuilder<S> get = LiteralArgumentBuilder.<S>literal("get")
                .executes(context -> handlers.get().run(context, LEGACY_GET_TYPE, DEFAULT_GET_COUNT))
                .then(VaultForecastCommandTree.<S>countArgument().executes(context ->
                        handlers.get().run(context, LEGACY_GET_TYPE, readGetCount(context))));

        LiteralArgumentBuilder<S> find = LiteralArgumentBuilder.<S>literal("find");
        LiteralArgumentBuilder<S> reset = LiteralArgumentBuilder.<S>literal("reset")
                .executes(handlers.resetAll());

        for (VaultType type : VaultType.values()) {
            get.then(LiteralArgumentBuilder.<S>literal(type.id())
                    .executes(context -> handlers.get().run(context, type, DEFAULT_GET_COUNT))
                    .then(VaultForecastCommandTree.<S>countArgument().executes(context ->
                            handlers.get().run(context, type, readGetCount(context)))));

            find.then(LiteralArgumentBuilder.<S>literal(type.id())
                    .then(RequiredArgumentBuilder.<S, String>argument("item", StringArgumentType.word())
                            .suggests(suggesting(FindableItem.idsFor(type)))
                            .executes(context -> handlers.find().run(context, type))));

            reset.then(LiteralArgumentBuilder.<S>literal(type.id())
                    .executes(context -> handlers.resetType().run(context, type)));
        }

        return LiteralArgumentBuilder.<S>literal("vaultp")
                .then(LiteralArgumentBuilder.<S>literal("count").executes(handlers.count()))
                .then(reset)
                .then(get)
                .then(find)
                .then(LiteralArgumentBuilder.<S>literal("seed")
                        .then(RequiredArgumentBuilder.<S, Long>argument("seed", LongArgumentType.longArg())
                                .then(sequenceArguments(handlers.seed()))))
                .then(LiteralArgumentBuilder.<S>literal("load")
                        .then(sequenceArguments(handlers.load())))
                .then(LiteralArgumentBuilder.<S>literal("watch").executes(handlers.watch()))
                .then(LiteralArgumentBuilder.<S>literal("maxGap")
                        .then(RequiredArgumentBuilder.<S, Integer>argument("gap",
                                        IntegerArgumentType.integer(MIN_MAX_GAP, MAX_MAX_GAP))
                                .executes(handlers.maxGap())))
                .then(LiteralArgumentBuilder.<S>literal("forget").executes(handlers.forget()));
    }

    public static int readMaxGap(CommandContext<?> context) {
        return IntegerArgumentType.getInteger(context, "gap");
    }

    private static <S> RequiredArgumentBuilder<S, Integer> countArgument() {
        return RequiredArgumentBuilder.argument("count",
                IntegerArgumentType.integer(MIN_GET_COUNT, MAX_GET_COUNT));
    }

    private static int readGetCount(CommandContext<?> context) {
        return IntegerArgumentType.getInteger(context, "count");
    }

    private static <S> RequiredArgumentBuilder<S, Integer> sequenceArguments(Command<S> executor) {
        return RequiredArgumentBuilder.<S, Integer>argument("count",
                        IntegerArgumentType.integer(
                                VaultGenerationRequest.MIN_COUNT,
                                VaultGenerationRequest.MAX_COUNT))
                .then(RequiredArgumentBuilder.<S, String>argument("method", StringArgumentType.word())
                        .suggests(suggesting(GenerationMethod.ids()))
                        .executes(executor));
    }

    private static <S> SuggestionProvider<S> suggesting(List<String> options) {
        return (context, builder) -> {
            String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (String option : options) {
                if (option.startsWith(prefix)) {
                    builder.suggest(option);
                }
            }
            return builder.buildFuture();
        };
    }

    public static VaultGenerationRequest readRequest(CommandContext<?> context) {
        return readRequest(context, LongArgumentType.getLong(context, "seed"));
    }

    public static VaultGenerationRequest readRequest(CommandContext<?> context, long seed) {
        return VaultGenerationRequest.of(
                seed,
                IntegerArgumentType.getInteger(context, "count"),
                StringArgumentType.getString(context, "method"));
    }

    public static FindableItem readFindTarget(CommandContext<?> context, VaultType type) {
        String id = StringArgumentType.getString(context, "item");

        FindableItem item = FindableItem.fromId(id).orElseThrow(() -> new IllegalArgumentException(
                "Unknown item '%s', expected one of %s"
                        .formatted(id, String.join(", ", FindableItem.idsFor(type)))));

        if (!item.supports(type)) {
            throw new IllegalArgumentException("%s is not a supported %s-vault search target"
                    .formatted(item.id(), type.id()));
        }

        return item;
    }

    private VaultForecastCommandTree() {
    }
}
