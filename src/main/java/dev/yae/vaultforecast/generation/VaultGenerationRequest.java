package dev.yae.vaultforecast.generation;

public record VaultGenerationRequest(long seed, int count, GenerationMethod method) {
    public static final int MIN_COUNT = 1;
    public static final int MAX_COUNT = 100_000;

    public VaultGenerationRequest {
        if (count < MIN_COUNT || count > MAX_COUNT) {
            throw new IllegalArgumentException(
                    "Count must be between %d and %d, got %d".formatted(MIN_COUNT, MAX_COUNT, count));
        }
    }

    public static VaultGenerationRequest of(long seed, int count, String methodId) {
        GenerationMethod method = GenerationMethod.fromId(methodId).orElseThrow(() ->
                new IllegalArgumentException(
                        "Unknown method '%s', expected one of %s".formatted(methodId, String.join(", ", GenerationMethod.ids()))));

        return new VaultGenerationRequest(seed, count, method);
    }
}
