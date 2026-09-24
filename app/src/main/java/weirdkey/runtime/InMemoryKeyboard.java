package weirdkey.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public final class InMemoryKeyboard implements KeyboardDevice {
    private final KeyboardTopology topology;
    private final List<Consumer<KeyInputEvent>> listeners = new ArrayList<>();
    private final Map<String, KeyColor> litKeys = new LinkedHashMap<>();
    private Set<String> capturedKeys = Set.of();

    public InMemoryKeyboard(KeyboardTopology topology) {
        this.topology = topology;
    }

    @Override
    public KeyboardTopology topology() {
        return topology;
    }

    @Override
    public void setColor(String keyId, KeyColor color) {
        requireKnownKey(keyId);
        litKeys.put(keyId, color);
    }

    @Override
    public void clearColor(String keyId) {
        requireKnownKey(keyId);
        litKeys.remove(keyId);
    }

    @Override
    public void addInputListener(Consumer<KeyInputEvent> listener) {
        listeners.add(listener);
    }

    @Override
    public void captureInputKeys(Set<String> keyIds) {
        keyIds.forEach(this::requireKnownKey);
        capturedKeys = Set.copyOf(keyIds);
    }

    public void emit(KeyInputEvent event) {
        requireKnownKey(event.keyId());
        for (Consumer<KeyInputEvent> listener : List.copyOf(listeners)) {
            listener.accept(event);
        }
    }

    public Optional<KeyColor> colorOf(String keyId) {
        requireKnownKey(keyId);
        return Optional.ofNullable(litKeys.get(keyId));
    }

    public Map<String, KeyColor> litKeys() {
        return Map.copyOf(litKeys);
    }

    Set<String> capturedKeyIds() {
        return capturedKeys;
    }

    private void requireKnownKey(String keyId) {
        topology.key(keyId).orElseThrow(() -> new IllegalArgumentException("Unknown key: " + keyId));
    }
}
