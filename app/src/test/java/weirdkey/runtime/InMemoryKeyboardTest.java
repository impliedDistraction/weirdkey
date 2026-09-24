package weirdkey.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InMemoryKeyboardTest {
    @Test
    void emitsPressHoldAndReleaseEventsToListeners() {
        InMemoryKeyboard keyboard = new InMemoryKeyboard(new KeyboardTopology(List.of(new KeyDefinition("A", 0, 0))));
        List<KeyInputEvent> seenEvents = new ArrayList<>();

        keyboard.addInputListener(seenEvents::add);
        keyboard.emit(new KeyInputEvent("A", InputType.PRESS));
        keyboard.emit(new KeyInputEvent("A", InputType.HOLD));
        keyboard.emit(new KeyInputEvent("A", InputType.RELEASE));

        assertEquals(
            List.of(
                new KeyInputEvent("A", InputType.PRESS),
                new KeyInputEvent("A", InputType.HOLD),
                new KeyInputEvent("A", InputType.RELEASE)
            ),
            seenEvents
        );
    }

    @Test
    void tracksConfiguredCapturedKeys() {
        InMemoryKeyboard keyboard = new InMemoryKeyboard(
            new KeyboardTopology(List.of(new KeyDefinition("A", 0, 0), new KeyDefinition("B", 0, 1)))
        );

        keyboard.captureInputKeys(Set.of("A", "B"));

        assertEquals(Set.of("A", "B"), keyboard.capturedKeyIds());
    }
}
