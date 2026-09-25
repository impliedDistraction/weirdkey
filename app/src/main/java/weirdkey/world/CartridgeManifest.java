package weirdkey.world;

import java.util.Optional;

public record CartridgeManifest(
    String id,
    String name,
    CartridgeAvailability availability,
    Optional<String> entry,
    String description
) {
    public CartridgeManifest {
        entry = entry.map(String::trim).filter(value -> !value.isEmpty());
        description = description.trim();
    }
}
