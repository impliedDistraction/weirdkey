package weirdkey.runtime;

import java.util.Optional;

public interface Cartridge {
    void install(CartridgeContext context);

    default Optional<CartridgeResult> result() {
        return Optional.empty();
    }
}
