package weirdkey.runtime;

import java.util.Optional;

import weirdkey.runtime.events.EventBus;
import weirdkey.runtime.events.EventTags;

public final class CartridgeRuntime implements AutoCloseable {
    private final KeyboardDevice keyboard;
    private final Cartridge cartridge;
    private final EventBus events;
    private final GameContext context;
    private boolean started;

    public CartridgeRuntime(KeyboardDevice keyboard, Optional<DisplaySurface> displaySurface, Cartridge cartridge) {
        this.keyboard = keyboard;
        this.cartridge = cartridge;
        this.events = new EventBus();
        this.context = new GameContext(keyboard, displaySurface, events);
    }

    public void start() {
        if (started) {
            throw new IllegalStateException("Runtime already started");
        }

        events.subscribe(KeyInputEvent.class, envelope -> cartridge.onInput(context, envelope.event()));
        keyboard.addInputListener(event -> events.emit(event, keyboard, EventTags.INPUT, EventTags.KEYBOARD));
        cartridge.start(context);
        started = true;
    }

    @Override
    public void close() {
        events.close();
    }
}
