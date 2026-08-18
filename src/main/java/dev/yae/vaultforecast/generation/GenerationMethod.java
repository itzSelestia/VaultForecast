package dev.yae.vaultforecast.generation;

import dev.yae.vaultforecast.generation.fast.FastVaultSequenceGenerator;
import dev.yae.vaultforecast.generation.server.VanillaServerSequenceGenerator;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum GenerationMethod {
    FAST("fast", "fast"),

    SERVER("server", "vanilla server (beta)");

    private final String id;
    private final String displayName;

    GenerationMethod(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public static Optional<GenerationMethod> fromId(String id) {
        String normalised = id.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(method -> method.id.equals(normalised))
                .findFirst();
    }

    public static List<String> ids() {
        return Arrays.stream(values()).map(GenerationMethod::id).toList();
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public VaultSequenceGenerator createGenerator(GeneratorContext context) {
        return switch (this) {
            case FAST -> new FastVaultSequenceGenerator(context.registries());
            case SERVER -> new VanillaServerSequenceGenerator(context.generatorDirectory(), context.registries());
        };
    }
}
