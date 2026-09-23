package weirdkey.runtime;

public record KeyColor(int red, int green, int blue) {
    public static final KeyColor GREEN = new KeyColor(0, 255, 0);

    public KeyColor {
        validate(red, "red");
        validate(green, "green");
        validate(blue, "blue");
    }

    private static void validate(int component, String name) {
        if (component < 0 || component > 255) {
            throw new IllegalArgumentException(name + " must be between 0 and 255");
        }
    }
}
