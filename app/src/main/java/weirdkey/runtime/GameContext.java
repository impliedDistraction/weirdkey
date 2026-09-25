package weirdkey.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import weirdkey.runtime.events.EventBus;

final class GameContext {
    private final KeyboardDevice keyboard;
    private final Optional<DisplaySurface> displaySurface;
    private final EventBus events;
    private final RuntimeLifecycle lifecycle;
    private final List<Runnable> pendingOutputs = new ArrayList<>();

    GameContext(
        KeyboardDevice keyboard,
        Optional<DisplaySurface> displaySurface,
        EventBus events,
        RuntimeLifecycle lifecycle
    ) {
        this.keyboard = keyboard;
        this.displaySurface = displaySurface;
        this.events = events;
        this.lifecycle = lifecycle;
    }

    public KeyboardTopology topology() {
        return keyboard.topology();
    }

    public EventBus events() {
        return events;
    }

    public void onPhase(LifecyclePhase phase, Runnable callback) {
        lifecycle.on(phase, callback);
    }

    public Optional<LifecyclePhase> currentPhase() {
        return lifecycle.currentPhase();
    }

    public void lightKey(String keyId, KeyColor color) {
        pendingOutputs.add(() -> keyboard.setColor(keyId, color));
    }

    public void clearKey(String keyId) {
        pendingOutputs.add(() -> keyboard.clearColor(keyId));
    }

    public void showStatus(String message) {
        pendingOutputs.add(() -> displaySurface.ifPresent(surface -> surface.showStatus(message)));
    }

    public void captureInputKeys(Collection<String> keyIds) {
        Set<String> capturedKeys = Set.copyOf(keyIds);
        pendingOutputs.add(() -> keyboard.captureInputKeys(capturedKeys));
    }

    void commitOutputs() {
        List<Runnable> outputs = List.copyOf(pendingOutputs);
        pendingOutputs.clear();
        outputs.forEach(Runnable::run);
    }

    void discardOutputs() {
        pendingOutputs.clear();
    }
}
