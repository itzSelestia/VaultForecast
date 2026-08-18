package dev.yae.vaultforecast.generation;

import net.minecraft.registry.RegistryWrapper;

import java.nio.file.Path;

public record GeneratorContext(RegistryWrapper.WrapperLookup registries, Path generatorDirectory) {
}
