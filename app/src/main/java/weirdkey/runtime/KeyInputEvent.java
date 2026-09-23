package weirdkey.runtime;

public record KeyInputEvent(String keyId, InputType type) {
    public KeyInputEvent {
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("keyId must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
    }
}
