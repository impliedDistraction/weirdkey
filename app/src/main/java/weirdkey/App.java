package weirdkey;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import weirdkey.playtest.PlaytestSession;
import weirdkey.playtest.RecordingKeyboardDevice;
import weirdkey.runtime.DisplaySurface;
import weirdkey.runtime.InMemoryKeyboard;
import weirdkey.runtime.InputType;
import weirdkey.runtime.KeyInputEvent;
import weirdkey.runtime.KeyboardDevice;
import weirdkey.runtime.LogitechG915XTopology;
import weirdkey.runtime.WindowsLogitechKeyboard;
import weirdkey.world.WeirdkeyWorld;

public final class App {
    private App() {
    }

    public static void main(String[] args) {
        Arguments arguments = Arguments.parse(args);
        PlaytestSession session = arguments.playtest()
            ? PlaytestSession.start(arguments.hardware() ? "hardware" : "simulation")
            : null;
        if (session != null) {
            System.out.println("Playtest artifacts: " + session.directory().toAbsolutePath());
        }
        try (session) {
            try {
                if (arguments.hardware()) {
                    runHardware(session);
                } else {
                    runSimulation(arguments.inputs(), session);
                }
            } catch (RuntimeException | Error failure) {
                if (session != null) {
                    session.recordFailure(failure);
                }
                throw failure;
            }
        }
    }

    private static void runSimulation(List<String> inputs, PlaytestSession session) {
        InMemoryKeyboard keyboard = new InMemoryKeyboard(LogitechG915XTopology.create());
        if (session == null) {
            runSimulation(keyboard, keyboard, consoleDisplay(), inputs, null);
            return;
        }

        try (RecordingKeyboardDevice recordingKeyboard = new RecordingKeyboardDevice(keyboard, session)) {
            runSimulation(keyboard, recordingKeyboard, session.record(consoleDisplay()), inputs, session);
        }
    }

    private static void runSimulation(
        InMemoryKeyboard inputSource,
        KeyboardDevice keyboard,
        DisplaySurface display,
        List<String> inputs,
        PlaytestSession session
    ) {
        try (WeirdkeyWorld runtime = new WeirdkeyWorld(
                keyboard,
                Optional.of(display),
                cartridgeVaultPath(),
                result -> recordResult(session, result)
            )) {
            runtime.start();
            printLitKeys(inputSource.litKeys());

            if (inputs.isEmpty()) {
                System.out.println("Pass key ids as arguments to simulate presses.");
                System.out.println("Use PRESS:KEY, HOLD:KEY, or RELEASE:KEY; bare KEY defaults to PRESS.");
                return;
            }

            for (String argument : inputs) {
                KeyInputEvent event = parse(argument);
                inputSource.emit(event);
                System.out.println("Input: " + event.type() + " " + event.keyId());
                printLitKeys(inputSource.litKeys());
            }
        }
    }

    private static void runHardware(PlaytestSession session) {
        try (WindowsLogitechKeyboard keyboard = new WindowsLogitechKeyboard()) {
            if (session == null) {
                runHardware(keyboard, keyboard, consoleDisplay(), null);
                return;
            }

            try (RecordingKeyboardDevice recordingKeyboard = new RecordingKeyboardDevice(keyboard, session)) {
                runHardware(keyboard, recordingKeyboard, session.record(consoleDisplay()), session);
            }
        }
    }

    private static void runHardware(
        WindowsLogitechKeyboard inputSource,
        KeyboardDevice keyboard,
        DisplaySurface display,
        PlaytestSession session
    ) {
        try (WeirdkeyWorld runtime = new WeirdkeyWorld(
                keyboard,
                Optional.of(display),
                cartridgeVaultPath(),
                result -> recordResult(session, result)
            )) {
            runtime.start();
            System.out.println("Weirdkey is running on the G915 X. Press uncaptured Esc to quit.");
            inputSource.runUntilEscape();
        }
    }

    private static void recordResult(PlaytestSession session, weirdkey.runtime.CartridgeResult result) {
        if (session != null) {
            session.recordResult(result);
        }
    }

    private static DisplaySurface consoleDisplay() {
        return message -> System.out.println("[display] " + message);
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

    private static Path cartridgeVaultPath() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("cartridges");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return Path.of("cartridges");
    }

    private record Arguments(boolean hardware, boolean playtest, List<String> inputs) {
        private static Arguments parse(String[] rawArguments) {
            boolean hardware = false;
            boolean playtest = false;
            List<String> inputs = new ArrayList<>();
            for (String argument : rawArguments) {
                switch (argument) {
                    case "--hardware" -> hardware = true;
                    case "--playtest" -> playtest = true;
                    default -> {
                        if (argument.startsWith("--")) {
                            throw new IllegalArgumentException("Unknown option: " + argument);
                        }
                        inputs.add(argument);
                    }
                }
            }
            if (hardware && !inputs.isEmpty()) {
                throw new IllegalArgumentException("Hardware mode does not accept simulated key inputs");
            }
            return new Arguments(hardware, playtest, List.copyOf(inputs));
        }
    }
}
