package dev.yae.vaultforecast.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.yae.vaultforecast.config.VaultForecastConfig;
import dev.yae.vaultforecast.generation.GeneratorContext;
import dev.yae.vaultforecast.generation.VaultGenerationRequest;
import dev.yae.vaultforecast.generation.VaultGenerationResult;
import dev.yae.vaultforecast.generation.VaultGenerationService;
import dev.yae.vaultforecast.generation.VaultSequenceGenerator;
import dev.yae.vaultforecast.loot.FindableItem;
import dev.yae.vaultforecast.loot.VaultType;
import dev.yae.vaultforecast.loot.display.LootDropChatFormatter;
import dev.yae.vaultforecast.loot.model.LootDrop;
import dev.yae.vaultforecast.observation.VaultDropObserver;
import dev.yae.vaultforecast.prediction.LootFinder;
import dev.yae.vaultforecast.session.VaultSession;
import dev.yae.vaultforecast.session.VaultSessionManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

public final class VaultForecastCommand {
    private static final LootDropChatFormatter FORMATTER = new LootDropChatFormatter();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(VaultForecastCommandTree.build(new VaultForecastCommandTree.Handlers<>(
                VaultForecastCommand::seed,
                VaultForecastCommand::load,
                VaultForecastCommand::count,
                VaultForecastCommand::watch,
                VaultForecastCommand::get,
                VaultForecastCommand::find,
                VaultForecastCommand::resetAll,
                VaultForecastCommand::resetType,
                VaultForecastCommand::maxGap,
                VaultForecastCommand::forget)));
    }

    private static int maxGap(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        int gap = VaultForecastCommandTree.readMaxGap(context);

        VaultForecastConfig.getInstance().setMaxGap(gap);

        source.sendFeedback(Text.literal("Max gap set to ").formatted(Formatting.GRAY)
                .append(Text.literal(String.valueOf(gap)).formatted(Formatting.WHITE)));

        return Command.SINGLE_SUCCESS;
    }

    private static int forget(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        int forgotten = VaultDropObserver.getInstance().forget();

        source.sendFeedback(forgotten == 0
                ? Text.literal("No vaults were being watched").formatted(Formatting.GRAY)
                : Text.literal("Stopped watching ").formatted(Formatting.GRAY)
                .append(Text.literal(String.valueOf(forgotten)).formatted(Formatting.WHITE))
                .append(Text.literal(forgotten == 1 ? " vault" : " vaults").formatted(Formatting.GRAY)));

        return Command.SINGLE_SUCCESS;
    }

    public static VaultSession session() {
        return VaultSessionManager.getInstance().session();
    }

    private static int get(CommandContext<FabricClientCommandSource> context, VaultType type, int count) {
        FabricClientCommandSource source = context.getSource();

        OptionalInt synced = syncedIndex(source, type);
        if (synced.isEmpty()) {
            return 0;
        }

        int currentIndex = synced.getAsInt();
        List<LootDrop> future = session().finder(type).orElseThrow().getFutureDrops(count);

        source.sendFeedback(header(type));

        for (int offset = 0; offset < future.size(); offset++) {
            int globalDropNumber = currentIndex + offset + 2;
            FORMATTER.format(future.get(offset), globalDropNumber, offset + 1).forEach(source::sendFeedback);
        }

        if (!future.isEmpty()) {
            source.sendFeedback(FORMATTER.separator());
        }

        if (future.size() < count) {
            source.sendFeedback(Text.literal("The loaded sequence ends here - %d of %d requested drops were available."
                    .formatted(future.size(), count)).formatted(Formatting.DARK_GRAY));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int changeGap(int value) {
        return 0;
    }

    private static int find(CommandContext<FabricClientCommandSource> context, VaultType type) {
        FabricClientCommandSource source = context.getSource();

        FindableItem target;
        try {
            target = VaultForecastCommandTree.readFindTarget(context, type);
        } catch (IllegalArgumentException exception) {
            source.sendFeedback(Text.literal(exception.getMessage()).formatted(Formatting.GRAY));
            return 0;
        }

        OptionalInt synced = syncedIndex(source, type);
        if (synced.isEmpty()) {
            return 0;
        }

        int currentIndex = synced.getAsInt();
        List<LootDrop> drops = session().drops(type);
        OptionalInt hit = target.firstMatch(drops, currentIndex + 1);

        if (hit.isEmpty()) {
            source.sendFeedback(Text.literal("No ").formatted(Formatting.GRAY)
                    .append(Text.literal(target.id()).formatted(Formatting.WHITE))
                    .append(Text.literal(" in the rest of the loaded %s sequence - try /vaultp load for a longer one"
                            .formatted(type.displayName())).formatted(Formatting.GRAY)));
            return 0;
        }

        int index = hit.getAsInt();
        source.sendFeedback(header(type));
        FORMATTER.format(drops.get(index), index + 1, index - currentIndex).forEach(source::sendFeedback);
        source.sendFeedback(FORMATTER.separator());

        return Command.SINGLE_SUCCESS;
    }

    private static Text header(VaultType type) {
        String name = type.displayName();
        return Text.literal(Character.toUpperCase(name.charAt(0)) + name.substring(1) + " vault forecast")
                .formatted(Formatting.GRAY);
    }

    private static OptionalInt syncedIndex(FabricClientCommandSource source, VaultType type) {
        if (!session().isLoaded()) {
            source.sendFeedback(Text.literal("No vault sequence is currently loaded").formatted(Formatting.GRAY));
            return OptionalInt.empty();
        }

        LootFinder finder = session().finder(type).orElse(null);
        if (finder == null) {
            source.sendFeedback(Text.literal("No %s vault sequence is currently loaded".formatted(type.displayName()))
                    .formatted(Formatting.GRAY));
            return OptionalInt.empty();
        }

        Set<Integer> candidates = finder.getCandidates();
        if (candidates.isEmpty()) {
            source.sendFeedback(Text.literal("The %s vault sequence is not synchronized".formatted(type.displayName()))
                    .formatted(Formatting.GRAY));
            return OptionalInt.empty();
        }

        if (candidates.size() > 1) {
            source.sendFeedback(Text.literal("Cannot predict exact drops while multiple candidates remain: ")
                    .formatted(Formatting.GRAY)
                    .append(Text.literal(String.valueOf(candidates.size())).formatted(Formatting.WHITE)));
            return OptionalInt.empty();
        }

        return OptionalInt.of(candidates.iterator().next());
    }

    private static int count(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();

        if (!session().isLoaded()) {
            source.sendFeedback(Text.literal("No vault sequence is currently loaded").formatted(Formatting.GRAY));
            return 0;
        }

        for (VaultType type : VaultType.values()) {
            MutableText line = Text.literal("Loaded %s drops: ".formatted(type.displayName()))
                    .formatted(Formatting.GRAY)
                    .append(Text.literal(String.valueOf(session().drops(type).size())).formatted(Formatting.WHITE));

            int candidates = session().finder(type).map(finder -> finder.getCandidates().size()).orElse(0);
            line.append(Text.literal("  candidates: ").formatted(Formatting.GRAY))
                    .append(Text.literal(String.valueOf(candidates)).formatted(Formatting.WHITE));

            source.sendFeedback(line);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int resetAll(CommandContext<FabricClientCommandSource> context) {
        for (VaultType type : VaultType.values()) {
            session().finder(type).ifPresent(finder -> finder.setCandidates(Set.of()));
        }

        VaultSessionManager.getInstance().save();
        context.getSource().sendFeedback(
                Text.literal("Cleared synchronization for both vault types").formatted(Formatting.GRAY));
        return Command.SINGLE_SUCCESS;
    }

    private static int resetType(CommandContext<FabricClientCommandSource> context, VaultType type) {
        session().finder(type).ifPresent(finder -> finder.setCandidates(Set.of()));

        VaultSessionManager.getInstance().save();
        context.getSource().sendFeedback(
                Text.literal("Cleared %s vault synchronization".formatted(type.displayName()))
                        .formatted(Formatting.GRAY));
        return Command.SINGLE_SUCCESS;
    }

    private static int watch(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        MinecraftClient client = source.getClient();

        if (client.world == null) {
            source.sendFeedback(Text.literal("No world is currently loaded").formatted(Formatting.GRAY));
            return 0;
        }

        HitResult target = client.crosshairTarget;
        if (!(target instanceof BlockHitResult blockTarget)) {
            source.sendFeedback(Text.literal("You are not looking at a block").formatted(Formatting.GRAY));
            return 0;
        }

        BlockPos pos = blockTarget.getBlockPos();
        source.sendFeedback(VaultDropObserver.getInstance().addWatching(client.world, pos));

        return Command.SINGLE_SUCCESS;
    }

    private static int seed(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();

        VaultGenerationRequest request;
        try {
            request = VaultForecastCommandTree.readRequest(context);
        } catch (IllegalArgumentException exception) {
            source.sendFeedback(Text.literal(exception.getMessage()).formatted(Formatting.GRAY));
            return 0;
        }

        return start(source, request, Map.of());
    }

    private static int load(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();

        VaultGenerationRequest current = session().request().orElse(null);
        if (current == null) {
            source.sendFeedback(Text.literal(
                            "No seed is loaded for this session - run /vaultp seed <seed> <count> <method> first")
                    .formatted(Formatting.GRAY));
            return 0;
        }

        VaultGenerationRequest request;
        try {
            request = VaultForecastCommandTree.readRequest(context, current.seed());
        } catch (IllegalArgumentException exception) {
            source.sendFeedback(Text.literal(exception.getMessage()).formatted(Formatting.GRAY));
            return 0;
        }

        Map<VaultType, Set<Integer>> carried = new EnumMap<>(VaultType.class);
        for (VaultType type : VaultType.values()) {
            session().finder(type).ifPresent(finder -> carried.put(type, finder.getCandidates()));
        }

        return start(source, request, carried);
    }

    private static int start(
            FabricClientCommandSource source,
            VaultGenerationRequest request,
            Map<VaultType, Set<Integer>> carriedCandidates
    ) {
        MinecraftClient client = source.getClient();
        GeneratorContext generatorContext = new GeneratorContext(
                source.getWorld().getRegistryManager(),
                VaultSessionManager.directory());
        VaultSequenceGenerator generator = request.method().createGenerator(generatorContext);

        return VaultSessionManager.getInstance().service(client)
                .start(request, generator, new ChatSink(client, Map.copyOf(carriedCandidates)))
                ? Command.SINGLE_SUCCESS
                : 0;
    }

    private record ChatSink(MinecraftClient client, Map<VaultType, Set<Integer>> carriedCandidates)
            implements VaultGenerationService.Sink {
        @Override
        public void message(VaultGenerationService.MessageKind kind, String text) {
            Formatting colour = switch (kind) {
                case INFO -> Formatting.GRAY;
                case PROGRESS -> Formatting.DARK_GRAY;
                case SUCCESS -> Formatting.WHITE;
                case ERROR -> Formatting.RED;
            };
            send(Text.literal(text).formatted(colour));
        }

        @Override
        public void completed(VaultGenerationResult result) {
            session().load(result);
            restoreCandidates(result.countPerType());
            VaultSessionManager.getInstance().save();

            send(Text.literal("Loaded ").formatted(Formatting.GRAY)
                    .append(Text.literal(String.valueOf(result.countPerType())).formatted(Formatting.WHITE))
                    .append(Text.literal(" normal drops and ").formatted(Formatting.GRAY))
                    .append(Text.literal(String.valueOf(result.countPerType())).formatted(Formatting.WHITE))
                    .append(Text.literal(" ominous drops.").formatted(Formatting.GRAY)));
            send(Text.literal("Seed: ").formatted(Formatting.GRAY)
                    .append(Text.literal(String.valueOf(result.seed())).formatted(Formatting.WHITE)));
            send(Text.literal("Method: ").formatted(Formatting.GRAY)
                    .append(Text.literal(result.method().displayName()).formatted(Formatting.WHITE)));
        }

        private void restoreCandidates(int sequenceSize) {
            if (carriedCandidates.isEmpty()) {
                return;
            }

            int kept = 0;
            for (VaultType type : VaultType.values()) {
                Set<Integer> stillValid = carriedCandidates.getOrDefault(type, Set.of()).stream()
                        .filter(index -> index < sequenceSize)
                        .collect(Collectors.toSet());

                session().finder(type).ifPresent(finder -> finder.setCandidates(stillValid));
                kept += stillValid.size();
            }

            send(kept == 0
                    ? Text.literal("The new sequences no longer cover where you were - sync was lost.")
                    .formatted(Formatting.GRAY)
                    : Text.literal("Kept %d candidate%s from the previous sequences."
                    .formatted(kept, kept == 1 ? "" : "s")).formatted(Formatting.GRAY));
        }

        private void send(Text text) {
            if (client.player != null) {
                client.player.sendMessage(text, false);
            }
        }
    }

    private VaultForecastCommand() {
    }
}
