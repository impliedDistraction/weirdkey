package weirdkey.runtime;

import java.util.Optional;

public final class CartridgeRuntime implements AutoCloseable {
    private final Cartridge cartridge;
    private final RuntimeEngine<CartridgeContext> engine;

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
        this.cartridge = cartridge;
        this.engine = new RuntimeEngine<>(
            keyboard,
            displaySurface,
            game -> new CartridgeContext(game, cartridge),
            onStop,
            () -> {
            }
        );
    }

    public void start() {
        engine.start(cartridge::install);
    }

    @Override
    public void close() {
        engine.close();
    }
}
