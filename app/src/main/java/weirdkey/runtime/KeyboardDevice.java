package weirdkey.runtime;

import java.util.Set;
import java.util.function.Consumer;

public interface KeyboardDevice {
    KeyboardTopology topology();

    void setColor(String keyId, KeyColor color);

    void clearColor(String keyId);

    InputSubscription addInputListener(Consumer<KeyInputEvent> listener);

    void captureInputKeys(Set<String> keyIds);
}
