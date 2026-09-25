package weirdkey.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.HHOOK;
import com.sun.jna.platform.win32.WinUser.KBDLLHOOKSTRUCT;
import com.sun.jna.platform.win32.WinUser.LowLevelKeyboardProc;
import com.sun.jna.platform.win32.WinUser.MSG;

public final class WindowsLogitechKeyboard implements KeyboardDevice, AutoCloseable {
    private static final int LOGI_DEVICETYPE_PERKEY_RGB = 1 << 2;
    private static final int LLKHF_EXTENDED = 0x01;
    private static final Map<String, Integer> SCAN_CODES = scanCodes();
    private static final Map<Integer, String> KEY_IDS = keyIds();

    private final KeyboardTopology topology;
    private final LogitechLedSdk ledSdk;
    private final List<Consumer<KeyInputEvent>> listeners = new CopyOnWriteArrayList<>();
    private final Set<String> pressedKeys = new HashSet<>();
    private final Set<String> suppressedKeys = new HashSet<>();
    private final LowLevelKeyboardProc keyboardProc = this::handleKeyboardEvent;
    private Set<String> capturedKeys = Set.of();
    private RuntimeException listenerFailure;
    private HHOOK hook;
    private boolean closed;

    public WindowsLogitechKeyboard() {
        if (!Platform.isWindows()) {
            throw new IllegalStateException("The Logitech hardware adapter requires Windows");
        }

        topology = supportedTopology();
        ledSdk = Native.load(findLedSdk().toString(), LogitechLedSdk.class);

        if (!ledSdk.LogiLedInit()) {
            throw new IllegalStateException("G HUB rejected Logitech LED SDK initialization");
        }

        try {
            requireSdkCall(ledSdk.LogiLedSetTargetDevice(LOGI_DEVICETYPE_PERKEY_RGB), "target the keyboard");
            requireSdkCall(ledSdk.LogiLedSaveCurrentLighting(), "save the current lighting");
            requireSdkCall(ledSdk.LogiLedSetLighting(0, 0, 0), "turn off the keyboard lighting");
        } catch (RuntimeException exception) {
            ledSdk.LogiLedShutdown();
            throw exception;
        }
    }

    @Override
    public KeyboardTopology topology() {
        return topology;
    }

    @Override
    public void setColor(String keyId, KeyColor color) {
        int scanCode = requireScanCode(keyId);
        requireSdkCall(
            ledSdk.LogiLedSetLightingForKeyWithScanCode(
                scanCode,
                toPercentage(color.red()),
                toPercentage(color.green()),
                toPercentage(color.blue())
            ),
            "set lighting for " + keyId
        );
    }

    @Override
    public void clearColor(String keyId) {
        int scanCode = requireScanCode(keyId);
        requireSdkCall(
            ledSdk.LogiLedSetLightingForKeyWithScanCode(scanCode, 0, 0, 0),
            "clear lighting for " + keyId
        );
    }

    @Override
    public InputSubscription addInputListener(Consumer<KeyInputEvent> listener) {
        listeners.add(listener);
        return new InputSubscription() {
            private volatile boolean active = true;

            @Override
            public boolean isActive() {
                return active;
            }

            @Override
            public void cancel() {
                if (active) {
                    active = false;
                    listeners.remove(listener);
                }
            }
        };
    }

    @Override
    public void captureInputKeys(Set<String> keyIds) {
        keyIds.forEach(keyId -> topology.key(keyId).orElseThrow(() -> new IllegalArgumentException("Unknown key: " + keyId)));
        capturedKeys = Set.copyOf(keyIds);
    }

    public void runUntilEscape() {
        if (hook != null) {
            throw new IllegalStateException("Keyboard input loop is already running");
        }

        listenerFailure = null;
        pressedKeys.clear();
        suppressedKeys.clear();
        hook = User32.INSTANCE.SetWindowsHookEx(WinUser.WH_KEYBOARD_LL, keyboardProc, null, 0);
        if (hook == null) {
            throw new IllegalStateException("Unable to install the Windows keyboard hook");
        }

        try {
            MSG message = new MSG();
            int result;
            while ((result = User32.INSTANCE.GetMessage(message, null, 0, 0)) > 0) {
                User32.INSTANCE.TranslateMessage(message);
                User32.INSTANCE.DispatchMessage(message);
            }
            RuntimeException failure = takeListenerFailure();
            if (failure != null) {
                throw failure;
            }
            if (result < 0) {
                throw new IllegalStateException("Windows keyboard message loop failed");
            }
        } finally {
            User32.INSTANCE.UnhookWindowsHookEx(hook);
            hook = null;
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        ledSdk.LogiLedRestoreLighting();
        ledSdk.LogiLedShutdown();
    }

    private LRESULT handleKeyboardEvent(int code, WPARAM message, KBDLLHOOKSTRUCT event) {
        if (code >= 0) {
            int messageId = message.intValue();
            boolean keyDown = messageId == WinUser.WM_KEYDOWN || messageId == WinUser.WM_SYSKEYDOWN;
            boolean keyUp = messageId == WinUser.WM_KEYUP || messageId == WinUser.WM_SYSKEYUP;
            if (keyDown || keyUp) {
                String keyId = keyId(event);
                boolean suppress = shouldSuppress(messageId, keyId, capturedKeys, suppressedKeys);
                if ("ESC".equals(keyId) && keyDown && !suppress) {
                    User32.INSTANCE.PostQuitMessage(0);
                } else if (keyId != null) {
                    try {
                        emit(keyId, keyDown);
                    } catch (RuntimeException exception) {
                        if (listenerFailure == null) {
                            listenerFailure = exception;
                        }
                        User32.INSTANCE.PostQuitMessage(1);
                        return new LRESULT(1);
                    }
                    if (suppress) {
                        return new LRESULT(1);
                    }
                }
            }
        }

        LPARAM eventPointer = new LPARAM(Pointer.nativeValue(event.getPointer()));
        return User32.INSTANCE.CallNextHookEx(hook, code, message, eventPointer);
    }

    private String keyId(KBDLLHOOKSTRUCT event) {
        if (event.vkCode == 0x0D && (event.flags & LLKHF_EXTENDED) != 0) {
            return "NUMPAD_ENTER";
        }
        return KEY_IDS.get(event.vkCode);
    }

    private void emit(String keyId, boolean keyDown) {
        InputType type;
        if (keyDown) {
            type = pressedKeys.add(keyId) ? InputType.PRESS : InputType.HOLD;
        } else {
            pressedKeys.remove(keyId);
            type = InputType.RELEASE;
        }

        KeyInputEvent event = new KeyInputEvent(keyId, type);
        List.copyOf(listeners).forEach(listener -> listener.accept(event));
    }

    static boolean shouldSuppress(
        int messageId,
        String keyId,
        Set<String> capturedKeys,
        Set<String> suppressedKeys
    ) {
        if (keyId == null) {
            return false;
        }
        if (messageId == WinUser.WM_KEYDOWN || messageId == WinUser.WM_SYSKEYDOWN) {
            if (suppressedKeys.contains(keyId)) {
                return true;
            }
            if (capturedKeys.contains(keyId)) {
                suppressedKeys.add(keyId);
                return true;
            }
        } else if (messageId == WinUser.WM_KEYUP || messageId == WinUser.WM_SYSKEYUP) {
            return suppressedKeys.remove(keyId);
        }
        return false;
    }

    private RuntimeException takeListenerFailure() {
        RuntimeException failure = listenerFailure;
        listenerFailure = null;
        return failure;
    }

    private static KeyboardTopology supportedTopology() {
        List<KeyDefinition> keys = LogitechG915XTopology.create().orderedKeys().stream()
            .filter(key -> SCAN_CODES.containsKey(key.id()))
            .toList();
        return new KeyboardTopology(keys);
    }

    static int requireScanCode(String keyId) {
        Integer scanCode = SCAN_CODES.get(keyId);
        if (scanCode == null) {
            throw new IllegalArgumentException("Unsupported Logitech key: " + keyId);
        }
        return scanCode;
    }

    static int toPercentage(int component) {
        return (component * 100 + 127) / 255;
    }

    private static void requireSdkCall(boolean succeeded, String operation) {
        if (!succeeded) {
            throw new IllegalStateException("Logitech LED SDK could not " + operation);
        }
    }

    private static Path findLedSdk() {
        String override = System.getenv("WEIRDKEY_LOGITECH_LED_DLL");
        if (override != null && !override.isBlank()) {
            Path path = Path.of(override);
            if (Files.isRegularFile(path)) {
                return path;
            }
            throw new IllegalStateException("WEIRDKEY_LOGITECH_LED_DLL does not point to a file: " + path);
        }

        for (String environmentVariable : List.of("ProgramW6432", "ProgramFiles")) {
            String programFiles = System.getenv(environmentVariable);
            if (programFiles == null) {
                continue;
            }
            Path path = Path.of(programFiles, "LGHUB", "sdks", "sdk_legacy_led_x64.dll");
            if (Files.isRegularFile(path)) {
                return path;
            }
        }

        throw new IllegalStateException(
            "Logitech G HUB LED SDK was not found; install G HUB or set WEIRDKEY_LOGITECH_LED_DLL"
        );
    }

    private static Map<String, Integer> scanCodes() {
        Map<String, Integer> scanCodes = new HashMap<>();
        String[] keys = {
            "ESC", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "MINUS", "EQUALS", "BACKSPACE",
            "TAB", "Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "LEFT_BRACKET", "RIGHT_BRACKET",
            "ENTER", "LEFT_CTRL", "A", "S", "D", "F", "G", "H", "J", "K", "L", "SEMICOLON", "APOSTROPHE",
            "GRAVE", "LEFT_SHIFT", "BACKSLASH", "Z", "X", "C", "V", "B", "N", "M", "COMMA", "PERIOD",
            "SLASH", "RIGHT_SHIFT", "NUMPAD_MULTIPLY", "LEFT_ALT", "SPACE", "CAPS_LOCK", "F1", "F2", "F3", "F4",
            "F5", "F6", "F7", "F8", "F9", "F10", "NUM_LOCK", "SCROLL_LOCK", "NUMPAD_7", "NUMPAD_8", "NUMPAD_9",
            "NUMPAD_MINUS", "NUMPAD_4", "NUMPAD_5", "NUMPAD_6", "NUMPAD_PLUS", "NUMPAD_1", "NUMPAD_2", "NUMPAD_3",
            "NUMPAD_0", "NUMPAD_DECIMAL", "F11", "F12"
        };
        int[] codes = {
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E,
            0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B,
            0x1C, 0x1D, 0x1E, 0x1F, 0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28,
            0x29, 0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x32, 0x33, 0x34,
            0x35, 0x36, 0x37, 0x38, 0x39, 0x3A, 0x3B, 0x3C, 0x3D, 0x3E,
            0x3F, 0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49,
            0x4A, 0x4B, 0x4C, 0x4D, 0x4E, 0x4F, 0x50, 0x51,
            0x52, 0x53, 0x57, 0x58
        };
        for (int index = 0; index < keys.length; index++) {
            scanCodes.put(keys[index], codes[index]);
        }

        scanCodes.put("NUMPAD_ENTER", 0x11C);
        scanCodes.put("RIGHT_CTRL", 0x11D);
        scanCodes.put("NUMPAD_DIVIDE", 0x135);
        scanCodes.put("PRINT_SCREEN", 0x137);
        scanCodes.put("RIGHT_ALT", 0x138);
        scanCodes.put("PAUSE", 0x145);
        scanCodes.put("HOME", 0x147);
        scanCodes.put("ARROW_UP", 0x148);
        scanCodes.put("PAGE_UP", 0x149);
        scanCodes.put("ARROW_LEFT", 0x14B);
        scanCodes.put("ARROW_RIGHT", 0x14D);
        scanCodes.put("END", 0x14F);
        scanCodes.put("ARROW_DOWN", 0x150);
        scanCodes.put("PAGE_DOWN", 0x151);
        scanCodes.put("INSERT", 0x152);
        scanCodes.put("DELETE", 0x153);
        scanCodes.put("LEFT_SUPER", 0x15B);
        scanCodes.put("MENU", 0x15D);
        return Map.copyOf(scanCodes);
    }

    static Map<Integer, String> keyIds() {
        Map<Integer, String> keyIds = new HashMap<>();
        for (int digit = 0; digit <= 9; digit++) {
            keyIds.put(0x30 + digit, Integer.toString(digit));
            keyIds.put(0x60 + digit, "NUMPAD_" + digit);
        }
        for (char letter = 'A'; letter <= 'Z'; letter++) {
            keyIds.put((int) letter, Character.toString(letter));
        }
        for (int function = 1; function <= 12; function++) {
            keyIds.put(0x6F + function, "F" + function);
        }

        keyIds.put(0x08, "BACKSPACE");
        keyIds.put(0x09, "TAB");
        keyIds.put(0x0D, "ENTER");
        keyIds.put(0x13, "PAUSE");
        keyIds.put(0x14, "CAPS_LOCK");
        keyIds.put(0x1B, "ESC");
        keyIds.put(0x20, "SPACE");
        keyIds.put(0x21, "PAGE_UP");
        keyIds.put(0x22, "PAGE_DOWN");
        keyIds.put(0x23, "END");
        keyIds.put(0x24, "HOME");
        keyIds.put(0x25, "ARROW_LEFT");
        keyIds.put(0x26, "ARROW_UP");
        keyIds.put(0x27, "ARROW_RIGHT");
        keyIds.put(0x28, "ARROW_DOWN");
        keyIds.put(0x2C, "PRINT_SCREEN");
        keyIds.put(0x2D, "INSERT");
        keyIds.put(0x2E, "DELETE");
        keyIds.put(0x5B, "LEFT_SUPER");
        keyIds.put(0x5D, "MENU");
        keyIds.put(0x6A, "NUMPAD_MULTIPLY");
        keyIds.put(0x6B, "NUMPAD_PLUS");
        keyIds.put(0x6D, "NUMPAD_MINUS");
        keyIds.put(0x6E, "NUMPAD_DECIMAL");
        keyIds.put(0x6F, "NUMPAD_DIVIDE");
        keyIds.put(0x90, "NUM_LOCK");
        keyIds.put(0x91, "SCROLL_LOCK");
        keyIds.put(0xA0, "LEFT_SHIFT");
        keyIds.put(0xA1, "RIGHT_SHIFT");
        keyIds.put(0xA2, "LEFT_CTRL");
        keyIds.put(0xA3, "RIGHT_CTRL");
        keyIds.put(0xA4, "LEFT_ALT");
        keyIds.put(0xA5, "RIGHT_ALT");
        keyIds.put(0xBA, "SEMICOLON");
        keyIds.put(0xBB, "EQUALS");
        keyIds.put(0xBC, "COMMA");
        keyIds.put(0xBD, "MINUS");
        keyIds.put(0xBE, "PERIOD");
        keyIds.put(0xBF, "SLASH");
        keyIds.put(0xC0, "GRAVE");
        keyIds.put(0xDB, "LEFT_BRACKET");
        keyIds.put(0xDC, "BACKSLASH");
        keyIds.put(0xDD, "RIGHT_BRACKET");
        keyIds.put(0xDE, "APOSTROPHE");
        return Map.copyOf(keyIds);
    }

    private interface LogitechLedSdk extends Library {
        boolean LogiLedInit();

        boolean LogiLedSetTargetDevice(int targetDevice);

        boolean LogiLedSaveCurrentLighting();

        boolean LogiLedSetLighting(int redPercentage, int greenPercentage, int bluePercentage);

        boolean LogiLedSetLightingForKeyWithScanCode(
            int keyCode,
            int redPercentage,
            int greenPercentage,
            int bluePercentage
        );

        boolean LogiLedRestoreLighting();

        void LogiLedShutdown();
    }
}