package weirdkey.playtest;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import weirdkey.runtime.CartridgeResult;
import weirdkey.runtime.DisplaySurface;

public final class PlaytestSession implements AutoCloseable {
    private static final DateTimeFormatter SESSION_ID = DateTimeFormatter
        .ofPattern("uuuuMMdd-HHmmss-SSS")
        .withZone(ZoneOffset.UTC);

    private final Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private final Gson compactGson = new GsonBuilder().disableHtmlEscaping().create();
    private final Path directory;
    private final Instant startedAt = Instant.now();
    private final long startedNanos = System.nanoTime();
    private final String mode;
    private final BufferedWriter events;
    private final Map<String, Integer> eventCounts = new LinkedHashMap<>();
    private final List<String> inputSequence = new ArrayList<>();
    private final List<String> statusTimeline = new ArrayList<>();
    private final List<CartridgeResult> results = new ArrayList<>();
    private Throwable failure;
    private boolean closed;

    private PlaytestSession(Path rootDirectory, String mode) {
        this.mode = mode;
        this.directory = rootDirectory.resolve(SESSION_ID.format(startedAt));
        try {
            Files.createDirectories(directory);
            events = Files.newBufferedWriter(
                directory.resolve("events.jsonl"),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
            );
            writeMetadata("running", null);
            writeNotesTemplate();
            record("session_start", Map.of("mode", mode));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not start playtest recording in " + directory, exception);
        }
    }

    public static PlaytestSession start(String mode) {
        String override = System.getenv("WEIRDKEY_PLAYTEST_DIR");
        Path rootDirectory = override == null || override.isBlank()
            ? defaultRootDirectory()
            : Path.of(override);
        return new PlaytestSession(rootDirectory, mode);
    }

    static PlaytestSession start(Path rootDirectory, String mode) {
        return new PlaytestSession(rootDirectory, mode);
    }

    public Path directory() {
        return directory;
    }

    public DisplaySurface record(DisplaySurface displaySurface) {
        return message -> {
            displaySurface.showStatus(message);
            record("status", Map.of("message", message));
        };
    }

    public synchronized void record(String type, Map<String, ?> details) {
        requireOpen();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("elapsedMs", elapsedMillis());
        event.put("type", type);
        event.putAll(details);
        try {
            events.write(compactGson.toJson(event));
            events.newLine();
            events.flush();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write playtest event", exception);
        }
        eventCounts.merge(type, 1, Integer::sum);
        if ("input".equals(type)) {
            inputSequence.add(details.get("inputType") + ":" + details.get("keyId"));
        } else if ("status".equals(type)) {
            statusTimeline.add(event.get("elapsedMs") + " ms: " + details.get("message"));
        }
    }

    public synchronized void recordResult(CartridgeResult result) {
        results.add(result);
        record("cartridge_result", Map.of(
            "resultType", result.getClass().getName(),
            "result", result
        ));
    }

    public synchronized void recordFailure(Throwable throwable) {
        failure = throwable;
        StringWriter stackTrace = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stackTrace));
        record("failure", Map.of(
            "exception", throwable.getClass().getName(),
            "message", String.valueOf(throwable.getMessage()),
            "stackTrace", stackTrace.toString()
        ));
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }

        record("session_end", Map.of("status", failure == null ? "completed" : "failed"));
        closed = true;
        try {
            events.close();
            writeMetadata(failure == null ? "completed" : "failed", Instant.now());
            writeSummary();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not finish playtest recording in " + directory, exception);
        }
    }

    private long elapsedMillis() {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    private void writeMetadata(String status, Instant endedAt) throws IOException {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("startedAt", startedAt.toString());
        metadata.put("endedAt", endedAt == null ? null : endedAt.toString());
        metadata.put("status", status);
        metadata.put("mode", mode);
        metadata.put("gitCommit", gitValue("rev-parse", "HEAD"));
        metadata.put("gitBranch", gitValue("branch", "--show-current"));
        metadata.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        metadata.put("java", System.getProperty("java.version"));
        if (failure != null) {
            metadata.put("failure", failure.getClass().getName() + ": " + failure.getMessage());
        }
        Files.writeString(directory.resolve("metadata.json"), gson.toJson(metadata), StandardCharsets.UTF_8);
    }

    private void writeSummary() throws IOException {
        StringBuilder summary = new StringBuilder();
        summary.append("# Playtest Summary\n\n");
        summary.append("- Status: ").append(failure == null ? "completed" : "failed").append('\n');
        summary.append("- Mode: ").append(mode).append('\n');
        summary.append("- Duration: ").append(elapsedMillis()).append(" ms\n");
        summary.append("- Captured inputs: ").append(eventCounts.getOrDefault("input", 0)).append('\n');
        summary.append("- LED changes: ").append(eventCounts.getOrDefault("led_set", 0) + eventCounts.getOrDefault("led_clear", 0)).append('\n');
        summary.append("- Status messages: ").append(eventCounts.getOrDefault("status", 0)).append("\n\n");
        summary.append("## Input Sequence\n\n");
        summary.append(inputSequence.isEmpty() ? "No captured input.\n" : String.join(" -> ", inputSequence) + "\n");
        summary.append("\n## Status Timeline\n\n");
        if (statusTimeline.isEmpty()) {
            summary.append("No status messages.\n");
        } else {
            statusTimeline.forEach(status -> summary.append("- ").append(status.replace(System.lineSeparator(), " / ")).append('\n'));
        }
        summary.append("\n## Cartridge Results\n\n");
        summary.append(results.isEmpty() ? "No cartridge result recorded.\n" : "```json\n" + gson.toJson(results) + "\n```\n");
        Files.writeString(directory.resolve("summary.md"), summary, StandardCharsets.UTF_8);
    }

    private void writeNotesTemplate() throws IOException {
        String notes = """
            # Playtest Notes

            Participant alias:
            Observer:
            Video/audio filename:

            ## During The Session

            Add timestamps for hesitation, surprise, confusion, delight, verbal hypotheses, and requests for help.

            ## Debrief

            - What did you think the lights represented at first?
            - When did you notice that you had a choice?
            - What did holding SPACE seem to do?
            - Did the numpad light feel related to your movement? When did that become clear?
            - What did you think happened at ESC?
            - Where did you feel stuck or tempted to stop?
            """;
        Files.writeString(directory.resolve("notes.md"), notes, StandardCharsets.UTF_8);
    }

    private static String gitValue(String... arguments) {
        try {
            List<String> command = new ArrayList<>();
            command.add("git");
            command.addAll(List.of(arguments));
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return process.waitFor() == 0 ? output : "unknown";
        } catch (IOException exception) {
            return "unknown";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "unknown";
        }
    }

    private static Path defaultRootDirectory() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("settings.gradle"))
                && Files.isDirectory(current.resolve("cartridges"))) {
                return current.resolve("playtests").resolve("sessions");
            }
            current = current.getParent();
        }
        return Path.of("playtests", "sessions");
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Playtest session is closed");
        }
    }
}