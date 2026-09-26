package weirdkey.runtime;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.sun.jna.platform.win32.WinUser;

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
        Set<String> suppressedKeys = new HashSet<>();

        assertTrue(WindowsLogitechKeyboard.shouldSuppress(WinUser.WM_KEYDOWN, "F1", capturedKeys, suppressedKeys));
        assertTrue(WindowsLogitechKeyboard.shouldSuppress(WinUser.WM_KEYUP, "F1", capturedKeys, suppressedKeys));
        assertFalse(WindowsLogitechKeyboard.shouldSuppress(WinUser.WM_KEYDOWN, "ESC", capturedKeys, suppressedKeys));
        assertFalse(WindowsLogitechKeyboard.shouldSuppress(WinUser.WM_KEYDOWN, "A", capturedKeys, suppressedKeys));
        assertFalse(WindowsLogitechKeyboard.shouldSuppress(0x0200, "F1", capturedKeys, suppressedKeys));
    }

    @Test
    void escapeCanBeCapturedByACartridge() {
        Set<String> suppressedKeys = new HashSet<>();

        assertTrue(WindowsLogitechKeyboard.shouldSuppress(
            WinUser.WM_KEYDOWN,
            "ESC",
            Set.of("ESC"),
            suppressedKeys
        ));
        assertTrue(WindowsLogitechKeyboard.shouldSuppress(
            WinUser.WM_KEYUP,
            "ESC",
            Set.of(),
            suppressedKeys
        ));
    }

    @Test
    void preservesSuppressionDecisionForTheWholePhysicalPress() {
        Set<String> suppressedKeys = new HashSet<>();

        assertTrue(WindowsLogitechKeyboard.shouldSuppress(
            WinUser.WM_KEYDOWN,
            "F1",
            Set.of("F1"),
            suppressedKeys
        ));
        assertTrue(WindowsLogitechKeyboard.shouldSuppress(
            WinUser.WM_KEYDOWN,
            "F1",
            Set.of(),
            suppressedKeys
        ));
        assertTrue(WindowsLogitechKeyboard.shouldSuppress(
            WinUser.WM_KEYUP,
            "F1",
            Set.of(),
            suppressedKeys
        ));
        assertTrue(suppressedKeys.isEmpty());

        assertFalse(WindowsLogitechKeyboard.shouldSuppress(
            WinUser.WM_KEYDOWN,
            "F2",
            Set.of(),
            suppressedKeys
        ));
        assertFalse(WindowsLogitechKeyboard.shouldSuppress(
            WinUser.WM_KEYUP,
            "F2",
            Set.of("F2"),
            suppressedKeys
        ));
    }
}