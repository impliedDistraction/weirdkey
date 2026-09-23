package weirdkey.runtime;

public record KeyDefinition(String id, int row, int column) {
    public KeyDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }
}
