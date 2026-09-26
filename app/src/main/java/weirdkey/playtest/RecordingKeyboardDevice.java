package weirdkey.playtest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import weirdkey.runtime.InputSubscription;
import weirdkey.runtime.KeyColor;
import weirdkey.runtime.KeyInputEvent;
import weirdkey.runtime.KeyboardDevice;
import weirdkey.runtime.KeyboardTopology;

public final class RecordingKeyboardDevice implements KeyboardDevice, AutoCloseable {
    private final KeyboardDevice delegate;
    private final PlaytestSession session;
    private final InputSubscription recordingSubscription;
    private final Map<String, KeyColor> litKeys = new LinkedHashMap<>();
    private volatile Set<String> capturedKeys = Set.of();

    public RecordingKeyboardDevice(KeyboardDevice delegate, PlaytestSession session) {
        this.delegate = delegate;
        this.session = session;
        this.recordingSubscription = delegate.addInputListener(this::recordInput);
        session.record("keyboard", Map.of(
            "implementation", delegate.getClass().getName(),
            "keyCount", delegate.topology().size()
        ));
    }

    @Override
    public KeyboardTopology topology() {
        return delegate.topology();
    }

    @Override
    public synchronized void setColor(String keyId, KeyColor color) {
        delegate.setColor(keyId, color);
        if (!color.equals(litKeys.put(keyId, color))) {
            session.record("led_set", Map.of(
                "keyId", keyId,
                "red", color.red(),
                "green", color.green(),
                "blue", color.blue()
            ));
        }
    }

    @Override
    public synchronized void clearColor(String keyId) {
        delegate.clearColor(keyId);
        if (litKeys.remove(keyId) != null) {
            session.record("led_clear", Map.of("keyId", keyId));
        }
    }

    @Override
    public InputSubscription addInputListener(Consumer<KeyInputEvent> listener) {
        return delegate.addInputListener(listener);
    }

    @Override
    public void captureInputKeys(Set<String> keyIds) {
        delegate.captureInputKeys(keyIds);
        Set<String> captured = Set.copyOf(keyIds);
        if (!captured.equals(capturedKeys)) {
            capturedKeys = captured;
            session.record("input_capture", Map.of("keyIds", captured.stream().sorted().toList()));
        }
    }

    @Override
    public void close() {
        recordingSubscription.cancel();
    }

    private void recordInput(KeyInputEvent event) {
        if (capturedKeys.contains(event.keyId())) {
            session.record("input", Map.of(
                "keyId", event.keyId(),
                "inputType", event.type().name()
            ));
        }
    }
}