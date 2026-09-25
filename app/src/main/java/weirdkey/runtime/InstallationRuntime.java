package weirdkey.runtime;

import java.util.Optional;
import java.util.function.Consumer;

public final class InstallationRuntime implements AutoCloseable {
    private final InstallationState installationState;
    private final RuntimeEngine<InstallationContext> engine;

    public InstallationRuntime(
        KeyboardDevice keyboard,
        Optional<DisplaySurface> displaySurface,
        InstallationState installationState
    ) {
        this(keyboard, displaySurface, installationState, () -> {
        });
    }

    public InstallationRuntime(
        KeyboardDevice keyboard,
        Optional<DisplaySurface> displaySurface,
        InstallationState installationState,
        Runnable afterTopLevelCycle
    ) {
        this.installationState = installationState;
        this.engine = new RuntimeEngine<>(
            keyboard,
            displaySurface,
            game -> new InstallationContext(game, installationState),
            () -> {
            },
            afterTopLevelCycle
        );
    }

    public void start() {
        engine.start(installationState::install);
    }

    public void run(Consumer<InstallationContext> action) {
        engine.run(action);
    }

    @Override
    public void close() {
        engine.close();
    }
}
