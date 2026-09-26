package weirdkey.cartridges;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import weirdkey.runtime.Cartridge;
import weirdkey.runtime.CartridgeContext;
import weirdkey.runtime.CartridgeResult;
import weirdkey.runtime.InputType;
import weirdkey.runtime.KeyColor;
import weirdkey.runtime.KeyInputEvent;

public final class ProgressionCartridge implements Cartridge {
    private static final KeyColor REACHABLE_COLOR = new KeyColor(0, 32, 32);
    private static final KeyColor ACTOR_COLOR = new KeyColor(255, 0, 128);
    private static final KeyColor BLOCKER_COLOR = new KeyColor(48, 0, 0);
    private static final KeyColor EXIT_COLOR = new KeyColor(0, 32, 96);
    private static final String LAYER_KEY = "SPACE";
    private static final String RETURN_KEY = "PAUSE";
    private static final List<String> TUTORIAL = List.of("F1", "F2", "F3", "F2", "F1");
    private static final Set<String> BLOCKERS = Set.of("NUMPAD_2", "NUMPAD_6", "NUMPAD_8");
    private static final Map<String, List<String>> PLAYER_PATHS = playerPaths();
    private static final Map<String, Position> NUMPAD_POSITIONS = numpadPositions();
    private static final Map<Position, String> NUMPAD_KEYS = numpadKeys();
    private static final Map<String, Position> MOVEMENT_DELTAS = Map.of(
        "W", new Position(-1, 0),
        "A", new Position(0, -1),
        "S", new Position(1, 0),
        "D", new Position(0, 1)
    );
    private static final Set<String> REQUIRED_KEYS = requiredKeys();

    private final EnumSet<Observation> observations = EnumSet.noneOf(Observation.class);
    private final List<String> actorRoute = new ArrayList<>(List.of("NUMPAD_5"));
    private int tutorialIndex;
    private String playerKey;
    private String actorKey = "NUMPAD_5";
    private boolean otherLayerVisible;
    private boolean escaped;
    private int failedActions;
    private int blockedActorMoves;

    @Override
    public void install(CartridgeContext context) {
        List<String> missingKeys = REQUIRED_KEYS.stream()
            .filter(keyId -> context.topology().key(keyId).isEmpty())
            .sorted()
            .toList();
        if (!missingKeys.isEmpty()) {
            throw new IllegalStateException("Progression requires keys: " + String.join(", ", missingKeys));
        }

        context.captureInputKeys(REQUIRED_KEYS);
        context.on(KeyInputEvent.class, event -> onInput(context, event));
        render(context);
    }

    private void onInput(CartridgeContext context, KeyInputEvent event) {
        if (event.type() == InputType.PRESS && RETURN_KEY.equals(event.keyId())) {
            context.showStatus("Returning to Weirdkey.");
            context.exit();
            return;
        }

        if (LAYER_KEY.equals(event.keyId())) {
            if (event.type() == InputType.PRESS || event.type() == InputType.HOLD) {
                otherLayerVisible = true;
                observations.add(Observation.INVESTIGATED_ANOMALY);
                render(context);
            } else if (event.type() == InputType.RELEASE) {
                otherLayerVisible = false;
                render(context);
            }
            return;
        }

        if (event.type() != InputType.PRESS) {
            return;
        }
        if (tutorialIndex < TUTORIAL.size()) {
            advanceTutorial(context, event.keyId());
        } else if (actorKey.equals("NUMPAD_0")) {
            if ("ESC".equals(event.keyId())) {
                escaped = true;
                context.showStatus("The light is gone.");
                context.exit();
            } else {
                failedAction();
            }
        } else {
            movePlayer(context, event.keyId());
        }
    }

    private void advanceTutorial(CartridgeContext context, String keyId) {
        if (!TUTORIAL.get(tutorialIndex).equals(keyId)) {
            failedAction();
            return;
        }

        tutorialIndex++;
        if (tutorialIndex == TUTORIAL.size()) {
            playerKey = "Q";
        }
        render(context);
    }

    private void movePlayer(CartridgeContext context, String destination) {
        if (!PLAYER_PATHS.get(playerKey).contains(destination)) {
            failedAction();
            return;
        }

        if ("Q".equals(playerKey)) {
            observations.add(
                "W".equals(destination) ? Observation.APPROACHED_UNKNOWN : Observation.RETREATED_FROM_MOTION
            );
        }
        playerKey = destination;
        moveActor(destination);
        render(context);
    }

    private void moveActor(String movementKey) {
        Position delta = MOVEMENT_DELTAS.get(movementKey);
        Position current = NUMPAD_POSITIONS.get(actorKey);
        String attemptedDestination = delta == null
            ? null
            : NUMPAD_KEYS.get(new Position(current.row() + delta.row(), current.column() + delta.column()));
        if (attemptedDestination == null || BLOCKERS.contains(attemptedDestination)) {
            if (delta != null) {
                blockedActorMoves++;
            }
            return;
        }
        actorKey = attemptedDestination;
        actorRoute.add(actorKey);
        observations.add(Observation.PROTECTED_OTHER_LIGHT);
    }

    private void failedAction() {
        failedActions++;
        if (failedActions >= 3) {
            observations.add(Observation.REPEATED_FAILED_ACTION);
        }
    }

    private void render(CartridgeContext context) {
        REQUIRED_KEYS.forEach(context::clearKey);
        if (tutorialIndex < TUTORIAL.size()) {
            String activeKey = TUTORIAL.get(tutorialIndex);
            context.lightKey(activeKey, KeyColor.GREEN);
            context.showStatus("Lit key " + activeKey);
            return;
        }
        if (actorKey.equals("NUMPAD_0")) {
            context.lightKey("ESC", KeyColor.GREEN);
            context.showStatus("Lit key ESC");
            return;
        }
        if (otherLayerVisible) {
            BLOCKERS.forEach(keyId -> context.lightKey(keyId, BLOCKER_COLOR));
            context.lightKey("NUMPAD_0", EXIT_COLOR);
            context.lightKey(actorKey, ACTOR_COLOR);
            context.showStatus("Another layer.");
            return;
        }

        context.lightKey(playerKey, KeyColor.GREEN);
        PLAYER_PATHS.get(playerKey).forEach(keyId -> context.lightKey(keyId, REACHABLE_COLOR));
        context.lightKey(LAYER_KEY, REACHABLE_COLOR);
        context.showStatus("Something can move.");
    }

    @Override
    public Optional<CartridgeResult> result() {
        return Optional.of(new Result(observations, escaped, actorRoute, blockedActorMoves, failedActions));
    }

    private static Map<String, List<String>> playerPaths() {
        Map<String, List<String>> paths = new LinkedHashMap<>();
        paths.put("Q", List.of("W", "A"));
        paths.put("W", List.of("Q", "S"));
        paths.put("A", List.of("Q", "S"));
        paths.put("S", List.of("W", "A", "D"));
        paths.put("D", List.of("S"));
        return Map.copyOf(paths);
    }

    private static Map<String, Position> numpadPositions() {
        Map<String, Position> positions = new LinkedHashMap<>();
        positions.put("NUMPAD_7", new Position(0, 0));
        positions.put("NUMPAD_8", new Position(0, 1));
        positions.put("NUMPAD_9", new Position(0, 2));
        positions.put("NUMPAD_4", new Position(1, 0));
        positions.put("NUMPAD_5", new Position(1, 1));
        positions.put("NUMPAD_6", new Position(1, 2));
        positions.put("NUMPAD_1", new Position(2, 0));
        positions.put("NUMPAD_2", new Position(2, 1));
        positions.put("NUMPAD_3", new Position(2, 2));
        positions.put("NUMPAD_0", new Position(3, 0));
        return Map.copyOf(positions);
    }

    private static Map<Position, String> numpadKeys() {
        Map<Position, String> keys = new LinkedHashMap<>();
        NUMPAD_POSITIONS.forEach((keyId, position) -> keys.put(position, keyId));
        return Map.copyOf(keys);
    }

    private static Set<String> requiredKeys() {
        List<String> keys = new ArrayList<>(TUTORIAL);
        keys.addAll(PLAYER_PATHS.keySet());
        PLAYER_PATHS.values().forEach(keys::addAll);
        keys.addAll(NUMPAD_POSITIONS.keySet());
        keys.addAll(List.of("ESC", LAYER_KEY, RETURN_KEY));
        return Set.copyOf(keys);
    }

    private record Position(int row, int column) {
    }

    public enum Observation {
        APPROACHED_UNKNOWN,
        RETREATED_FROM_MOTION,
        REPEATED_FAILED_ACTION,
        INVESTIGATED_ANOMALY,
        PROTECTED_OTHER_LIGHT
    }

    public record Result(
        Set<Observation> observations,
        boolean escaped,
        List<String> actorRoute,
        int blockedActorMoves,
        int failedActions
    ) implements CartridgeResult {
        public Result {
            observations = Set.copyOf(observations);
            actorRoute = List.copyOf(actorRoute);
        }
    }
}
