package dev.yae.vaultforecast.generation;

public interface VaultSequenceGenerator {
    VaultGenerationResult generate(
            VaultGenerationRequest request,
            GenerationProgressListener progressListener
    ) throws Exception;
}
