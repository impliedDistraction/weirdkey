package weirdkey.runtime;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import weirdkey.runtime.events.EventBus;

public final class GameContext {
    private final KeyboardDevice keyboard;
    private final Optional<DisplaySurface> displaySurface;
    private final EventBus events;

    public GameContext(KeyboardDevice keyboard, Optional<DisplaySurface> displaySurface, EventBus events) {
        this.keyboard = keyboard;
        this.displaySurface = displaySurface;
        this.events = events;
    }

    public KeyboardTopology topology() {
        return keyboard.topology();
    }

    public EventBus events() {
        return events;
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
