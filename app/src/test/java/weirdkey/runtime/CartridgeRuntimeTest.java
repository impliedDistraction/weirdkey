package weirdkey.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

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
}
