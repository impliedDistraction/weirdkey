package weirdkey.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LogitechG915XTopologyTest {
    @Test
    void exposesKeyboardWorldInStableOrder() {
        KeyboardTopology topology = LogitechG915XTopology.create();

        assertTrue(topology.size() > 80);
        assertEquals("ESC", topology.orderedKeys().get(0).id());
        assertTrue(topology.key("NUMPAD_ENTER").isPresent());
        assertEquals(new KeyDefinition("ARROW_DOWN", 5, 9), topology.key("ARROW_DOWN").orElseThrow());
    }
}
