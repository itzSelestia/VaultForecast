package dev.yae.vaultforecast.generation;

import dev.yae.vaultforecast.loot.VaultType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class VaultGenerationService {
    private static final Logger LOGGER = LoggerFactory.getLogger("vaultforecast");

    private static final int PROGRESS_STEPS = 10;

    public enum MessageKind {
        INFO,
        PROGRESS,
        SUCCESS,
        ERROR
    }

    public interface Sink {
        void message(MessageKind kind, String text);

        void completed(VaultGenerationResult result);
    }

    private final ExecutorService worker;
    private final Executor clientThread;
    private final AtomicBoolean running = new AtomicBoolean();

    public VaultGenerationService(ExecutorService worker, Executor clientThread) {
        this.worker = worker;
        this.clientThread = clientThread;
    }

    public static VaultGenerationService create(Executor clientThread) {
        return new VaultGenerationService(Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "vaultforecast-generation");
            thread.setDaemon(true);
            return thread;
        }), clientThread);
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean start(VaultGenerationRequest request, VaultSequenceGenerator generator, Sink sink) {
        if (!running.compareAndSet(false, true)) {
            sink.message(MessageKind.ERROR, "A generation is already running. Wait for it to finish.");
            return false;
        }

        sink.message(MessageKind.INFO, "Generating %d drops per vault type for seed %d using %s..."
                .formatted(request.count(), request.seed(), request.method().displayName()));

        worker.execute(() -> {
            try {
                run(request, generator, sink);
            } finally {
                running.set(false);
            }
        });

        return true;
    }

    private void run(VaultGenerationRequest request, VaultSequenceGenerator generator, Sink sink) {
        try {
            VaultGenerationResult result = generator.generate(request, listenerFor(request, sink));

            for (VaultType type : VaultType.values()) {
                int produced = result.drops(type).size();
                if (produced != request.count()) {
                    throw new IllegalStateException("Generator returned %d %s drops instead of %d"
                            .formatted(produced, type.id(), request.count()));
                }
            }

            clientThread.execute(() -> sink.completed(result));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            report(sink, "Generation was cancelled.");
        } catch (Exception exception) {
            LOGGER.error("Vault sequence generation failed ({}, seed {}, count {})",
                    request.method().id(), request.seed(), request.count(), exception);
            report(sink, message(exception));
        }
    }

    private GenerationProgressListener listenerFor(VaultGenerationRequest request, Sink sink) {
        int step = Math.max(1, request.count() / PROGRESS_STEPS);

        return new GenerationProgressListener() {
            private VaultType lastType;
            private int lastReported;

            @Override
            public void onStage(String message) {
                clientThread.execute(() -> sink.message(MessageKind.INFO, message));
            }

            @Override
            public void onProgress(VaultType type, int generated, int total) {
                if (type != lastType) {
                    lastType = type;
                    lastReported = 0;
                }

                if (generated >= total || generated - lastReported < step) {
                    return;
                }

                lastReported = generated;
                clientThread.execute(() -> sink.message(MessageKind.PROGRESS,
                        "Generating %s drops: %d / %d".formatted(type.displayName(), generated, total)));
            }
        };
    }

    private void report(Sink sink, String text) {
        clientThread.execute(() -> sink.message(MessageKind.ERROR, text));
    }

    private static String message(Exception exception) {
        String detail = exception.getMessage();
        return detail == null || detail.isBlank()
                ? "Generation failed: " + exception.getClass().getSimpleName() + " (see the log)"
                : detail;
    }
}
