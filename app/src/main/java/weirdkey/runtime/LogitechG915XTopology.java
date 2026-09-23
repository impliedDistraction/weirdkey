package weirdkey.runtime;

import java.util.ArrayList;
import java.util.List;

public final class LogitechG915XTopology {
    private static final String[][] ROWS = {
        {"ESC", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12", "PRINT_SCREEN", "SCROLL_LOCK", "PAUSE"},
        {"GRAVE", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "MINUS", "EQUALS", "BACKSPACE", "INSERT", "HOME", "PAGE_UP", "NUM_LOCK", "NUMPAD_DIVIDE", "NUMPAD_MULTIPLY", "NUMPAD_MINUS"},
        {"TAB", "Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "LEFT_BRACKET", "RIGHT_BRACKET", "BACKSLASH", "DELETE", "END", "PAGE_DOWN", "NUMPAD_7", "NUMPAD_8", "NUMPAD_9", "NUMPAD_PLUS"},
        {"CAPS_LOCK", "A", "S", "D", "F", "G", "H", "J", "K", "L", "SEMICOLON", "APOSTROPHE", "ENTER", "NUMPAD_4", "NUMPAD_5", "NUMPAD_6"},
        {"LEFT_SHIFT", "Z", "X", "C", "V", "B", "N", "M", "COMMA", "PERIOD", "SLASH", "RIGHT_SHIFT", "ARROW_UP", "NUMPAD_1", "NUMPAD_2", "NUMPAD_3"},
        {"LEFT_CTRL", "LEFT_SUPER", "LEFT_ALT", "SPACE", "RIGHT_ALT", "FN", "MENU", "RIGHT_CTRL", "ARROW_LEFT", "ARROW_DOWN", "ARROW_RIGHT", "NUMPAD_0", "NUMPAD_DECIMAL", "NUMPAD_ENTER"}
    };

    private LogitechG915XTopology() {
    }

    public static KeyboardTopology create() {
        List<KeyDefinition> keys = new ArrayList<>();
        for (int row = 0; row < ROWS.length; row++) {
            for (int column = 0; column < ROWS[row].length; column++) {
                keys.add(new KeyDefinition(ROWS[row][column], row, column));
            }
        }
        return new KeyboardTopology(keys);
    }
}
