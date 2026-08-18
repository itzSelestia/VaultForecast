package dev.yae.vaultforecast.generation.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yae.vaultforecast.generation.GenerationProgressListener;
import dev.yae.vaultforecast.generation.VaultGenerationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

public final class ServerJarProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger("vaultforecast");

    private static final URI VERSION_MANIFEST =
            URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    private static final Duration TIMEOUT = Duration.ofMinutes(5);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public Path obtain(Path directory, String minecraftVersion, GenerationProgressListener listener)
            throws VaultGenerationException {
        Path jar = directory.resolve("server.jar");
        Download download = resolveDownload(minecraftVersion);

        if (Files.isRegularFile(jar) && sha1(jar).equalsIgnoreCase(download.sha1())) {
            return jar;
        }

        listener.onStage("Downloading the official %s server jar (%.1f MB)...".formatted(
                minecraftVersion, download.size() / 1024.0 / 1024.0));

        Path temporary = directory.resolve("server.jar.partial");
        try {
            Files.deleteIfExists(temporary);
            HttpResponse<Path> response = http.send(
                    HttpRequest.newBuilder(URI.create(download.url())).timeout(TIMEOUT).GET().build(),
                    HttpResponse.BodyHandlers.ofFile(temporary));

            if (response.statusCode() != 200) {
                throw new VaultGenerationException(
                        "Could not download the official server jar (HTTP %d).".formatted(response.statusCode()));
            }
        } catch (IOException exception) {
            throw new VaultGenerationException("Could not download the official server jar.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new VaultGenerationException("Generation was cancelled while downloading the server jar.", exception);
        }

        String actual = sha1(temporary);
        if (!actual.equalsIgnoreCase(download.sha1())) {
            deleteQuietly(temporary);
            throw new VaultGenerationException(
                    "The downloaded server jar failed its SHA-1 check (expected %s, got %s). Nothing was installed."
                            .formatted(download.sha1(), actual));
        }

        try {
            Files.move(temporary, jar, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new VaultGenerationException("Could not store the downloaded server jar.", exception);
        }

        LOGGER.info("Cached the official {} server jar at {}", minecraftVersion, jar);
        return jar;
    }

    private Download resolveDownload(String minecraftVersion) throws VaultGenerationException {
        JsonObject manifest = fetchJson(VERSION_MANIFEST, "the official version manifest");

        JsonArray versions = manifest.getAsJsonArray("versions");
        if (versions == null) {
            throw new VaultGenerationException("Mojang's version manifest did not contain any versions.");
        }

        String versionUrl = null;
        for (JsonElement element : versions) {
            JsonObject version = element.getAsJsonObject();
            if (minecraftVersion.equals(version.get("id").getAsString())) {
                versionUrl = version.get("url").getAsString();
                break;
            }
        }

        if (versionUrl == null) {
            throw new VaultGenerationException(
                    "Mojang's version manifest has no entry for Minecraft %s.".formatted(minecraftVersion));
        }

        JsonObject version = fetchJson(URI.create(versionUrl), "the official metadata for " + minecraftVersion);
        JsonObject downloads = version.getAsJsonObject("downloads");
        JsonObject server = downloads == null ? null : downloads.getAsJsonObject("server");
        if (server == null) {
            throw new VaultGenerationException(
                    "Mojang's metadata for Minecraft %s has no dedicated server download.".formatted(minecraftVersion));
        }

        return new Download(server.get("url").getAsString(), server.get("sha1").getAsString(), server.get("size").getAsLong());
    }

    private JsonObject fetchJson(URI uri, String what) throws VaultGenerationException {
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new VaultGenerationException(
                        "Could not download %s (HTTP %d).".formatted(what, response.statusCode()));
            }

            return JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (IOException | RuntimeException exception) {
            throw new VaultGenerationException("Could not download %s.".formatted(what), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new VaultGenerationException("Generation was cancelled while downloading %s.".formatted(what), exception);
        }
    }

    private static String sha1(Path file) throws VaultGenerationException {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = input.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new VaultGenerationException("Could not hash the server jar for verification.", exception);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            LOGGER.warn("Could not delete {}", path, exception);
        }
    }

    private record Download(String url, String sha1, long size) {
    }
}
