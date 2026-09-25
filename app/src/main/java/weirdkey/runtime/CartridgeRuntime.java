package weirdkey.runtime;

import java.util.Optional;

import weirdkey.runtime.events.EventBus;
import weirdkey.runtime.events.EventTags;

public final class CartridgeRuntime implements AutoCloseable {
    private final KeyboardDevice keyboard;
    private final Cartridge cartridge;
    private final RuntimeLifecycle lifecycle;
    private final EventBus events;
    private final GameContext context;
    private final CartridgeContext cartridgeContext;
    private final Runnable onStop;
    private final Object cycleLock = new Object();
    private InputSubscription keyboardInputSubscription;
    private boolean started;
    private boolean closed;
    private boolean stopRequested;

    public CartridgeRuntime(KeyboardDevice keyboard, Optional<DisplaySurface> displaySurface, Cartridge cartridge) {
        this(keyboard, displaySurface, cartridge, () -> {
        });
    }

    public CartridgeRuntime(
        KeyboardDevice keyboard,
        Optional<DisplaySurface> displaySurface,
        Cartridge cartridge,
        Runnable onStop
    ) {
        this.keyboard = keyboard;
        this.cartridge = cartridge;
        this.lifecycle = new RuntimeLifecycle();
        this.events = new EventBus(this::dispatchEvent);
        this.context = new GameContext(keyboard, displaySurface, events, lifecycle, this::requestStop);
        this.cartridgeContext = new CartridgeContext(context, cartridge);
        this.onStop = onStop;
    }

    public void start() {
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
                runCycle(() -> cartridge.install(cartridgeContext));
                notifyStop = closeIfRequested();
            } catch (RuntimeException exception) {
                closed = true;
                cancelSubscriptions();
                events.close();
                throw exception;
            }
        }
        notifyStop(notifyStop);
    }

    private void dispatchEvent(Runnable eventDispatch) {
        boolean notifyStop = false;
        synchronized (cycleLock) {
            Optional<LifecyclePhase> currentPhase = lifecycle.currentPhase();
            if (currentPhase.isEmpty()) {
                runCycle(eventDispatch);
                notifyStop = closeIfRequested();
            } else {
                if (currentPhase.get() != LifecyclePhase.UPDATE) {
                    throw new IllegalStateException("Events can only be emitted during UPDATE or between cycles");
                }
                eventDispatch.run();
            }
        }
        notifyStop(notifyStop);
    }

    private void runCycle(Runnable updateAction) {
        try {
            lifecycle.run(LifecyclePhase.PRE_UPDATE, () -> {
            });
            lifecycle.run(LifecyclePhase.UPDATE, updateAction);
            lifecycle.run(LifecyclePhase.POST_UPDATE, () -> {
            });
            lifecycle.runAfterCallbacks(LifecyclePhase.COMMIT, context::commitOutputs);
        } catch (RuntimeException exception) {
            context.discardOutputs();
            throw exception;
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

    private void cancelSubscriptions() {
        if (keyboardInputSubscription != null) {
            keyboardInputSubscription.cancel();
            keyboardInputSubscription = null;
        }
    }

    private void requestStop() {
        stopRequested = true;
    }

    private boolean closeIfRequested() {
        if (!stopRequested) {
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

    private void notifyStop(boolean shouldNotify) {
        if (shouldNotify) {
            onStop.run();
        }
    }
}
