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
    private final Object cycleLock = new Object();
    private InputSubscription keyboardInputSubscription;
    private boolean started;
    private boolean closed;

    public CartridgeRuntime(KeyboardDevice keyboard, Optional<DisplaySurface> displaySurface, Cartridge cartridge) {
        this.keyboard = keyboard;
        this.cartridge = cartridge;
        this.lifecycle = new RuntimeLifecycle();
        this.events = new EventBus(this::dispatchEvent);
        this.context = new GameContext(keyboard, displaySurface, events, lifecycle);
        this.cartridgeContext = new CartridgeContext(context, cartridge);
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
                runCycle(() -> cartridge.install(cartridgeContext));
            } catch (RuntimeException exception) {
                closed = true;
                cancelSubscriptions();
                events.close();
                throw exception;
            }
        }
    }

    private void dispatchEvent(Runnable eventDispatch) {
        synchronized (cycleLock) {
            Optional<LifecyclePhase> currentPhase = lifecycle.currentPhase();
            if (currentPhase.isEmpty()) {
                runCycle(eventDispatch);
                return;
            }
            if (currentPhase.get() != LifecyclePhase.UPDATE) {
                throw new IllegalStateException("Events can only be emitted during UPDATE or between cycles");
            }
            eventDispatch.run();
        }
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
}
