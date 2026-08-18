package dev.yae.vaultforecast.session;

import dev.yae.vaultforecast.generation.GeneratorContext;
import dev.yae.vaultforecast.generation.VaultGenerationRequest;
import dev.yae.vaultforecast.generation.VaultGenerationResult;
import dev.yae.vaultforecast.generation.VaultGenerationService;
import dev.yae.vaultforecast.loot.VaultType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class VaultSessionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("vaultforecast");

    private static final int AUTOSAVE_INTERVAL_TICKS = 100;

    private static final VaultSessionManager INSTANCE = new VaultSessionManager();

    private final VaultSession sessionState = new VaultSession();
    private final VaultSessionStorage storage = new VaultSessionStorage(directory().resolve("sessions"));

    private String session;
    private Map<VaultType, Set<Integer>> lastSavedCandidates;
    private int tickCounter;
    private VaultGenerationService service;

    public static VaultSessionManager getInstance() {
        return INSTANCE;
    }

    public static Path directory() {
        return FabricLoader.getInstance().getGameDir().resolve("vaultforecast");
    }

    public VaultSession session() {
        return sessionState;
    }

    public Optional<String> sessionKey() {
        return Optional.ofNullable(session);
    }

    public synchronized VaultGenerationService service(Executor clientThread) {
        if (service == null) {
            service = VaultGenerationService.create(clientThread);
        }
        return service;
    }

    public void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onJoin(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnect());
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onJoin(MinecraftClient client) {
        session = SessionKey.current(client).orElse(null);
        lastSavedCandidates = null;

        if (session == null) {
            return;
        }

        storage.load(session).ifPresent(saved -> restore(client, saved));
    }

    private void onDisconnect() {
        save();
        session = null;
        lastSavedCandidates = null;
        sessionState.clear();
    }

    private void onTick(MinecraftClient client) {
        if (session == null || ++tickCounter < AUTOSAVE_INTERVAL_TICKS) {
            return;
        }

        tickCounter = 0;

        Map<VaultType, Set<Integer>> candidates = currentCandidates();
        if (!candidates.isEmpty() && !candidates.equals(lastSavedCandidates)) {
            save();
        }
    }

    public void save() {
        if (session == null) {
            return;
        }

        Optional<VaultGenerationRequest> request = sessionState.request();
        Map<VaultType, Set<Integer>> candidates = currentCandidates();

        if (request.isEmpty() || candidates.isEmpty()) {
            return;
        }

        VaultGenerationRequest loaded = request.get();
        storage.save(new SavedSession(session, loaded.seed(), loaded.count(), loaded.method(), candidates));
        lastSavedCandidates = candidates;
    }

    public String forget() {
        if (session == null) {
            return "Not connected to a server or world, so there is nothing to reset.";
        }

        boolean deleted = storage.delete(session);
        sessionState.clear();
        lastSavedCandidates = null;

        return deleted
                ? "Cleared the saved vault forecast for " + session + "."
                : "There was no saved vault forecast for " + session + ".";
    }

    public void restore(MinecraftClient client, SavedSession saved) {
        if (client.world == null) {
            LOGGER.warn("Joined {} without a world; skipping the saved session", saved.session());
            return;
        }

        VaultGenerationRequest request = saved.request();
        message(client, "Restoring %d drops per vault type for seed %d (%s)...".formatted(
                request.count(), request.seed(), request.method().displayName()), Formatting.GRAY);

        GeneratorContext context = new GeneratorContext(client.world.getRegistryManager(), directory());

        service(client).start(
                request,
                request.method().createGenerator(context),
                new RestoreSink(client, saved, this));
    }

    private Map<VaultType, Set<Integer>> currentCandidates() {
        if (!sessionState.isLoaded()) {
            return Map.of();
        }

        Map<VaultType, Set<Integer>> candidates = new EnumMap<>(VaultType.class);
        for (VaultType type : VaultType.values()) {
            sessionState.finder(type).ifPresent(finder -> candidates.put(type, finder.getCandidates()));
        }
        return candidates;
    }

    static void message(MinecraftClient client, String text, Formatting colour) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal(text).formatted(colour), false);
        }
    }

    private record RestoreSink(MinecraftClient client, SavedSession saved, VaultSessionManager manager)
            implements VaultGenerationService.Sink {
        @Override
        public void message(VaultGenerationService.MessageKind kind, String text) {
            if (kind == VaultGenerationService.MessageKind.ERROR) {
                VaultSessionManager.message(client, "Could not restore the saved forecast: " + text, Formatting.RED);
            }
        }

        @Override
        public void completed(VaultGenerationResult result) {
            if (manager.sessionKey().filter(saved.session()::equals).isEmpty()) {
                LOGGER.info("Discarding a restore for {}: the player has since left", saved.session());
                return;
            }

            manager.session().load(result);

            for (VaultType type : VaultType.values()) {
                manager.session().finder(type)
                        .ifPresent(finder -> finder.setCandidates(saved.candidates(type)));
            }

            LOGGER.info("Restored {} drops per type and {} candidates for {}",
                    result.countPerType(), saved.totalCandidates(), saved.session());

            VaultSessionManager.message(client,
                    "Restored %d drops per vault type for seed %d (%d candidate%s)."
                            .formatted(result.countPerType(), result.seed(), saved.totalCandidates(),
                                    saved.totalCandidates() == 1 ? "" : "s"),
                    Formatting.GREEN);
        }
    }

    public void withService(Executor clientThread, Consumer<VaultGenerationService> action) {
        action.accept(service(clientThread));
    }

    private VaultSessionManager() {
    }
}
