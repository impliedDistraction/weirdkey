package weirdkey.runtime;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public final class GameContext {
    private final KeyboardDevice keyboard;
    private final Optional<DisplaySurface> displaySurface;

    public GameContext(KeyboardDevice keyboard, Optional<DisplaySurface> displaySurface) {
        this.keyboard = keyboard;
        this.displaySurface = displaySurface;
    }

    public KeyboardTopology topology() {
        return keyboard.topology();
    }

    public void lightKey(String keyId, KeyColor color) {
        keyboard.setColor(keyId, color);
    }

    public void clearKey(String keyId) {
        keyboard.clearColor(keyId);
    }

    public void showStatus(String message) {
        displaySurface.ifPresent(surface -> surface.showStatus(message));
    }

    public void captureInputKeys(Collection<String> keyIds) {
        keyboard.captureInputKeys(Set.copyOf(keyIds));
    }
}
