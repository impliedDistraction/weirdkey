package weirdkey.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import weirdkey.runtime.events.EventEnvelope;
import weirdkey.runtime.events.EventTags;

class CartridgeRuntimeTest {
    @Test
    void letsCartridgesDeclareCapturedInputKeys() {
        InMemoryKeyboard keyboard = new InMemoryKeyboard(
            new KeyboardTopology(List.of(new KeyDefinition("A", 0, 0), new KeyDefinition("B", 0, 1)))
        );
        CartridgeRuntime runtime = new CartridgeRuntime(
            keyboard,
            Optional.empty(),
            new Cartridge() {
                @Override
                public void start(GameContext context) {
                    context.captureInputKeys(List.of("A", "B"));
                }

                @Override
                public void onInput(GameContext context, KeyInputEvent event) {
                }
            }
        );

        runtime.start();

        assertEquals(Set.of("A", "B"), keyboard.capturedKeyIds());
    }

    @Test
    void publishesKeyboardInputWithDeviceContextAndTags() {
        InMemoryKeyboard keyboard = new InMemoryKeyboard(
            new KeyboardTopology(List.of(new KeyDefinition("A", 0, 0)))
        );
        List<EventEnvelope<KeyInputEvent>> seen = new java.util.ArrayList<>();
        Cartridge cartridge = new Cartridge() {
            @Override
            public void start(GameContext context) {
                context.events().subscribe(KeyInputEvent.class, Set.of(EventTags.INPUT), seen::add);
            }

            @Override
            public void onInput(GameContext context, KeyInputEvent event) {
            }
        };

        try (CartridgeRuntime runtime = new CartridgeRuntime(keyboard, Optional.empty(), cartridge)) {
            runtime.start();
            keyboard.emit(new KeyInputEvent("A", InputType.PRESS));
        }

        assertEquals(1, seen.size());
        assertEquals(new KeyInputEvent("A", InputType.PRESS), seen.get(0).event());
        assertEquals(Optional.of(keyboard), seen.get(0).emitterAs(InMemoryKeyboard.class));
        assertEquals(Set.of(EventTags.INPUT, EventTags.KEYBOARD), seen.get(0).tags());
    }
}
