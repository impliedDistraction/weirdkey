package weirdkey.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class KeyboardTopology {
    private final List<KeyDefinition> orderedKeys;
    private final Map<String, KeyDefinition> keysById;

    public KeyboardTopology(List<KeyDefinition> keys) {
        this.orderedKeys = List.copyOf(keys);
        Map<String, KeyDefinition> orderedKeyMap = new LinkedHashMap<>();
        for (KeyDefinition key : orderedKeys) {
            if (orderedKeyMap.putIfAbsent(key.id(), key) != null) {
                throw new IllegalArgumentException("Duplicate key id: " + key.id());
            }
        }
        this.keysById = Map.copyOf(orderedKeyMap);
    }

    public List<KeyDefinition> orderedKeys() {
        return orderedKeys;
    }

    public Optional<KeyDefinition> key(String keyId) {
        return Optional.ofNullable(keysById.get(keyId));
    }

    public int size() {
        return orderedKeys.size();
    }
}
