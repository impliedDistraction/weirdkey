package weirdkey.runtime;

public final class CartridgeContext extends RuntimeContext {
    CartridgeContext(GameContext game, Object cartridge) {
        super(game, cartridge);
    }

    public void exit() {
        game().exit();
    }
}