package weirdkey.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.jna.platform.win32.WinUser;
import java.util.Set;
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

    @Test
    void suppressesOnlyCapturedGameplayKeys() {
        Set<String> capturedKeys = Set.of("F1", "F2");

        assertTrue(WindowsLogitechKeyboard.shouldSuppress(WinUser.WM_KEYDOWN, "F1", capturedKeys));
        assertTrue(WindowsLogitechKeyboard.shouldSuppress(WinUser.WM_KEYUP, "F2", capturedKeys));
        assertFalse(WindowsLogitechKeyboard.shouldSuppress(WinUser.WM_KEYDOWN, "ESC", capturedKeys));
        assertFalse(WindowsLogitechKeyboard.shouldSuppress(WinUser.WM_KEYDOWN, "A", capturedKeys));
        assertFalse(WindowsLogitechKeyboard.shouldSuppress(0x0200, "F1", capturedKeys));
    }
}