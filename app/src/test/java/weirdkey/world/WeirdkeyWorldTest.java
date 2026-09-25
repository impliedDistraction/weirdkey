package weirdkey.world;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import weirdkey.runtime.InMemoryKeyboard;
import weirdkey.runtime.InstallationContext;
import weirdkey.runtime.InstallationState;
import weirdkey.runtime.KeyColor;
import weirdkey.runtime.KeyDefinition;
import weirdkey.runtime.KeyInputEvent;
import weirdkey.runtime.KeyboardTopology;
import weirdkey.runtime.InputType;
import weirdkey.runtime.LogitechG915XTopology;
import weirdkey.cartridges.ProgressionCartridge;

class WeirdkeyWorldTest {
    @Test
    void discoversRepositoryCartridgeFixtures() {
        CartridgeVault vault = new CartridgeVault(repositoryRoot().resolve("cartridges"));

        List<CartridgeManifest> manifests = vault.discover();

        assertEquals(
            List.of("Progression", "Paint With Kevin"),
            manifests.stream().map(CartridgeManifest::name).toList()
        );
        assertEquals(
            List.of(CartridgeAvailability.AVAILABLE, CartridgeAvailability.UNAVAILABLE),
            manifests.stream().map(CartridgeManifest::availability).toList()
        );
        assertEquals(Optional.of("weirdkey.cartridges.ProgressionCartridge"), manifests.get(0).entry());
        assertEquals("KEVIN IS NOT AVAILABLE.", manifests.get(1).description());
    }

    @Test
    void selectingAnUnavailableCartridgeShowsItsManifestTextWithoutLaunching() {
        InMemoryKeyboard keyboard = keyboard("F1", "F2", "PAUSE");
        List<String> statuses = new ArrayList<>();

        try (WeirdkeyWorld world = new WeirdkeyWorld(
                keyboard,
                Optional.of(statuses::add),
                repositoryRoot().resolve("cartridges")
            )) {
            world.start();

            keyboard.emit(new KeyInputEvent("F2", InputType.PRESS));
        }

        assertEquals(
            "F1 Progression [AVAILABLE]" + System.lineSeparator() + "F2 Paint With Kevin [UNAVAILABLE]",
            statuses.get(0)
        );
        assertEquals("> PAINT WITH KEVIN" + System.lineSeparator() + System.lineSeparator() + "KEVIN IS NOT AVAILABLE.", last(statuses));
        assertEquals(Optional.of(KeyColor.GREEN), keyboard.colorOf("F1"));
        assertEquals(Optional.of(new KeyColor(96, 96, 0)), keyboard.colorOf("F2"));
    }

    @Test
    void launchingProgressionCanReturnToTheCartlessWorld() {
        InMemoryKeyboard keyboard = new InMemoryKeyboard(LogitechG915XTopology.create());
        List<String> statuses = new ArrayList<>();

        try (WeirdkeyWorld world = new WeirdkeyWorld(
                keyboard,
                Optional.of(statuses::add),
                repositoryRoot().resolve("cartridges")
            )) {
            world.start();

            keyboard.emit(new KeyInputEvent("F1", InputType.PRESS));
            assertEquals("Lit key F1", last(statuses));
            assertEquals(Optional.of(KeyColor.GREEN), keyboard.colorOf("F1"));
            assertTrue(keyboard.colorOf("F2").isEmpty());

            keyboard.emit(new KeyInputEvent("PAUSE", InputType.PRESS));
        }

        assertEquals(
            "F1 Progression [AVAILABLE]" + System.lineSeparator() + "F2 Paint With Kevin [UNAVAILABLE]",
            last(statuses)
        );
        assertEquals(Optional.of(KeyColor.GREEN), keyboard.colorOf("F1"));
        assertEquals(Optional.of(new KeyColor(96, 96, 0)), keyboard.colorOf("F2"));
    }

    @Test
    void installationStateSubscriptionsSurviveLaunchAndReturn() {
        InMemoryKeyboard keyboard = new InMemoryKeyboard(LogitechG915XTopology.create());
        PersistentProbe probe = new PersistentProbe();

        try (WeirdkeyWorld world = new WeirdkeyWorld(
                keyboard,
                Optional.empty(),
                new CartridgeVault(repositoryRoot().resolve("cartridges")),
                List.of(probe)
            )) {
            world.start();

            keyboard.emit(new KeyInputEvent("F2", InputType.PRESS));
            keyboard.emit(new KeyInputEvent("F1", InputType.PRESS));
            keyboard.emit(new KeyInputEvent("PAUSE", InputType.PRESS));
            keyboard.emit(new KeyInputEvent("F2", InputType.PRESS));
        }

        assertEquals(1, probe.installs.get());
        assertEquals(4, probe.inputs.get());
    }

    @Test
    void completedProgressionReturnsItsResultToTheWorld() {
        InMemoryKeyboard keyboard = new InMemoryKeyboard(LogitechG915XTopology.create());

        try (WeirdkeyWorld world = new WeirdkeyWorld(
                keyboard,
                Optional.empty(),
                repositoryRoot().resolve("cartridges")
            )) {
            world.start();

            press(keyboard, "F1", "F1", "F2", "F3", "F2", "F1", "W", "S", "A", "S", "A", "S", "ESC");

            ProgressionCartridge.Result result =
                (ProgressionCartridge.Result) world.lastCartridgeResult().orElseThrow();
            assertTrue(result.escaped());
            assertEquals(Optional.of(KeyColor.GREEN), keyboard.colorOf("F1"));
        }
    }

    private static String last(List<String> values) {
        return values.get(values.size() - 1);
    }

    private static InMemoryKeyboard keyboard(String... keyIds) {
        return new InMemoryKeyboard(
            new KeyboardTopology(
                java.util.stream.IntStream.range(0, keyIds.length)
                    .mapToObj(index -> new KeyDefinition(keyIds[index], 0, index))
                    .toList()
            )
        );
    }

    private static void press(InMemoryKeyboard keyboard, String... keyIds) {
        for (String keyId : keyIds) {
            keyboard.emit(new KeyInputEvent(keyId, InputType.PRESS));
        }
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("cartridges"))
                && Files.isRegularFile(current.resolve("cartridges/progression/cartridge.md"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository root");
    }

    private static final class PersistentProbe implements InstallationState {
        private final AtomicInteger installs = new AtomicInteger();
        private final AtomicInteger inputs = new AtomicInteger();

        @Override
        public void install(InstallationContext context) {
            installs.incrementAndGet();
            context.on(KeyInputEvent.class, event -> inputs.incrementAndGet());
        }
    }
}
