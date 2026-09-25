package weirdkey.world;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import weirdkey.runtime.CartridgeRuntime;
import weirdkey.runtime.DisplaySurface;
import weirdkey.runtime.InstallationRuntime;
import weirdkey.runtime.InstallationState;
import weirdkey.runtime.KeyDefinition;
import weirdkey.runtime.KeyboardDevice;

public final class WeirdkeyWorld implements AutoCloseable {
    private final KeyboardDevice keyboard;
    private final Optional<DisplaySurface> displaySurface;
    private final CartridgeVault cartridgeVault;
    private final List<InstallationState> installationStates;
    private InstallationRuntime installationRuntime;
    private NoCartridgeState noCartridgeState;
    private CartridgeRuntime activeRuntime;
    private CartridgeManifest pendingLaunch;
    private boolean started;
    private boolean closed;

    public WeirdkeyWorld(KeyboardDevice keyboard, Optional<DisplaySurface> displaySurface, Path cartridgeVaultPath) {
        this(keyboard, displaySurface, new CartridgeVault(cartridgeVaultPath), List.of());
    }

    WeirdkeyWorld(
        KeyboardDevice keyboard,
        Optional<DisplaySurface> displaySurface,
        CartridgeVault cartridgeVault,
        List<InstallationState> installationStates
    ) {
        this.keyboard = keyboard;
        this.displaySurface = displaySurface;
        this.cartridgeVault = cartridgeVault;
        this.installationStates = List.copyOf(installationStates);
    }

    public void start() {
        if (closed) {
            throw new IllegalStateException("Weirdkey world is closed");
        }
        if (started) {
            throw new IllegalStateException("Weirdkey world already started");
        }

        started = true;
        noCartridgeState = new NoCartridgeState(cartridgeVault.discover(), manifest -> pendingLaunch = manifest);
        InstallationState installationState = context -> {
            noCartridgeState.install(context);
            installationStates.forEach(state -> state.install(context));
        };
        installationRuntime = new InstallationRuntime(
            keyboard,
            displaySurface,
            installationState,
            this::launchPendingCartridge
        );
        installationRuntime.start();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        if (activeRuntime != null) {
            CartridgeRuntime runtime = activeRuntime;
            activeRuntime = null;
            runtime.close();
        }
        if (installationRuntime != null) {
            installationRuntime.close();
            installationRuntime = null;
        }
    }

    private void launchPendingCartridge() {
        if (closed) {
            return;
        }
        if (activeRuntime != null || pendingLaunch == null) {
            return;
        }

        CartridgeManifest manifest = pendingLaunch;
        pendingLaunch = null;
        installationRuntime.run(noCartridgeState::deactivate);
        launch(manifest);
    }

    private void launch(CartridgeManifest manifest) {
        activeRuntime = new CartridgeRuntime(
            keyboard,
            displaySurface,
            cartridgeVault.instantiate(manifest),
            this::onCartridgeStopped
        );
        activeRuntime.start();
    }

    private void onCartridgeStopped() {
        if (closed) {
            return;
        }

        activeRuntime = null;
        prepareDeviceForNextState();
        installationRuntime.run(noCartridgeState::activate);
    }

    private void prepareDeviceForNextState() {
        keyboard.captureInputKeys(java.util.Set.of());
        for (KeyDefinition key : keyboard.topology().orderedKeys()) {
            keyboard.clearColor(key.id());
        }
    }
}
