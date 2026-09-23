package weirdkey.cartridges;

import java.util.List;
import weirdkey.runtime.Cartridge;
import weirdkey.runtime.GameContext;
import weirdkey.runtime.InputType;
import weirdkey.runtime.KeyColor;
import weirdkey.runtime.KeyDefinition;
import weirdkey.runtime.KeyInputEvent;

public final class FirstExperimentCartridge implements Cartridge {
    private List<String> keyCycle = List.of();
    private int activeIndex = -1;

    @Override
    public void start(GameContext context) {
        this.keyCycle = context.topology().orderedKeys().stream().map(KeyDefinition::id).toList();
        if (keyCycle.isEmpty()) {
            throw new IllegalStateException("Topology must contain at least one key");
        }

        activeIndex = 0;
        lightActiveKey(context);
    }

    @Override
    public void onInput(GameContext context, KeyInputEvent event) {
        if (event.type() != InputType.PRESS || !activeKeyId().equals(event.keyId())) {
            return;
        }

        context.clearKey(activeKeyId());
        activeIndex = (activeIndex + 1) % keyCycle.size();
        lightActiveKey(context);
    }

    private String activeKeyId() {
        return keyCycle.get(activeIndex);
    }

    private void lightActiveKey(GameContext context) {
        context.lightKey(activeKeyId(), KeyColor.GREEN);
        context.showStatus("Lit key " + activeKeyId());
    }
}
