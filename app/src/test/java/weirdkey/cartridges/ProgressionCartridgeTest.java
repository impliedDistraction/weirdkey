package weirdkey.cartridges;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import weirdkey.runtime.CartridgeRuntime;
import weirdkey.runtime.InMemoryKeyboard;
import weirdkey.runtime.InputType;
import weirdkey.runtime.KeyColor;
import weirdkey.runtime.KeyInputEvent;
import weirdkey.runtime.LogitechG915XTopology;

class ProgressionCartridgeTest {
    @Test
    void tutorialReversesBeforeOpeningAChoiceBetweenAdjacentKeys() {
        InMemoryKeyboard keyboard = keyboard();
        try (CartridgeRuntime runtime = new CartridgeRuntime(
                keyboard,
                Optional.empty(),
                new ProgressionCartridge()
            )) {
            runtime.start();
            assertLit(keyboard, "F1", KeyColor.GREEN);

            press(keyboard, "F1", "F2", "F3");
            assertLit(keyboard, "F2", KeyColor.GREEN);
            press(keyboard, "F2", "F1");

            assertLit(keyboard, "Q", KeyColor.GREEN);
            assertLit(keyboard, "W", new KeyColor(0, 32, 32));
            assertLit(keyboard, "A", new KeyColor(0, 32, 32));
        }
    }

    @Test
    void holdAndReleaseRevealAndHideTheOtherLayer() {
        InMemoryKeyboard keyboard = keyboard();
        try (CartridgeRuntime runtime = new CartridgeRuntime(
                keyboard,
                Optional.empty(),
                new ProgressionCartridge()
            )) {
            runtime.start();
            finishTutorial(keyboard);

            keyboard.emit(new KeyInputEvent("SPACE", InputType.PRESS));
            assertLit(keyboard, "NUMPAD_5", new KeyColor(255, 0, 128));
            assertLit(keyboard, "NUMPAD_2", new KeyColor(48, 0, 0));
            assertLit(keyboard, "NUMPAD_0", new KeyColor(0, 32, 96));

            keyboard.emit(new KeyInputEvent("SPACE", InputType.HOLD));
            keyboard.emit(new KeyInputEvent("SPACE", InputType.RELEASE));
            assertLit(keyboard, "Q", KeyColor.GREEN);
            assertTrue(keyboard.colorOf("NUMPAD_5").isEmpty());
        }
    }

    @Test
    void blockedMovementAndAnIntentionalDetourCanFreeTheActor() {
        InMemoryKeyboard keyboard = keyboard();
        List<String> statuses = new ArrayList<>();
        AtomicInteger exits = new AtomicInteger();
        ProgressionCartridge cartridge = new ProgressionCartridge();

        try (CartridgeRuntime runtime = new CartridgeRuntime(
                keyboard,
                Optional.of(statuses::add),
                cartridge,
                exits::incrementAndGet
            )) {
            runtime.start();
            finishTutorial(keyboard);

            press(keyboard, "W");
            revealActorAt(keyboard, "NUMPAD_5");
            press(keyboard, "S", "A");
            revealActorAt(keyboard, "NUMPAD_4");
            press(keyboard, "S");
            revealActorAt(keyboard, "NUMPAD_1");
            press(keyboard, "A", "S");
            assertLit(keyboard, "ESC", KeyColor.GREEN);
            press(keyboard, "ESC");
        }

        assertEquals(1, exits.get());
        assertEquals("The light is gone.", statuses.get(statuses.size() - 1));
        ProgressionCartridge.Result result = (ProgressionCartridge.Result) cartridge.result().orElseThrow();
        assertTrue(result.escaped());
        assertEquals(
            java.util.Set.of(
                ProgressionCartridge.Observation.APPROACHED_UNKNOWN,
                ProgressionCartridge.Observation.INVESTIGATED_ANOMALY,
                ProgressionCartridge.Observation.PROTECTED_OTHER_LIGHT
            ),
            result.observations()
        );
        assertEquals(List.of("NUMPAD_5", "NUMPAD_4", "NUMPAD_1", "NUMPAD_0"), result.actorRoute());
        assertEquals(3, result.blockedActorMoves());
        assertEquals(0, result.failedActions());
    }

    @Test
    void repeatedInvalidActionsAreRecordedWithoutAdvancing() {
        InMemoryKeyboard keyboard = keyboard();
        ProgressionCartridge cartridge = new ProgressionCartridge();
        try (CartridgeRuntime runtime = new CartridgeRuntime(keyboard, Optional.empty(), cartridge)) {
            runtime.start();

            press(keyboard, "A", "A", "A");
            assertLit(keyboard, "F1", KeyColor.GREEN);
        }

        ProgressionCartridge.Result result = (ProgressionCartridge.Result) cartridge.result().orElseThrow();
        assertTrue(result.observations().contains(ProgressionCartridge.Observation.REPEATED_FAILED_ACTION));
        assertEquals(3, result.failedActions());
    }

    private static InMemoryKeyboard keyboard() {
        return new InMemoryKeyboard(LogitechG915XTopology.create());
    }

    private static void finishTutorial(InMemoryKeyboard keyboard) {
        press(keyboard, "F1", "F2", "F3", "F2", "F1");
    }

    private static void press(InMemoryKeyboard keyboard, String... keyIds) {
        for (String keyId : keyIds) {
            keyboard.emit(new KeyInputEvent(keyId, InputType.PRESS));
        }
    }

    private static void revealActorAt(InMemoryKeyboard keyboard, String keyId) {
        keyboard.emit(new KeyInputEvent("SPACE", InputType.PRESS));
        assertLit(keyboard, keyId, new KeyColor(255, 0, 128));
        keyboard.emit(new KeyInputEvent("SPACE", InputType.RELEASE));
    }

    private static void assertLit(InMemoryKeyboard keyboard, String keyId, KeyColor color) {
        assertEquals(Optional.of(color), keyboard.colorOf(keyId));
    }
}
