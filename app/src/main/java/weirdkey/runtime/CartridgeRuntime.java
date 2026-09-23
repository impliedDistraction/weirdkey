package weirdkey.runtime;

import java.util.Optional;

public final class CartridgeRuntime {
    private final KeyboardDevice keyboard;
    private final Cartridge cartridge;
    private final GameContext context;
    private boolean started;

    public CartridgeRuntime(KeyboardDevice keyboard, Optional<DisplaySurface> displaySurface, Cartridge cartridge) {
        this.keyboard = keyboard;
        this.cartridge = cartridge;
        this.context = new GameContext(keyboard, displaySurface);
    }

    public void start() {
        if (started) {
            throw new IllegalStateException("Runtime already started");
        }

        keyboard.addInputListener(event -> cartridge.onInput(context, event));
        cartridge.start(context);
        started = true;
    }
}
