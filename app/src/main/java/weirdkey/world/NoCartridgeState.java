package weirdkey.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import weirdkey.runtime.Cartridge;
import weirdkey.runtime.CartridgeContext;
import weirdkey.runtime.InputType;
import weirdkey.runtime.KeyColor;
import weirdkey.runtime.KeyDefinition;
import weirdkey.runtime.KeyInputEvent;

final class NoCartridgeState implements Cartridge {
    private static final KeyColor UNAVAILABLE_COLOR = new KeyColor(96, 96, 0);

    private final List<CartridgeManifest> cartridges;
    private final Consumer<CartridgeManifest> launchAction;
    private List<Selection> selections = List.of();
    private String pauseKeyId;

    NoCartridgeState(List<CartridgeManifest> cartridges, Consumer<CartridgeManifest> launchAction) {
        this.cartridges = List.copyOf(cartridges);
        this.launchAction = launchAction;
    }

    @Override
    public void install(CartridgeContext context) {
        List<String> assignableKeys = context.topology().orderedKeys().stream()
            .map(KeyDefinition::id)
            .filter(keyId -> !"ESC".equals(keyId))
            .toList();
        if (assignableKeys.size() < cartridges.size()) {
            throw new IllegalStateException("Not enough keys to expose discovered cartridges");
        }

        List<Selection> discoveredSelections = new ArrayList<>();
        for (int index = 0; index < cartridges.size(); index++) {
            CartridgeManifest manifest = cartridges.get(index);
            String keyId = assignableKeys.get(index);
            discoveredSelections.add(new Selection(keyId, manifest));
        }
        selections = List.copyOf(discoveredSelections);
        pauseKeyId = assignableKeys.contains("PAUSE") ? "PAUSE" : null;

        List<String> capturedKeys = new ArrayList<>(selections.stream().map(Selection::keyId).toList());
        if (pauseKeyId != null && !capturedKeys.contains(pauseKeyId)) {
            capturedKeys.add(pauseKeyId);
        }
        context.captureInputKeys(capturedKeys);
        selections.forEach(selection -> context.lightKey(
            selection.keyId(),
            selection.manifest().availability() == CartridgeAvailability.AVAILABLE ? KeyColor.GREEN : UNAVAILABLE_COLOR
        ));
        context.showStatus(renderRoster());
        context.on(KeyInputEvent.class, event -> onInput(context, event));
    }

    private void onInput(CartridgeContext context, KeyInputEvent event) {
        if (event.type() != InputType.PRESS) {
            return;
        }
        if (pauseKeyId != null && pauseKeyId.equals(event.keyId())) {
            context.showStatus(renderRoster());
            return;
        }

        Selection selection = selections.stream()
            .filter(candidate -> candidate.keyId().equals(event.keyId()))
            .findFirst()
            .orElse(null);
        if (selection == null) {
            return;
        }

        CartridgeManifest manifest = selection.manifest();
        if (manifest.availability() == CartridgeAvailability.AVAILABLE) {
            launchAction.accept(manifest);
            context.exit();
            return;
        }

        context.showStatus(
            "> " + manifest.name().toUpperCase() + System.lineSeparator() + System.lineSeparator() + manifest.description()
        );
    }

    private String renderRoster() {
        return selections.stream()
            .map(selection -> selection.keyId() + " " + selection.manifest().name() + " [" + selection.manifest().availability() + "]")
            .reduce((left, right) -> left + System.lineSeparator() + right)
            .orElse("No cartridges discovered.");
    }

    private record Selection(String keyId, CartridgeManifest manifest) {
    }
}
