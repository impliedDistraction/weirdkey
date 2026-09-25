package weirdkey.runtime;

import java.util.Optional;
import java.util.function.Consumer;

import weirdkey.runtime.events.EventBus;
import weirdkey.runtime.events.EventTags;

public final class InstallationRuntime implements AutoCloseable {
    private final KeyboardDevice keyboard;
    private final InstallationState installationState;
    private final RuntimeLifecycle lifecycle;
    private final EventBus events;
    private final GameContext gameContext;
    private final InstallationContext installationContext;
    private final Runnable afterTopLevelCycle;
    private final Object cycleLock = new Object();
    private InputSubscription keyboardInputSubscription;
    private boolean started;
    private boolean closed;

    public InstallationRuntime(
        KeyboardDevice keyboard,
        Optional<DisplaySurface> displaySurface,
        InstallationState installationState
    ) {
        this(keyboard, displaySurface, installationState, () -> {
        });
    }

    public InstallationRuntime(
        KeyboardDevice keyboard,
        Optional<DisplaySurface> displaySurface,
        InstallationState installationState,
        Runnable afterTopLevelCycle
    ) {
        this.keyboard = keyboard;
        this.installationState = installationState;
        this.lifecycle = new RuntimeLifecycle();
        this.events = new EventBus(this::dispatchEvent);
        this.gameContext = new GameContext(keyboard, displaySurface, events, lifecycle, () -> {
        });
        this.installationContext = new InstallationContext(gameContext, installationState);
        this.afterTopLevelCycle = afterTopLevelCycle;
    }

    public void start() {
        synchronized (cycleLock) {
            if (closed) {
                throw new IllegalStateException("Runtime is closed");
            }
            if (started) {
                throw new IllegalStateException("Runtime already started");
            }

            started = true;
            try {
                keyboardInputSubscription = keyboard.addInputListener(event -> {
                    synchronized (cycleLock) {
                        if (!closed) {
                            events.emit(event, keyboard, EventTags.INPUT, EventTags.KEYBOARD);
                        }
                    }
                });
                runCycle(() -> installationState.install(installationContext));
            } catch (RuntimeException exception) {
                closed = true;
                cancelSubscriptions();
                events.close();
                throw exception;
            }
        }
    }

    public void run(Consumer<InstallationContext> action) {
        synchronized (cycleLock) {
            requireRunning();
            if (lifecycle.currentPhase().isPresent()) {
                throw new IllegalStateException("Installation runtime actions must run between cycles");
            }
            runCycle(() -> action.accept(installationContext));
        }
    }

    private void dispatchEvent(Runnable eventDispatch) {
        boolean ranTopLevelCycle = false;
        synchronized (cycleLock) {
            Optional<LifecyclePhase> currentPhase = lifecycle.currentPhase();
            if (currentPhase.isEmpty()) {
                runCycle(eventDispatch);
                ranTopLevelCycle = true;
            } else {
                if (currentPhase.get() != LifecyclePhase.UPDATE) {
                    throw new IllegalStateException("Events can only be emitted during UPDATE or between cycles");
                }
                eventDispatch.run();
            }
        }
        if (ranTopLevelCycle) {
            afterTopLevelCycle.run();
        }
    }

    private void runCycle(Runnable updateAction) {
        try {
            lifecycle.run(LifecyclePhase.PRE_UPDATE, () -> {
            });
            lifecycle.run(LifecyclePhase.UPDATE, updateAction);
            lifecycle.run(LifecyclePhase.POST_UPDATE, () -> {
            });
            lifecycle.runAfterCallbacks(LifecyclePhase.COMMIT, gameContext::commitOutputs);
        } catch (RuntimeException exception) {
            gameContext.discardOutputs();
            throw exception;
        }
    }

    @Override
    public void close() {
        synchronized (cycleLock) {
            if (closed) {
                return;
            }
            closed = true;
            cancelSubscriptions();
            events.close();
        }
    }

    private void cancelSubscriptions() {
        if (keyboardInputSubscription != null) {
            keyboardInputSubscription.cancel();
            keyboardInputSubscription = null;
        }
    }

    private void requireRunning() {
        if (closed) {
            throw new IllegalStateException("Runtime is closed");
        }
        if (!started) {
            throw new IllegalStateException("Runtime is not started");
        }
    }
}
