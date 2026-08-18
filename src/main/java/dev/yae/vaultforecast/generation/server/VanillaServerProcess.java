package dev.yae.vaultforecast.generation.server;

import dev.yae.vaultforecast.generation.VaultGenerationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class VanillaServerProcess implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("vaultforecast");

    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration KILL_TIMEOUT = Duration.ofSeconds(10);
    private static final int RECENT_LINE_LIMIT = 60;

    private final Process process;
    private final BufferedWriter console;
    private final Thread reader;
    private final BlockingQueue<String> blockData = new LinkedBlockingQueue<>();
    private final CountDownLatch startup = new CountDownLatch(1);
    private final Deque<String> recentLines = new ArrayDeque<>();

    private volatile boolean startupComplete;

    private VanillaServerProcess(Process process) {
        this.process = process;
        this.console = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.reader = new Thread(this::readConsole, "vaultforecast-server-console");
        this.reader.setDaemon(true);
        this.reader.start();
    }

    public static VanillaServerProcess start(ServerInstallation installation) throws VaultGenerationException {
        List<String> command = List.of(javaExecutable(), "-Xmx1G", "-jar", installation.jar().getFileName().toString(), "nogui");
        LOGGER.info("Starting the generator server: {} (in {})", String.join(" ", command), installation.directory());

        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(installation.directory().toFile())
                .redirectErrorStream(true);

        try {
            return new VanillaServerProcess(builder.start());
        } catch (IOException exception) {
            throw new VaultGenerationException(
                    "Could not start the vanilla server process. Is a Java runtime available?", exception);
        }
    }

    private static String javaExecutable() {
        Path home = Path.of(System.getProperty("java.home"));
        Path executable = home.resolve("bin").resolve(System.getProperty("os.name", "").toLowerCase().startsWith("win")
                ? "java.exe"
                : "java");
        return java.nio.file.Files.isExecutable(executable) ? executable.toString() : "java";
    }

    public void awaitStartup(Duration timeout) throws VaultGenerationException {
        try {
            if (startup.await(timeout.toMillis(), TimeUnit.MILLISECONDS) && startupComplete) {
                return;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new VaultGenerationException("Generation was cancelled while the server was starting.", exception);
        }

        if (!process.isAlive()) {
            throw new VaultGenerationException(
                    "The vanilla server exited during startup (exit code %d). %s"
                            .formatted(process.exitValue(), seeLogHint()));
        }

        throw new VaultGenerationException(
                "The vanilla server did not finish starting within %d seconds. %s"
                        .formatted(timeout.toSeconds(), seeLogHint()));
    }

    public void send(String command) throws VaultGenerationException {
        try {
            console.write(command);
            console.newLine();
        } catch (IOException exception) {
            throw new VaultGenerationException("Lost the connection to the vanilla server console.", exception);
        }
    }

    public void flush() throws VaultGenerationException {
        try {
            console.flush();
        } catch (IOException exception) {
            throw new VaultGenerationException("Lost the connection to the vanilla server console.", exception);
        }
    }

    public List<String> awaitBlockData(int expected, Duration timeout) throws VaultGenerationException {
        List<String> responses = new ArrayList<>(expected);
        long deadline = System.nanoTime() + timeout.toNanos();

        while (responses.size() < expected) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new VaultGenerationException(
                        "The vanilla server stopped answering: expected %d loot results, got %d. %s"
                                .formatted(expected, responses.size(), seeLogHint()));
            }

            String response;
            try {
                response = blockData.poll(Math.min(remaining, TimeUnit.SECONDS.toNanos(1)), TimeUnit.NANOSECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new VaultGenerationException("Generation was cancelled.", exception);
            }

            if (response != null) {
                responses.add(response);
            } else if (!process.isAlive() && blockData.isEmpty()) {
                throw new VaultGenerationException(
                        "The vanilla server exited unexpectedly (exit code %d) after %d of %d loot results. %s"
                                .formatted(process.exitValue(), responses.size(), expected, seeLogHint()));
            }
        }

        return responses;
    }

    public List<String> recentOutput() {
        synchronized (recentLines) {
            return List.copyOf(recentLines);
        }
    }

    @Override
    public void close() {
        try {
            if (process.isAlive()) {
                try {
                    send("stop");
                    flush();
                } catch (VaultGenerationException exception) {
                    LOGGER.warn("Could not ask the generator server to stop", exception);
                }

                if (!process.waitFor(STOP_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                    LOGGER.warn("The generator server ignored 'stop'; terminating it");
                    process.destroy();

                    if (!process.waitFor(KILL_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                    }
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        } finally {
            closeStreams();
            reader.interrupt();
        }
    }

    private void closeStreams() {
        try {
            console.close();
        } catch (IOException exception) {
            LOGGER.debug("Could not close the server console stream", exception);
        }
    }

    private void readConsole() {
        try (BufferedReader input = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = input.readLine()) != null) {
                remember(line);
                LOGGER.debug("[server] {}", line);

                ServerConsoleParser.readBlockData(line).ifPresent(blockData::add);
                if (!startupComplete && ServerConsoleParser.isStartupComplete(line)) {
                    startupComplete = true;
                    startup.countDown();
                }
            }
        } catch (IOException exception) {
            LOGGER.debug("The generator server console closed", exception);
        } finally {

            startup.countDown();
        }
    }

    private void remember(String line) {
        synchronized (recentLines) {
            recentLines.addLast(line);
            while (recentLines.size() > RECENT_LINE_LIMIT) {
                recentLines.removeFirst();
            }
        }
    }

    private static String seeLogHint() {
        return "See the game log for the server console output.";
    }
}
