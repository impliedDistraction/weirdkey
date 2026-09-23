package weirdkey.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WindowsLogitechKeyboardTest {
    @Test
    void mapsTopologyIdsToLogitechScanCodes() {
        assertEquals(0x01, WindowsLogitechKeyboard.requireScanCode("ESC"));
        assertEquals(0x3B, WindowsLogitechKeyboard.requireScanCode("F1"));
        assertEquals(0x11C, WindowsLogitechKeyboard.requireScanCode("NUMPAD_ENTER"));
        assertEquals(0x153, WindowsLogitechKeyboard.requireScanCode("DELETE"));
    }

    @Test
    void mapsWindowsVirtualKeysToTopologyIds() {
        assertEquals("ESC", WindowsLogitechKeyboard.keyIds().get(0x1B));
        assertEquals("A", WindowsLogitechKeyboard.keyIds().get(0x41));
        assertEquals("F12", WindowsLogitechKeyboard.keyIds().get(0x7B));
        assertEquals("NUMPAD_0", WindowsLogitechKeyboard.keyIds().get(0x60));
    }

    @Test
    void convertsRgbBytesToSdkPercentages() {
        assertEquals(0, WindowsLogitechKeyboard.toPercentage(0));
        assertEquals(50, WindowsLogitechKeyboard.toPercentage(127));
        assertEquals(100, WindowsLogitechKeyboard.toPercentage(255));
    }
}