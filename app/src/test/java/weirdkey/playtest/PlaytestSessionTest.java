package weirdkey.playtest;

import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import weirdkey.runtime.InMemoryKeyboard;
import weirdkey.runtime.InputType;
import weirdkey.runtime.KeyColor;
import weirdkey.runtime.KeyDefinition;
import weirdkey.runtime.KeyInputEvent;
import weirdkey.runtime.KeyboardTopology;

class PlaytestSessionTest {
    @TempDir
    java.nio.file.Path temporaryDirectory;

    @Test
    void recordsArtifactsWithoutLoggingUncapturedInput() throws Exception {
        PlaytestSession session = PlaytestSession.start(temporaryDirectory, "simulation");
        InMemoryKeyboard delegate = new InMemoryKeyboard(new KeyboardTopology(List.of(
            new KeyDefinition("A", 0, 0),
            new KeyDefinition("B", 0, 1)
        )));

        try (session; RecordingKeyboardDevice keyboard = new RecordingKeyboardDevice(delegate, session)) {
            keyboard.captureInputKeys(Set.of("A"));
            delegate.emit(new KeyInputEvent("A", InputType.PRESS));
            delegate.emit(new KeyInputEvent("B", InputType.PRESS));
            keyboard.setColor("A", KeyColor.GREEN);
            session.record(message -> {
            }).showStatus("Visible status");
        }

        String events = Files.readString(session.directory().resolve("events.jsonl"));
        assertTrue(events.contains("\"keyId\":\"A\""));
        assertFalse(events.contains("\"keyId\":\"B\""));
        assertTrue(events.contains("Visible status"));
        assertTrue(Files.readString(session.directory().resolve("metadata.json")).contains("\"status\": \"completed\""));
        assertTrue(Files.readString(session.directory().resolve("summary.md")).contains("Captured inputs: 1"));
        assertTrue(Files.isRegularFile(session.directory().resolve("notes.md")));
    }
}