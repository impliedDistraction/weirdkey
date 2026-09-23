package weirdkey;

import java.util.Map;
import java.util.Optional;
import weirdkey.cartridges.FirstExperimentCartridge;
import weirdkey.runtime.CartridgeRuntime;
import weirdkey.runtime.DisplaySurface;
import weirdkey.runtime.InMemoryKeyboard;
import weirdkey.runtime.InputType;
import weirdkey.runtime.KeyInputEvent;
import weirdkey.runtime.LogitechG915XTopology;

public final class App {
    private App() {
    }

    public static void main(String[] args) {
        InMemoryKeyboard keyboard = new InMemoryKeyboard(LogitechG915XTopology.create());
        DisplaySurface display = message -> System.out.println("[display] " + message);
        CartridgeRuntime runtime = new CartridgeRuntime(
            keyboard,
            Optional.of(display),
            new FirstExperimentCartridge()
        );

        runtime.start();
        printLitKeys(keyboard.litKeys());

        if (args.length == 0) {
            System.out.println("Pass key ids as arguments to simulate presses.");
            System.out.println("Use PRESS:KEY, HOLD:KEY, or RELEASE:KEY; bare KEY defaults to PRESS.");
            return;
        }

        for (String argument : args) {
            KeyInputEvent event = parse(argument);
            keyboard.emit(event);
            System.out.println("Input: " + event.type() + " " + event.keyId());
            printLitKeys(keyboard.litKeys());
        }
    }

    private static KeyInputEvent parse(String argument) {
        String[] parts = argument.split(":", 2);
        if (parts.length == 1) {
            return new KeyInputEvent(parts[0], InputType.PRESS);
        }

        return new KeyInputEvent(parts[1], InputType.valueOf(parts[0].toUpperCase()));
    }

    private static void printLitKeys(Map<String, ?> litKeys) {
        System.out.println("Lit keys: " + litKeys.keySet());
    }
}
