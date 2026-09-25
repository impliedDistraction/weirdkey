package weirdkey.runtime;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import weirdkey.runtime.events.EventBus;
import weirdkey.runtime.events.EventTags;

final class RuntimeEngine<C extends RuntimeContext> implements AutoCloseable {
    private final KeyboardDevice keyboard;
    private final RuntimeLifecycle lifecycle = new RuntimeLifecycle();
    private final EventBus events = new EventBus(this::dispatchEvent);
    private final GameContext gameContext;
    private final C context;
    private final Runnable onStop;
    private final Runnable afterTopLevelCycle;
    private final Object cycleLock = new Object();
    private InputSubscription keyboardInputSubscription;
    private boolean started;
    private boolean closed;
    private boolean stopRequested;
    private int cycleDepth;

    RuntimeEngine(
        KeyboardDevice keyboard,
        Optional<DisplaySurface> displaySurface,
        Function<GameContext, C> contextFactory,
        Runnable onStop,
        Runnable afterTopLevelCycle
    ) {
        this.keyboard = keyboard;
        this.gameContext = new GameContext(keyboard, displaySurface, events, lifecycle, this::requestStop);
        this.context = contextFactory.apply(gameContext);
        this.onStop = onStop;
        this.afterTopLevelCycle = afterTopLevelCycle;
    }

    void start(Consumer<C> installAction) {
        boolean notifyStop = false;
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
                runCycle(() -> installAction.accept(context));
                notifyStop = closeIfRequested();
            } catch (RuntimeException exception) {
                closeAfterFailure();
                throw exception;
            }
        }
        notifyStop(notifyStop);
    }

    void run(Consumer<C> action) {
        synchronized (cycleLock) {
            requireRunning();
            if (lifecycle.currentPhase().isPresent()) {
                throw new IllegalStateException("Runtime actions must run between cycles");
            }
            runCycle(() -> action.accept(context));
        }
    }

    private void dispatchEvent(Runnable eventDispatch) {
        boolean notifyStop = false;
        boolean ranTopLevelCycle = false;
        synchronized (cycleLock) {
            Optional<LifecyclePhase> currentPhase = lifecycle.currentPhase();
            if (currentPhase.isEmpty()) {
                runCycle(eventDispatch);
                notifyStop = closeIfRequested();
                ranTopLevelCycle = true;
            } else {
                if (currentPhase.get() != LifecyclePhase.UPDATE) {
                    throw new IllegalStateException("Events can only be emitted during UPDATE or between cycles");
                }
                eventDispatch.run();
                notifyStop = closeIfRequested();
            }
        }
        notifyStop(notifyStop);
        if (ranTopLevelCycle && !notifyStop) {
            afterTopLevelCycle.run();
        }
    }

    private void runCycle(Runnable updateAction) {
        cycleDepth++;
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
        } finally {
            cycleDepth--;
        }
    }

    @Override
    public void close() {
        boolean notifyStop;
        synchronized (cycleLock) {
            notifyStop = closeInternal();
        }
        notifyStop(notifyStop);
    }

    private void requestStop() {
        stopRequested = true;
    }

    private boolean closeIfRequested() {
        if (!stopRequested || cycleDepth > 0) {
            return false;
        }
        return closeInternal();
    }

    private boolean closeInternal() {
        if (closed) {
            return false;
        }
        closed = true;
        cancelSubscriptions();
        events.close();
        return true;
    }

    private void closeAfterFailure() {
        closed = true;
        cancelSubscriptions();
        events.close();
    }

    private void cancelSubscriptions() {
        if (keyboardInputSubscription != null) {
            keyboardInputSubscription.cancel();
            keyboardInputSubscription = null;
        }
    }

    private void notifyStop(boolean shouldNotify) {
        if (shouldNotify) {
            onStop.run();
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