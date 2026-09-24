package weirdkey.runtime.events;

public record EventTag(String value) {
    public EventTag {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }
}