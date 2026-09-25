package weirdkey.runtime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void runsOrderedPhasesAndCommitsOutputsAfterObservation() {
        InMemoryKeyboard keyboard = keyboard("A");
        List<String> order = new ArrayList<>();
        Cartridge cartridge = new Cartridge() {
            private int presses;

            @Override
            public void start(GameContext context) {
                context.onPhase(LifecyclePhase.PRE_UPDATE, () -> order.add("pre"));
                context.onPhase(LifecyclePhase.UPDATE, () -> order.add("update"));
                context.onPhase(LifecyclePhase.POST_UPDATE, () -> {
                    order.add("post:" + presses + ":" + keyboard.colorOf("A").isPresent());
                });
                context.onPhase(LifecyclePhase.COMMIT, () -> {
                    order.add("commit:" + keyboard.colorOf("A").isPresent());
                });
            }

            @Override
            public void onInput(GameContext context, KeyInputEvent event) {
                order.add("event");
                presses++;
                context.lightKey("A", KeyColor.GREEN);
            }
        };

        try (CartridgeRuntime runtime = new CartridgeRuntime(keyboard, Optional.empty(), cartridge)) {
            runtime.start();
            order.clear();

            keyboard.emit(new KeyInputEvent("A", InputType.PRESS));
        }

        assertEquals(
            List.of("pre", "event", "update", "post:1:false", "commit:false"),
            order
        );
        assertEquals(Optional.of(KeyColor.GREEN), keyboard.colorOf("A"));
    }

    @Test
    void reentrantEventsStayInsideTheCurrentUpdate() {
        InMemoryKeyboard keyboard = keyboard("A");
        List<String> order = new ArrayList<>();
        Cartridge cartridge = new Cartridge() {
            @Override
            public void start(GameContext context) {
                context.events().subscribe(String.class, envelope -> order.add(envelope.event()));
                context.onPhase(LifecyclePhase.PRE_UPDATE, () -> order.add("pre"));
                context.onPhase(LifecyclePhase.UPDATE, () -> order.add("update"));
                context.onPhase(LifecyclePhase.POST_UPDATE, () -> order.add("post"));
                context.onPhase(LifecyclePhase.COMMIT, () -> order.add("commit"));
            }

            @Override
            public void onInput(GameContext context, KeyInputEvent event) {
                order.add("input");
                context.events().emit("nested", this);
            }
        };

        try (CartridgeRuntime runtime = new CartridgeRuntime(keyboard, Optional.empty(), cartridge)) {
            runtime.start();
            order.clear();
            keyboard.emit(new KeyInputEvent("A", InputType.PRESS));
        }

        assertEquals(List.of("pre", "input", "nested", "update", "post", "commit"), order);
    }

    @Test
    void delayedEventsRunInTheirOwnLifecycleCycle() throws Exception {
        InMemoryKeyboard keyboard = keyboard("A");
        AtomicReference<GameContext> contextReference = new AtomicReference<>();
        AtomicInteger preUpdates = new AtomicInteger();
        AtomicInteger postUpdates = new AtomicInteger();
        Cartridge cartridge = new Cartridge() {
            @Override
            public void start(GameContext context) {
                contextReference.set(context);
                context.onPhase(LifecyclePhase.PRE_UPDATE, preUpdates::incrementAndGet);
                context.onPhase(LifecyclePhase.POST_UPDATE, postUpdates::incrementAndGet);
            }

            @Override
            public void onInput(GameContext context, KeyInputEvent event) {
            }
        };

        try (CartridgeRuntime runtime = new CartridgeRuntime(keyboard, Optional.empty(), cartridge)) {
            runtime.start();
            preUpdates.set(0);
            postUpdates.set(0);

            contextReference.get().events().emitAfter(
                Duration.ofMillis(10),
                "delayed",
                this,
                Set.of()
            ).get(1, TimeUnit.SECONDS);
        }

        assertEquals(1, preUpdates.get());
        assertEquals(1, postUpdates.get());
    }

    @Test
    void failedUpdatesDiscardBufferedOutputs() {
        InMemoryKeyboard keyboard = keyboard("A");
        Cartridge cartridge = new Cartridge() {
            @Override
            public void start(GameContext context) {
            }

            @Override
            public void onInput(GameContext context, KeyInputEvent event) {
                context.lightKey("A", KeyColor.GREEN);
                throw new IllegalStateException("failed update");
            }
        };

        try (CartridgeRuntime runtime = new CartridgeRuntime(keyboard, Optional.empty(), cartridge)) {
            runtime.start();
            assertThrows(
                IllegalStateException.class,
                () -> keyboard.emit(new KeyInputEvent("A", InputType.PRESS))
            );
        }

        assertTrue(keyboard.colorOf("A").isEmpty());
    }

    @Test
    void failedPostUpdateAbortsCommitAndDiscardsBufferedOutputs() {
        InMemoryKeyboard keyboard = keyboard("A");
        List<String> seenPhases = new ArrayList<>();
        AtomicInteger postUpdates = new AtomicInteger();
        Cartridge cartridge = new Cartridge() {
            @Override
            public void start(GameContext context) {
                context.onPhase(LifecyclePhase.POST_UPDATE, () -> {
                    seenPhases.add("post");
                    if (postUpdates.incrementAndGet() > 1) {
                        throw new IllegalStateException("failed observation");
                    }
                });
                context.onPhase(LifecyclePhase.COMMIT, () -> seenPhases.add("commit"));
            }

            @Override
            public void onInput(GameContext context, KeyInputEvent event) {
                context.lightKey("A", KeyColor.GREEN);
            }
        };

        try (CartridgeRuntime runtime = new CartridgeRuntime(keyboard, Optional.empty(), cartridge)) {
            runtime.start();
            seenPhases.clear();

            assertThrows(
                IllegalStateException.class,
                () -> keyboard.emit(new KeyInputEvent("A", InputType.PRESS))
            );
        }

        assertEquals(List.of("post"), seenPhases);
        assertTrue(keyboard.colorOf("A").isEmpty());
    }

    @Test
    void failedCommitCallbackDiscardsBufferedOutputs() {
        InMemoryKeyboard keyboard = keyboard("A");
        AtomicInteger commits = new AtomicInteger();
        Cartridge cartridge = new Cartridge() {
            @Override
            public void start(GameContext context) {
                context.onPhase(LifecyclePhase.COMMIT, () -> {
                    if (commits.incrementAndGet() > 1) {
                        throw new IllegalStateException("failed commit");
                    }
                });
            }

            @Override
            public void onInput(GameContext context, KeyInputEvent event) {
                context.lightKey("A", KeyColor.GREEN);
            }
        };

        try (CartridgeRuntime runtime = new CartridgeRuntime(keyboard, Optional.empty(), cartridge)) {
            runtime.start();

            assertThrows(
                IllegalStateException.class,
                () -> keyboard.emit(new KeyInputEvent("A", InputType.PRESS))
            );
        }

        assertTrue(keyboard.colorOf("A").isEmpty());
    }

    @Test
    void closeDetachesTheKeyboardListener() {
        InMemoryKeyboard keyboard = keyboard("A");
        AtomicInteger inputs = new AtomicInteger();
        Cartridge cartridge = new Cartridge() {
            @Override
            public void start(GameContext context) {
            }

            @Override
            public void onInput(GameContext context, KeyInputEvent event) {
                inputs.incrementAndGet();
            }
        };
        CartridgeRuntime runtime = new CartridgeRuntime(keyboard, Optional.empty(), cartridge);
        runtime.start();

        runtime.close();
        keyboard.emit(new KeyInputEvent("A", InputType.PRESS));

        assertEquals(0, inputs.get());
    }

    @Test
    void failedStartupCleansUpAndCannotBeRetried() {
        InMemoryKeyboard keyboard = keyboard("A");
        AtomicInteger starts = new AtomicInteger();
        Cartridge cartridge = new Cartridge() {
            @Override
            public void start(GameContext context) {
                starts.incrementAndGet();
                throw new IllegalStateException("failed startup");
            }

            @Override
            public void onInput(GameContext context, KeyInputEvent event) {
                throw new AssertionError("failed runtime must not receive input");
            }
        };
        CartridgeRuntime runtime = new CartridgeRuntime(keyboard, Optional.empty(), cartridge);

        assertThrows(IllegalStateException.class, runtime::start);
        keyboard.emit(new KeyInputEvent("A", InputType.PRESS));
        IllegalStateException retryFailure = assertThrows(IllegalStateException.class, runtime::start);

        assertEquals("Runtime is closed", retryFailure.getMessage());
        assertEquals(1, starts.get());
    }

    @Test
    void appliesDeviceOutputDuringCommitPhase() {
        InMemoryKeyboard delegate = keyboard("A");
        AtomicReference<GameContext> contextReference = new AtomicReference<>();
        AtomicReference<LifecyclePhase> outputPhase = new AtomicReference<>();
        KeyboardDevice keyboard = new KeyboardDevice() {
            @Override
            public KeyboardTopology topology() {
                return delegate.topology();
            }

            @Override
            public void setColor(String keyId, KeyColor color) {
                outputPhase.set(contextReference.get().currentPhase().orElseThrow());
                delegate.setColor(keyId, color);
            }

            @Override
            public void clearColor(String keyId) {
                delegate.clearColor(keyId);
            }

            @Override
            public InputSubscription addInputListener(java.util.function.Consumer<KeyInputEvent> listener) {
                return delegate.addInputListener(listener);
            }

            @Override
            public void captureInputKeys(Set<String> keyIds) {
                delegate.captureInputKeys(keyIds);
            }
        };
        Cartridge cartridge = new Cartridge() {
            @Override
            public void start(GameContext context) {
                contextReference.set(context);
                context.lightKey("A", KeyColor.GREEN);
            }

            @Override
            public void onInput(GameContext context, KeyInputEvent event) {
            }
        };

        try (CartridgeRuntime runtime = new CartridgeRuntime(keyboard, Optional.empty(), cartridge)) {
            runtime.start();
        }

        assertEquals(LifecyclePhase.COMMIT, outputPhase.get());
        assertEquals(Optional.of(KeyColor.GREEN), delegate.colorOf("A"));
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
}
