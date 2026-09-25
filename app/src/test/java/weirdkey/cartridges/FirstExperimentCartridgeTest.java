package weirdkey.cartridges;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import weirdkey.runtime.CartridgeRuntime;
import weirdkey.runtime.InMemoryKeyboard;
import weirdkey.runtime.InputType;
import weirdkey.runtime.KeyColor;
import weirdkey.runtime.KeyDefinition;
import weirdkey.runtime.KeyInputEvent;
import weirdkey.runtime.KeyboardTopology;

class FirstExperimentCartridgeTest {
    @Test
    void advancesOnlyWhenTheLitKeyIsPressed() {
        InMemoryKeyboard keyboard = new InMemoryKeyboard(topology("A", "B", "C"));
        try (CartridgeRuntime runtime = new CartridgeRuntime(
                keyboard,
                Optional.empty(),
                new FirstExperimentCartridge()
            )) {
            runtime.start();
            assertEquals(Optional.of(KeyColor.GREEN), keyboard.colorOf("A"));

            keyboard.emit(new KeyInputEvent("A", InputType.HOLD));
            keyboard.emit(new KeyInputEvent("A", InputType.RELEASE));
            keyboard.emit(new KeyInputEvent("B", InputType.PRESS));
            assertEquals(Optional.of(KeyColor.GREEN), keyboard.colorOf("A"));
            assertFalse(keyboard.colorOf("B").isPresent());

            keyboard.emit(new KeyInputEvent("A", InputType.PRESS));
            assertTrue(keyboard.colorOf("A").isEmpty());
            assertEquals(Optional.of(KeyColor.GREEN), keyboard.colorOf("B"));

            keyboard.emit(new KeyInputEvent("B", InputType.PRESS));
            keyboard.emit(new KeyInputEvent("C", InputType.PRESS));
            assertEquals(Optional.of(KeyColor.GREEN), keyboard.colorOf("A"));
        }
    }

    private static KeyboardTopology topology(String... keyIds) {
        return new KeyboardTopology(
            java.util.stream.IntStream.range(0, keyIds.length)
                .mapToObj(index -> new KeyDefinition(keyIds[index], 0, index))
                .toList()
        );
    }
}
