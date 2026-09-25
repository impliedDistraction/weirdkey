package weirdkey.world;

import java.nio.file.Path;
import java.util.Optional;

import weirdkey.runtime.CartridgeRuntime;
import weirdkey.runtime.DisplaySurface;
import weirdkey.runtime.KeyDefinition;
import weirdkey.runtime.KeyboardDevice;

public final class WeirdkeyWorld implements AutoCloseable {
    private final KeyboardDevice keyboard;
    private final Optional<DisplaySurface> displaySurface;
    private final CartridgeVault cartridgeVault;
    private CartridgeRuntime activeRuntime;
    private CartridgeManifest pendingLaunch;
    private boolean started;
    private boolean closed;

    public WeirdkeyWorld(KeyboardDevice keyboard, Optional<DisplaySurface> displaySurface, Path cartridgeVaultPath) {
        this(keyboard, displaySurface, new CartridgeVault(cartridgeVaultPath));
    }

    WeirdkeyWorld(KeyboardDevice keyboard, Optional<DisplaySurface> displaySurface, CartridgeVault cartridgeVault) {
        this.keyboard = keyboard;
        this.displaySurface = displaySurface;
        this.cartridgeVault = cartridgeVault;
    }

    public void start() {
        if (closed) {
            throw new IllegalStateException("Weirdkey world is closed");
        }
        if (started) {
            throw new IllegalStateException("Weirdkey world already started");
        }

        started = true;
        enterNoCartridgeState();
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
    }

    private void enterNoCartridgeState() {
        prepareDeviceForNextState();
        activeRuntime = new CartridgeRuntime(
            keyboard,
            displaySurface,
            new NoCartridgeState(cartridgeVault.discover(), manifest -> pendingLaunch = manifest),
            this::onRuntimeStopped
        );
        activeRuntime.start();
    }

    private void onRuntimeStopped() {
        if (closed) {
            return;
        }

        activeRuntime = null;
        if (pendingLaunch != null) {
            CartridgeManifest manifest = pendingLaunch;
            pendingLaunch = null;
            launch(manifest);
            return;
        }

        enterNoCartridgeState();
    }

    private void launch(CartridgeManifest manifest) {
        prepareDeviceForNextState();
        activeRuntime = new CartridgeRuntime(
            keyboard,
            displaySurface,
            cartridgeVault.instantiate(manifest),
            this::onRuntimeStopped
        );
        activeRuntime.start();
    }

    private void prepareDeviceForNextState() {
        keyboard.captureInputKeys(java.util.Set.of());
        for (KeyDefinition key : keyboard.topology().orderedKeys()) {
            keyboard.clearColor(key.id());
        }
    }
}
