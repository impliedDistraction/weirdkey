package weirdkey.runtime;

import java.util.function.Consumer;

public interface KeyboardDevice {
    KeyboardTopology topology();

    void setColor(String keyId, KeyColor color);

    void clearColor(String keyId);

    void addInputListener(Consumer<KeyInputEvent> listener);
}
